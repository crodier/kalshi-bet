package com.betfanatics.exchange.controller

import org.springframework.web.bind.annotation.*
import org.springframework.http.ResponseEntity
import org.apache.pekko.actor.typed.ActorRef
import com.betfanatics.exchange.order.actor.FixGatewayActor
import org.springframework.beans.factory.annotation.Autowired
import org.apache.pekko.cluster.sharding.typed.javadsl.ClusterSharding
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityTypeKey
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityRef
import com.betfanatics.exchange.order.actor.OrderProcessManager
import java.math.BigDecimal
import java.time.Duration
import org.apache.pekko.actor.typed.javadsl.AskPattern
import java.util.concurrent.CompletionStage
import java.util.concurrent.CompletableFuture
import org.apache.pekko.actor.typed.ActorSystem
import com.betfanatics.exchange.order.actor.common.OrderRequestDTO
import com.betfanatics.exchange.order.actor.common.OrderSide as ActorOrderSide
import com.betfanatics.exchange.order.actor.common.OrderType as ActorOrderType
import com.betfanatics.exchange.order.actor.common.TimeInForce as ActorTimeInForce
import com.betfanatics.exchange.order.service.ClOrdIdMappingService
import org.slf4j.LoggerFactory

// --- V1 Order Request Model ---
enum class OrderSide {
    BUY, SELL
}

enum class OrderType {
    MARKET, LIMIT
}

enum class TimeInForce {
    GTC, // Good Till Cancelled
    IOC, // Immediate or Cancel
    FOK  // Fill or Kill
}

data class OrderRequestV1(
    val betOrderId: String, // GUID for idempotency
    val symbol: String,
    val side: OrderSide,
    val quantity: BigDecimal,
    val price: BigDecimal?, // Optional for MARKET orders
    val orderType: OrderType,
    val timeInForce: TimeInForce? = null
)

data class CancelOrderRequest(
    val betOrderId: String // The order ID to cancel
)

data class OrderStatusResponse(val betOrderId: String, val amount: BigDecimal, val status: OrderProcessManager.Step)

