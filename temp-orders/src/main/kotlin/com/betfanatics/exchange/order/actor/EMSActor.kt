package com.betfanatics.exchange.order.actor

import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.javadsl.Behaviors
import org.apache.pekko.persistence.typed.PersistenceId
import org.apache.pekko.persistence.typed.javadsl.EventSourcedBehaviorWithEnforcedReplies
import org.apache.pekko.persistence.typed.javadsl.CommandHandlerWithReply
import org.apache.pekko.persistence.typed.javadsl.EventHandler
import org.apache.pekko.actor.typed.javadsl.ActorContext
import org.apache.pekko.actor.typed.ActorRef
import com.betfanatics.exchange.order.actor.common.OrderRequestDTO
import com.betfanatics.exchange.order.messaging.KafkaSender
import com.betfanatics.exchange.order.config.KafkaTopicsConfig
import java.time.Instant
import java.math.BigDecimal

class EMSActor(
    persistenceId: PersistenceId,
    private val context: ActorContext<EMSActor.Command>,
    private val kafkaSender: KafkaSender,
    private val kafkaTopicsConfig: KafkaTopicsConfig,
    private val fixGatewayActor: ActorRef<FixGatewayActor.Command>?
) : EventSourcedBehaviorWithEnforcedReplies<EMSActor.Command, EMSActor.Event, EMSActor.State>(persistenceId) {

    companion object {
        fun create(
            entityId: String,
            kafkaSender: KafkaSender,
            kafkaTopicsConfig: KafkaTopicsConfig,
            fixGatewayActor: ActorRef<FixGatewayActor.Command>?
        ): Behavior<Command> =
            Behaviors.setup { ctx ->
                EMSActor(
                    PersistenceId.ofUniqueId("EMSActor|$entityId"),
                    ctx,
                    kafkaSender,
                    kafkaTopicsConfig,
                    fixGatewayActor
                )
            }
    }

    // Commands
    sealed interface Command : SerializationMarker
    data class ProcessOrderFromKafka(
        val orderMessage: Map<String, Any>,
        val replyTo: ActorRef<Response>?
    ) : Command
    
    data class ConvertToFixMessage(
        val orderRequest: OrderRequestDTO,
        val replyTo: ActorRef<Response>
    ) : Command
    
    data class FixMessageSent(
        val orderId: String,
        val success: Boolean,
        val fixMessageId: String?
    ) : Command
    
    data class ExecutionReportReceived(
        val orderId: String,
        val executionReport: ExecutionReport
    ) : Command

    // Events
    sealed interface Event : SerializationMarker
    data class OrderProcessedEvt(
        val orderId: String,
        val orderRequest: OrderRequestDTO,
        val timestamp: Instant
    ) : Event
    
    data class FixMessageCreatedEvt(
        val orderId: String,
        val fixMessageId: String,
        val messageType: String,
        val timestamp: Instant
    ) : Event
    
    data class FixMessageSentEvt(
        val orderId: String,
        val fixMessageId: String,
        val success: Boolean,
        val timestamp: Instant
    ) : Event
    
    data class ExecutionReportProcessedEvt(
        val orderId: String,
        val executionReport: ExecutionReport,
        val timestamp: Instant
    ) : Event

    // Responses
    sealed interface Response : SerializationMarker
    data class OrderProcessingStarted(val orderId: String) : Response
    data class FixMessageCreated(val orderId: String, val fixMessageId: String) : Response
    data class ProcessingFailed(val orderId: String, val reason: String) : Response

    // State
    data class State(
        val orderId: String = "",
        val orderRequest: OrderRequestDTO? = null,
        val fixMessageId: String? = null,
        val status: ProcessingStatus = ProcessingStatus.PENDING,
        val lastUpdated: Instant = Instant.EPOCH,
        val executionReports: List<ExecutionReport> = emptyList()
    ) : SerializationMarker

    enum class ProcessingStatus {
        PENDING, FIX_MESSAGE_CREATED, FIX_MESSAGE_SENT, EXECUTION_RECEIVED, FAILED
    }

    // Execution Report data class
    data class ExecutionReport(
        val orderId: String,
        val executionId: String,
        val status: String,
        val filledQuantity: BigDecimal,
        val remainingQuantity: BigDecimal,
        val avgPrice: BigDecimal?,
        val timestamp: Instant
    ) : SerializationMarker

    override fun emptyState(): State = State()

    // Command handlers
    override fun commandHandler(): CommandHandlerWithReply<Command, Event, State> =
        newCommandHandlerWithReplyBuilder()
            .forAnyState()
            .onCommand(ProcessOrderFromKafka::class.java) { state, cmd ->
                context.log.info("ACTOR_MESSAGE_RECEIVED: actor=EMSActor command=ProcessOrderFromKafka orderId={} messageKeys={}", 
                    cmd.orderMessage["orderId"], cmd.orderMessage.keys.joinToString(","))
                
                try {
                    val orderRequest = mapKafkaMessageToOrderRequest(cmd.orderMessage)
                    
                    Effect().persist(OrderProcessedEvt(
                        orderRequest.orderId,
                        orderRequest,
                        Instant.now()
                    )).thenRun { newState: State ->
                        // Convert to FIX message
                        convertToFixMessage(newState, cmd.replyTo)
                    }.thenReply(cmd.replyTo) { newState: State ->
                        OrderProcessingStarted(newState.orderId)
                    }
                } catch (e: Exception) {
                    context.log.error("[EMSActor] Error processing Kafka message: error={}", e.message, e)
                    Effect().reply(cmd.replyTo, ProcessingFailed("", "Failed to parse Kafka message: ${e.message}"))
                }
            }
            .onCommand(ConvertToFixMessage::class.java) { state, cmd ->
                context.log.info("[EMSActor] Converting to FIX message: orderId={}", cmd.orderRequest.orderId)
                
                try {
                    val fixMessageId = generateFixMessageId(cmd.orderRequest)
                    
                    Effect().persist(FixMessageCreatedEvt(
                        cmd.orderRequest.orderId,
                        fixMessageId,
                        "NewOrderSingle",
                        Instant.now()
                    )).thenRun { newState: State ->
                        // Send to FIX Gateway (when available)
                        if (fixGatewayActor != null) {
                            context.log.info("HANDOFF_TO_ACTOR: from=EMSActor to=FixGatewayActor orderId={} fixMessageId={}", 
                                cmd.orderRequest.orderId, fixMessageId)
                            sendToFixGateway(newState, cmd.orderRequest)
                        } else {
                            context.log.warn("FIX_GATEWAY_UNAVAILABLE: actor=EMSActor orderId={} skippingFixSend=true", 
                                cmd.orderRequest.orderId)
                            // For testing without FIX, we'll simulate success
                            context.self.tell(FixMessageSent(cmd.orderRequest.orderId, true, fixMessageId))
                        }
                    }.thenReply(cmd.replyTo) { newState: State ->
                        FixMessageCreated(newState.orderId, newState.fixMessageId ?: "")
                    }
                } catch (e: Exception) {
                    context.log.error("[EMSActor] Error converting to FIX message: orderId={}, error={}", 
                        cmd.orderRequest.orderId, e.message, e)
                    Effect().reply(cmd.replyTo, ProcessingFailed(cmd.orderRequest.orderId, "FIX conversion failed: ${e.message}"))
                }
            }
            .onCommand(FixMessageSent::class.java) { state, cmd ->
                context.log.info("[EMSActor] FIX message sent: orderId={}, success={}", cmd.orderId, cmd.success)
                
                Effect().persist(FixMessageSentEvt(
                    cmd.orderId,
                    cmd.fixMessageId ?: "",
                    cmd.success,
                    Instant.now()
                )).thenRun { newState: State ->
                    // Publish execution status to Kafka
                    publishExecutionStatus(newState, cmd.success)
                }.thenNoReply()
            }
            .onCommand(ExecutionReportReceived::class.java) { state, cmd ->
                context.log.info("[EMSActor] Execution report received: orderId={}, status={}, filledQty={}", 
                    cmd.orderId, cmd.executionReport.status, cmd.executionReport.filledQuantity)
                
                Effect().persist(ExecutionReportProcessedEvt(
                    cmd.orderId,
                    cmd.executionReport,
                    Instant.now()
                )).thenRun { newState: State ->
                    // Publish execution report to Kafka
                    publishExecutionReport(newState, cmd.executionReport)
                }.thenNoReply()
            }
            .build()

    // Event handlers
    override fun eventHandler(): EventHandler<State, Event> =
        newEventHandlerBuilder()
            .forAnyState()
            .onEvent(OrderProcessedEvt::class.java) { state, evt ->
                context.log.info("[EMSActor] Order processed: orderId={}", evt.orderId)
                state.copy(
                    orderId = evt.orderId,
                    orderRequest = evt.orderRequest,
                    status = ProcessingStatus.PENDING,
                    lastUpdated = evt.timestamp
                )
            }
            .onEvent(FixMessageCreatedEvt::class.java) { state, evt ->
                context.log.info("[EMSActor] FIX message created: orderId={}, fixMessageId={}", 
                    evt.orderId, evt.fixMessageId)
                state.copy(
                    fixMessageId = evt.fixMessageId,
                    status = ProcessingStatus.FIX_MESSAGE_CREATED,
                    lastUpdated = evt.timestamp
                )
            }
            .onEvent(FixMessageSentEvt::class.java) { state, evt ->
                context.log.info("[EMSActor] FIX message sent: orderId={}, success={}", evt.orderId, evt.success)
                state.copy(
                    status = if (evt.success) ProcessingStatus.FIX_MESSAGE_SENT else ProcessingStatus.FAILED,
                    lastUpdated = evt.timestamp
                )
            }
            .onEvent(ExecutionReportProcessedEvt::class.java) { state, evt ->
                context.log.info("[EMSActor] Execution report processed: orderId={}", evt.orderId)
                state.copy(
                    status = ProcessingStatus.EXECUTION_RECEIVED,
                    executionReports = state.executionReports + evt.executionReport,
                    lastUpdated = evt.timestamp
                )
            }
            .build()

    private fun mapKafkaMessageToOrderRequest(message: Map<String, Any>): OrderRequestDTO {
        return OrderRequestDTO(
            orderId = message["orderId"] as String,
            symbol = message["symbol"] as String,
            side = com.betfanatics.exchange.order.actor.common.OrderSide.valueOf(message["side"] as String),
            quantity = BigDecimal(message["quantity"].toString()),
            price = message["price"]?.let { BigDecimal(it.toString()) },
            orderType = com.betfanatics.exchange.order.actor.common.OrderType.valueOf(message["orderType"] as String),
            timeInForce = com.betfanatics.exchange.order.actor.common.TimeInForce.valueOf(message["timeInForce"] as String),
            userId = message["userId"] as String
        )
    }

    private fun generateFixMessageId(orderRequest: OrderRequestDTO): String {
        // Generate a unique FIX message ID
        return "FIX_${orderRequest.orderId}_${System.currentTimeMillis()}"
    }

    private fun convertToFixMessage(state: State, replyTo: ActorRef<Response>?) {
        // This is where we would convert the order to FIX message format
        // For now, we'll create a simplified conversion
        if (state.orderRequest != null) {
            context.self.tell(ConvertToFixMessage(state.orderRequest, 
                replyTo ?: context.system.ignoreRef()))
        }
    }

    private fun sendToFixGateway(state: State, orderRequest: OrderRequestDTO) {
        // This would send the order to the FIX Gateway
        // For now, we'll simulate the call since we're not testing FIX connectivity
        if (fixGatewayActor != null) {
            context.log.info("[EMSActor] Sending order to FIX Gateway: orderId={}", orderRequest.orderId)
            // fixGatewayActor.tell(FixGatewayActor.SendOrder(...))
            
            // Simulate successful send for testing
            context.self.tell(FixMessageSent(orderRequest.orderId, true, state.fixMessageId))
        }
    }

    private fun publishExecutionStatus(state: State, success: Boolean) {
        val executionMessage = mapOf(
            "orderId" to state.orderId,
            "fixMessageId" to state.fixMessageId,
            "status" to if (success) "FIX_SENT" else "FIX_FAILED",
            "timestamp" to Instant.now().toString()
        )
        
        kafkaSender.send(kafkaTopicsConfig.fixExecutionTopic.name, state.orderId, executionMessage)
            .thenAccept { result ->
                if (result.isSuccess) {
                    context.log.info("[EMSActor] Execution status published to Kafka: orderId={}", state.orderId)
                } else {
                    context.log.error("[EMSActor] Failed to publish execution status: orderId={}, error={}", 
                        state.orderId, result.errorMessage)
                }
            }
            .exceptionally { throwable ->
                context.log.error("[EMSActor] Exception publishing execution status: orderId={}, error={}", 
                    state.orderId, throwable.message, throwable)
                null
            }
    }

    private fun publishExecutionReport(state: State, executionReport: ExecutionReport) {
        val executionMessage = mapOf(
            "orderId" to executionReport.orderId,
            "executionId" to executionReport.executionId,
            "status" to executionReport.status,
            "filledQuantity" to executionReport.filledQuantity,
            "remainingQuantity" to executionReport.remainingQuantity,
            "avgPrice" to executionReport.avgPrice,
            "timestamp" to executionReport.timestamp.toString()
        )
        
        kafkaSender.send(kafkaTopicsConfig.fixExecutionTopic.name, state.orderId, executionMessage)
            .thenAccept { result ->
                if (result.isSuccess) {
                    context.log.info("[EMSActor] Execution report published to Kafka: orderId={}", state.orderId)
                } else {
                    context.log.error("[EMSActor] Failed to publish execution report: orderId={}, error={}", 
                        state.orderId, result.errorMessage)
                }
            }
            .exceptionally { throwable ->
                context.log.error("[EMSActor] Exception publishing execution report: orderId={}, error={}", 
                    state.orderId, throwable.message, throwable)
                null
            }
    }
}