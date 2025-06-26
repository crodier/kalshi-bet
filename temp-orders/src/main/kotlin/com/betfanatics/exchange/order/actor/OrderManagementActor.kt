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
import com.betfanatics.exchange.order.config.RiskLimitsConfig
import com.betfanatics.exchange.order.config.KafkaTopicsConfig
import java.time.Instant
import java.math.BigDecimal
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import javax.sql.DataSource

class OrderManagementActor(
    persistenceId: PersistenceId,
    private val context: ActorContext<OrderManagementActor.Command>,
    private val kafkaSender: KafkaSender,
    private val riskLimitsConfig: RiskLimitsConfig,
    private val kafkaTopicsConfig: KafkaTopicsConfig,
    private val dataSource: DataSource
) : EventSourcedBehaviorWithEnforcedReplies<OrderManagementActor.Command, OrderManagementActor.Event, OrderManagementActor.State>(persistenceId) {

    companion object {
        fun create(
            entityId: String,
            kafkaSender: KafkaSender,
            riskLimitsConfig: RiskLimitsConfig,
            kafkaTopicsConfig: KafkaTopicsConfig,
            dataSource: DataSource
        ): Behavior<Command> =
            Behaviors.setup { ctx ->
                OrderManagementActor(
                    PersistenceId.ofUniqueId("OrderManagementActor|$entityId"),
                    ctx,
                    kafkaSender,
                    riskLimitsConfig,
                    kafkaTopicsConfig,
                    dataSource
                )
            }
    }

    // Commands
    sealed interface Command : SerializationMarker
    data class ProcessOrder(
        val orderRequest: OrderRequestDTO,
        val replyTo: ActorRef<Response>
    ) : Command
    
    data class ConfirmKafkaPublished(
        val orderId: String,
        val success: Boolean
    ) : Command

    // Events
    sealed interface Event : SerializationMarker
    data class OrderReceivedEvt(
        val orderId: String,
        val userId: String,
        val orderRequest: OrderRequestDTO,
        val riskAmount: BigDecimal,
        val timestamp: Instant
    ) : Event
    
    data class OrderValidatedEvt(
        val orderId: String,
        val timestamp: Instant
    ) : Event
    
    data class OrderRiskRejectedEvt(
        val orderId: String,
        val reason: String,
        val timestamp: Instant
    ) : Event
    
    data class OrderPersistedEvt(
        val orderId: String,
        val timestamp: Instant
    ) : Event
    
    data class OrderPublishedToKafkaEvt(
        val orderId: String,
        val success: Boolean,
        val timestamp: Instant
    ) : Event

    // Responses
    sealed interface Response : SerializationMarker
    data class OrderAccepted(val orderId: String, val timestamp: Instant) : Response
    data class OrderRejected(val orderId: String, val reason: String) : Response

    // State
    data class State(
        val orderId: String = "",
        val userId: String = "",
        val orderRequest: OrderRequestDTO? = null,
        val riskAmount: BigDecimal = BigDecimal.ZERO,
        val status: OrderStatus = OrderStatus.PENDING,
        val lastUpdated: Instant = Instant.EPOCH
    ) : SerializationMarker

    enum class OrderStatus {
        PENDING, VALIDATED, RISK_REJECTED, PERSISTED, KAFKA_PUBLISHED, FAILED
    }

    override fun emptyState(): State = State()

    // Command handlers
    override fun commandHandler(): CommandHandlerWithReply<Command, Event, State> =
        newCommandHandlerWithReplyBuilder()
            .forAnyState()
            .onCommand(ProcessOrder::class.java) { state, cmd ->
                context.log.info("ACTOR_MESSAGE_RECEIVED: actor=OrderManagementActor command=ProcessOrder orderId={} userId={} symbol={} side={} quantity={}",
                    cmd.orderRequest.orderId, cmd.orderRequest.userId, cmd.orderRequest.symbol, 
                    cmd.orderRequest.side, cmd.orderRequest.quantity)
                
                if (state.status != OrderStatus.PENDING && state.orderId.isNotEmpty()) {
                    context.log.warn("[OrderManagementActor] Order already processed: orderId={}, status={}", 
                        state.orderId, state.status)
                    Effect().reply(cmd.replyTo, OrderRejected(cmd.orderRequest.orderId, "Order already processed"))
                } else {
                    // Calculate risk amount based on order type and side
                    val riskAmount = calculateRiskAmount(cmd.orderRequest)
                    
                    Effect().persist(OrderReceivedEvt(
                        cmd.orderRequest.orderId,
                        cmd.orderRequest.userId,
                        cmd.orderRequest,
                        riskAmount,
                        Instant.now()
                    )).thenRun { newState: State ->
                        // Validate risk limits
                        validateRiskLimits(newState, cmd.replyTo)
                    }.thenNoReply()
                }
            }
            .onCommand(ConfirmKafkaPublished::class.java) { state, cmd ->
                context.log.info("[OrderManagementActor] Kafka publish confirmation: orderId={}, success={}", 
                    cmd.orderId, cmd.success)
                
                Effect().persist(OrderPublishedToKafkaEvt(cmd.orderId, cmd.success, Instant.now()))
                    .thenNoReply()
            }
            .build()

    // Event handlers
    override fun eventHandler(): EventHandler<State, Event> =
        newEventHandlerBuilder()
            .forAnyState()
            .onEvent(OrderReceivedEvt::class.java) { state, evt ->
                context.log.info("[OrderManagementActor] Order received: orderId={}, userId={}, riskAmount={}", 
                    evt.orderId, evt.userId, evt.riskAmount)
                state.copy(
                    orderId = evt.orderId,
                    userId = evt.userId,
                    orderRequest = evt.orderRequest,
                    riskAmount = evt.riskAmount,
                    status = OrderStatus.PENDING,
                    lastUpdated = evt.timestamp
                )
            }
            .onEvent(OrderValidatedEvt::class.java) { state, evt ->
                context.log.info("[OrderManagementActor] Order validated: orderId={}", evt.orderId)
                state.copy(
                    status = OrderStatus.VALIDATED,
                    lastUpdated = evt.timestamp
                )
            }
            .onEvent(OrderRiskRejectedEvt::class.java) { state, evt ->
                context.log.info("[OrderManagementActor] Order risk rejected: orderId={}, reason={}", 
                    evt.orderId, evt.reason)
                state.copy(
                    status = OrderStatus.RISK_REJECTED,
                    lastUpdated = evt.timestamp
                )
            }
            .onEvent(OrderPersistedEvt::class.java) { state, evt ->
                context.log.info("[OrderManagementActor] Order persisted: orderId={}", evt.orderId)
                state.copy(
                    status = OrderStatus.PERSISTED,
                    lastUpdated = evt.timestamp
                )
            }
            .onEvent(OrderPublishedToKafkaEvt::class.java) { state, evt ->
                context.log.info("[OrderManagementActor] Order published to Kafka: orderId={}, success={}", 
                    evt.orderId, evt.success)
                state.copy(
                    status = if (evt.success) OrderStatus.KAFKA_PUBLISHED else OrderStatus.FAILED,
                    lastUpdated = evt.timestamp
                )
            }
            .build()

    private fun calculateRiskAmount(orderRequest: OrderRequestDTO): BigDecimal {
        // For binary options, risk is the amount you can lose
        // For BUY orders: risk = quantity * price (what you pay)
        // For SELL orders: risk = quantity * (100 - price) (what you could pay if wrong)
        return when (orderRequest.side) {
            com.betfanatics.exchange.order.actor.common.OrderSide.BUY -> {
                val price = orderRequest.price ?: BigDecimal.ZERO
                orderRequest.quantity.multiply(price)
            }
            com.betfanatics.exchange.order.actor.common.OrderSide.SELL -> {
                val price = orderRequest.price ?: BigDecimal.ZERO
                val maxPayout = BigDecimal(100) // Binary options max payout
                orderRequest.quantity.multiply(maxPayout.subtract(price))
            }
        }
    }

    private fun validateRiskLimits(state: State, replyTo: ActorRef<Response>) {
        try {
            // Check per-bet limit
            if (state.riskAmount > riskLimitsConfig.maxBetAmount) {
                context.log.warn("[OrderManagementActor] Order exceeds per-bet limit: orderId={}, riskAmount={}, limit={}", 
                    state.orderId, state.riskAmount, riskLimitsConfig.maxBetAmount)
                replyTo.tell(OrderRejected(state.orderId, "Order exceeds maximum bet amount limit"))
                return
            }

            // Check user total risk limit
            val currentUserRisk = getUserTotalRisk(state.userId)
            val newTotalRisk = currentUserRisk.add(state.riskAmount)
            
            if (newTotalRisk > riskLimitsConfig.maxUserTotalRisk) {
                context.log.warn("[OrderManagementActor] Order exceeds user total risk limit: orderId={}, userId={}, newTotalRisk={}, limit={}", 
                    state.orderId, state.userId, newTotalRisk, riskLimitsConfig.maxUserTotalRisk)
                replyTo.tell(OrderRejected(state.orderId, "Order exceeds user total risk limit"))
                return
            }

            // Risk validation passed
            context.log.info("[OrderManagementActor] Risk validation passed: orderId={}, riskAmount={}, userTotalRisk={}", 
                state.orderId, state.riskAmount, newTotalRisk)
            
            // Persist to database
            persistOrderToDatabase(state, replyTo)
            
        } catch (e: Exception) {
            context.log.error("[OrderManagementActor] Error during risk validation: orderId={}, error={}", 
                state.orderId, e.message, e)
            replyTo.tell(OrderRejected(state.orderId, "Risk validation failed: ${e.message}"))
        }
    }

    private fun getUserTotalRisk(userId: String): BigDecimal {
        return try {
            dataSource.connection.use { conn ->
                val sql = "SELECT total_risk_amount FROM user_risk_tracking WHERE user_id = ?"
                conn.prepareStatement(sql).use { stmt ->
                    stmt.setString(1, userId)
                    val rs = stmt.executeQuery()
                    if (rs.next()) {
                        rs.getBigDecimal("total_risk_amount") ?: BigDecimal.ZERO
                    } else {
                        BigDecimal.ZERO
                    }
                }
            }
        } catch (e: Exception) {
            context.log.error("[OrderManagementActor] Error retrieving user total risk: userId={}, error={}", 
                userId, e.message, e)
            BigDecimal.ZERO
        }
    }

    private fun persistOrderToDatabase(state: State, replyTo: ActorRef<Response>) {
        try {
            dataSource.connection.use { conn ->
                conn.autoCommit = false
                
                try {
                    // Insert order
                    val orderSql = """
                        INSERT INTO order_management 
                        (order_id, user_id, symbol, side, quantity, price, order_type, time_in_force, 
                         status, risk_amount, created_at, kafka_published, fix_sent)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """.trimIndent()
                    
                    conn.prepareStatement(orderSql).use { stmt ->
                        stmt.setString(1, state.orderId)
                        stmt.setString(2, state.userId)
                        stmt.setString(3, state.orderRequest!!.symbol)
                        stmt.setString(4, state.orderRequest.side.name)
                        stmt.setBigDecimal(5, state.orderRequest.quantity)
                        stmt.setBigDecimal(6, state.orderRequest.price)
                        stmt.setString(7, state.orderRequest.orderType.name)
                        stmt.setString(8, state.orderRequest.timeInForce.name)
                        stmt.setString(9, "VALIDATED")
                        stmt.setBigDecimal(10, state.riskAmount)
                        stmt.setTimestamp(11, java.sql.Timestamp.from(Instant.now()))
                        stmt.setBoolean(12, false)
                        stmt.setBoolean(13, false)
                        stmt.executeUpdate()
                    }
                    
                    // Update user risk tracking
                    val upsertRiskSql = """
                        INSERT INTO user_risk_tracking (user_id, total_risk_amount, last_updated)
                        VALUES (?, ?, ?)
                        ON CONFLICT (user_id) 
                        DO UPDATE SET 
                            total_risk_amount = user_risk_tracking.total_risk_amount + EXCLUDED.total_risk_amount,
                            last_updated = EXCLUDED.last_updated
                    """.trimIndent()
                    
                    conn.prepareStatement(upsertRiskSql).use { stmt ->
                        stmt.setString(1, state.userId)
                        stmt.setBigDecimal(2, state.riskAmount)
                        stmt.setTimestamp(3, java.sql.Timestamp.from(Instant.now()))
                        stmt.executeUpdate()
                    }
                    
                    conn.commit()
                    context.log.info("[OrderManagementActor] Order persisted to database: orderId={}", state.orderId)
                    
                    // Publish to Kafka
                    publishToKafka(state, replyTo)
                    
                } catch (e: Exception) {
                    conn.rollback()
                    throw e
                }
            }
        } catch (e: Exception) {
            context.log.error("[OrderManagementActor] Error persisting order to database: orderId={}, error={}", 
                state.orderId, e.message, e)
            replyTo.tell(OrderRejected(state.orderId, "Database persistence failed: ${e.message}"))
        }
    }

    private fun publishToKafka(state: State, replyTo: ActorRef<Response>) {
        val orderMessage = mapOf(
            "orderId" to state.orderId,
            "userId" to state.userId,
            "symbol" to state.orderRequest!!.symbol,
            "side" to state.orderRequest.side.name,
            "quantity" to state.orderRequest.quantity,
            "price" to state.orderRequest.price,
            "orderType" to state.orderRequest.orderType.name,
            "timeInForce" to state.orderRequest.timeInForce.name,
            "riskAmount" to state.riskAmount,
            "timestamp" to Instant.now().toString()
        )
        
        context.log.info("HANDOFF_TO_KAFKA: from=OrderManagementActor to=Kafka topic={} orderId={} messageSize={}",
            kafkaTopicsConfig.fixOrderTopic.name, state.orderId, orderMessage.size)
        
        kafkaSender.send(kafkaTopicsConfig.fixOrderTopic.name, state.orderId, orderMessage)
            .thenAccept { result ->
                if (result.isSuccess) {
                    context.log.info("KAFKA_PUBLISH_SUCCESS: actor=OrderManagementActor topic={} orderId={}", 
                        kafkaTopicsConfig.fixOrderTopic.name, state.orderId)
                    context.self.tell(ConfirmKafkaPublished(state.orderId, true))
                    replyTo.tell(OrderAccepted(state.orderId, Instant.now()))
                } else {
                    context.log.error("KAFKA_PUBLISH_FAILED: actor=OrderManagementActor topic={} orderId={} error={}", 
                        kafkaTopicsConfig.fixOrderTopic.name, state.orderId, result.errorMessage)
                    context.self.tell(ConfirmKafkaPublished(state.orderId, false))
                    replyTo.tell(OrderRejected(state.orderId, "Kafka publication failed"))
                }
            }
            .exceptionally { throwable ->
                context.log.error("[OrderManagementActor] Exception publishing to Kafka: orderId={}, error={}", 
                    state.orderId, throwable.message, throwable)
                context.self.tell(ConfirmKafkaPublished(state.orderId, false))
                replyTo.tell(OrderRejected(state.orderId, "Kafka publication exception: ${throwable.message}"))
                null
            }
    }

}