@RestController
class OrderController @Autowired constructor(
    private val fixGatewayActor: ActorRef<FixGatewayActor.Command>,
    private val clusterSharding: ClusterSharding,
    private val actorSystem: ActorSystem<Void>,
    private val orderProcessManagerTypeKey: EntityTypeKey<OrderProcessManager.Command>,
    private val clOrdIdMappingService: ClOrdIdMappingService
) {
    
    private val log = LoggerFactory.getLogger(OrderController::class.java)
 
    @GetMapping("/order/{orderId}")
    fun getOrder(@PathVariable orderId: String): CompletionStage<ResponseEntity<Any>> {
        val processManagerTypeKey: EntityTypeKey<OrderProcessManager.Command> =
            EntityTypeKey.create(OrderProcessManager.Command::class.java, "OpenPositionProcessManager")
        val processManagerRef: EntityRef<OrderProcessManager.Command> =
            clusterSharding.entityRefFor(processManagerTypeKey, orderId)
        val timeout = Duration.ofSeconds(15)

        return AskPattern.ask(
            processManagerRef,
            { replyTo: ActorRef<OrderProcessManager.Confirmation> ->
                OrderProcessManager.GetStatus(replyTo)
            },
            timeout,
            actorSystem.scheduler()
        ).thenApply { response ->
            when (response) {
                is OrderProcessManager.OrderResult ->
                    ResponseEntity.ok(OrderStatusResponse(response.orderId, response.filledQty, OrderProcessManager.Step.COMPLETED))
                is OrderProcessManager.WorkflowFailed ->
                    ResponseEntity.status(500).body("Workflow failed: ${response.reason}")
                else -> ResponseEntity.status(500).body("Unknown response")
            }
        }
    }

    @PostMapping("/v1/order")
    fun placeOrderV1(
        @RequestBody orderRequest: OrderRequestV1,
        @RequestHeader("X-Dev-User", required = false) devUser: String?
    ): CompletionStage<ResponseEntity<String>> {
        val userId = devUser ?: "default-user"
        
        // Log detailed order request arrival
        log.info("ORDER_REQUEST_RECEIVED: betOrderId={} userId={} symbol={} side={} quantity={} price={} orderType={} timeInForce={}",
            orderRequest.betOrderId,
            userId,
            orderRequest.symbol,
            orderRequest.side,
            orderRequest.quantity,
            orderRequest.price,
            orderRequest.orderType,
            orderRequest.timeInForce)
            
        // Check for idempotency - has this betOrderId been processed before?
        if (clOrdIdMappingService.hasClOrdId(orderRequest.betOrderId)) {
            val existingClOrdId = clOrdIdMappingService.getClOrdIdByOrderId(orderRequest.betOrderId)
            log.warn("DUPLICATE_ORDER_REQUEST: betOrderId={} already has ClOrdID={}, returning success for idempotency",
                orderRequest.betOrderId, existingClOrdId)
            return CompletableFuture.completedFuture(
                ResponseEntity.ok("Order already submitted with betOrderId: ${orderRequest.betOrderId}")
            )
        }
        
        val resolvedTIF = orderRequest.timeInForce ?: when (orderRequest.orderType) {
            OrderType.MARKET -> TimeInForce.IOC
            OrderType.LIMIT -> TimeInForce.GTC
        }
        
        // TODO this is horrible
        // Map controller enums to actor system enums
        val orderRequestDTO = OrderRequestDTO(
            orderId = orderRequest.betOrderId,  // This is the betOrderId
            symbol = orderRequest.symbol,
            side = when (orderRequest.side) {
                OrderSide.BUY -> ActorOrderSide.BUY
                OrderSide.SELL -> ActorOrderSide.SELL
            },
            quantity = orderRequest.quantity,
            price = orderRequest.price,
            orderType = when (orderRequest.orderType) {
                OrderType.MARKET -> ActorOrderType.MARKET
                OrderType.LIMIT -> ActorOrderType.LIMIT
            },
            timeInForce = when (resolvedTIF) {
                TimeInForce.GTC -> ActorTimeInForce.GTC
                TimeInForce.IOC -> ActorTimeInForce.IOC
                TimeInForce.FOK -> ActorTimeInForce.FOK
            },
            userId = userId
        )
        
        // Generate and store ClOrdID mapping with complete order data
        val clOrdId = clOrdIdMappingService.generateAndStoreClOrdId(orderRequest.betOrderId, orderRequestDTO)
        log.info("CLORDID_GENERATED: betOrderId={} clOrdId={}", orderRequest.betOrderId, clOrdId)

        // get the process manager ref
        val processManagerRef: EntityRef<OrderProcessManager.Command> =
            clusterSharding.entityRefFor(orderProcessManagerTypeKey, orderRequest.betOrderId)
        val timeout = Duration.ofSeconds(10) // TODO move to config

        // Log handoff to ProcessManager
        log.info("HANDOFF_TO_ACTOR: from=OrderController to=OrderProcessManager betOrderId={} userId={} entityId={}",
            orderRequest.betOrderId, userId, orderRequest.betOrderId)

        return AskPattern.ask(
            processManagerRef,
            { replyTo: ActorRef<OrderProcessManager.Confirmation> ->
                log.debug("ACTOR_MESSAGE_SEND: to=OrderProcessManager command=StartWorkflow betOrderId={}", orderRequest.betOrderId)
                OrderProcessManager.StartWorkflow(orderRequestDTO, replyTo)
            },
            timeout,
            actorSystem.scheduler()
        ).thenApply { response: OrderProcessManager.Confirmation ->
            when (response) {
                is OrderProcessManager.WorkflowStarted ->
                    ResponseEntity.ok("Workflow started for orderId: ${response.orderId}")
                is OrderProcessManager.WorkflowFailed ->
                    ResponseEntity.status(500).body("Workflow failed: ${response.reason}")
                is OrderProcessManager.OrderResult ->
                    ResponseEntity.ok("Order result: ${response.orderId}, ${response.outcome}, filled: ${response.filledQty}")
                else -> ResponseEntity.status(500).body("Unknown response")
            }
        }
    }
    
    @PostMapping("/v1/order/cancel")
    fun cancelOrderV1(
        @RequestBody cancelRequest: CancelOrderRequest,
        @RequestHeader("X-Dev-User", required = false) devUser: String?
    ): CompletionStage<ResponseEntity<String>> {
        val userId = devUser ?: "default-user"
        
        // Log cancel request arrival
        log.info("CANCEL_REQUEST_RECEIVED: betOrderId={} userId={}",
            cancelRequest.betOrderId,
            userId)
            
        // Check if order exists
        if (!clOrdIdMappingService.hasClOrdId(cancelRequest.betOrderId)) {
            log.warn("CANCEL_REQUEST_REJECTED: betOrderId={} not found", cancelRequest.betOrderId)
            return CompletableFuture.completedFuture(
                ResponseEntity.status(404).body("Order not found: ${cancelRequest.betOrderId}")
            )
        }
        
        try {
            // Generate cancel ClOrdID and get the OrigClOrdID for tag 41
            val (cancelClOrdId, origClOrdId) = clOrdIdMappingService.generateAndStoreCancelClOrdId(cancelRequest.betOrderId)
            log.info("CANCEL_CLORDID_GENERATED: betOrderId={} cancelClOrdId={} origClOrdId={}", 
                cancelRequest.betOrderId, cancelClOrdId, origClOrdId)
            
            // Send cancel request to FIX gateway
            val timeout = Duration.ofSeconds(10)
            
            log.info("HANDOFF_TO_ACTOR: from=OrderController to=FixGatewayActor action=CancelOrder betOrderId={} userId={}",
                cancelRequest.betOrderId, userId)
            
            return AskPattern.ask(
                fixGatewayActor,
                { replyTo: ActorRef<FixGatewayActor.Response> ->
                    FixGatewayActor.CancelOrder(cancelRequest.betOrderId, cancelClOrdId, origClOrdId, userId, replyTo)
                },
                timeout,
                actorSystem.scheduler()
            ).thenApply { response ->
                when (response) {
                    is FixGatewayActor.OrderCancelled ->
                        ResponseEntity.ok("Cancel request sent for betOrderId: ${response.orderId}")
                    is FixGatewayActor.OrderRejected ->
                        ResponseEntity.status(400).body("Cancel rejected: ${response.reason}")
                    else -> ResponseEntity.status(500).body("Unknown response")
                }
            }
        } catch (e: Exception) {
            log.error("Failed to process cancel request for betOrderId={}: {}", cancelRequest.betOrderId, e.message)
            return CompletableFuture.completedFuture(
                ResponseEntity.status(500).body("Failed to process cancel: ${e.message}")
            )
        }
    }
}