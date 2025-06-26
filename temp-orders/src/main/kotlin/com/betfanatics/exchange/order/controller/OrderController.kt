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
import org.apache.pekko.actor.typed.ActorSystem
import com.betfanatics.exchange.order.actor.common.OrderRequestDTO
import com.betfanatics.exchange.order.actor.common.OrderSide as ActorOrderSide
import com.betfanatics.exchange.order.actor.common.OrderType as ActorOrderType
import com.betfanatics.exchange.order.actor.common.TimeInForce as ActorTimeInForce

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
    val orderId: String, // GUID for idempotency
    val symbol: String,
    val side: OrderSide,
    val quantity: BigDecimal,
    val price: BigDecimal?, // Optional for MARKET orders
    val orderType: OrderType,
    val timeInForce: TimeInForce? = null
)

data class OrderStatusResponse(val orderId: String, val amount: BigDecimal, val status: OrderProcessManager.Step)

@RestController
class OrderController @Autowired constructor(
    private val fixGatewayActor: ActorRef<FixGatewayActor.Command>,
    private val clusterSharding: ClusterSharding,
    private val actorSystem: ActorSystem<Void>,
    private val orderProcessManagerTypeKey: EntityTypeKey<OrderProcessManager.Command>
) {
 
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
        val resolvedTIF = orderRequest.timeInForce ?: when (orderRequest.orderType) {
            OrderType.MARKET -> TimeInForce.IOC
            OrderType.LIMIT -> TimeInForce.GTC
        }
        
        // TODO this is horrible
        // Map controller enums to actor system enums
        val orderRequestDTO = OrderRequestDTO(
            orderId = orderRequest.orderId,
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

        // get the process manager ref
        val processManagerRef: EntityRef<OrderProcessManager.Command> =
            clusterSharding.entityRefFor(orderProcessManagerTypeKey, orderRequest.orderId)
        val timeout = Duration.ofSeconds(10) // TODO move to config

        return AskPattern.ask(
            processManagerRef,
            { replyTo: ActorRef<OrderProcessManager.Confirmation> ->
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
}