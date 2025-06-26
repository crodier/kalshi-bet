package com.betfanatics.exchange.order.actor

import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.javadsl.Behaviors
import java.time.Instant
import java.math.BigDecimal
import org.apache.pekko.persistence.typed.PersistenceId
import org.apache.pekko.persistence.typed.javadsl.EventSourcedBehaviorWithEnforcedReplies
import org.apache.pekko.persistence.typed.javadsl.CommandHandlerWithReply
import org.apache.pekko.persistence.typed.javadsl.EventHandler
import org.apache.pekko.actor.typed.javadsl.ActorContext
import org.apache.pekko.actor.typed.ActorRef
import java.time.Duration
import com.betfanatics.exchange.order.actor.FixGatewayActor.Command.SendOrder
import com.betfanatics.exchange.order.actor.common.OrderRequestDTO
import org.apache.pekko.actor.Cancellable
import com.betfanatics.exchange.order.actor.common.OrderProcessManagerResolver
import com.betfanatics.exchange.order.actor.common.PositionActorResolver

class OrderActor(
    persistenceId: PersistenceId,
    private val orderId: String,
    private val context: ActorContext<OrderActor.Command>,
    private val fixGatewayActor: ActorRef<FixGatewayActor.Command>,
    private val orderProcessManagerResolver: OrderProcessManagerResolver,
    private val positionActorResolver: PositionActorResolver
) : EventSourcedBehaviorWithEnforcedReplies<OrderActor.Command, OrderActor.Event, OrderActor.State>(persistenceId) {

    companion object {
        private val FILL_TIMEOUT: Duration = Duration.ofSeconds(3) // TODO move to config
        fun create(orderId: String, 
        fixGatewayActor: ActorRef<FixGatewayActor.Command>, 
        orderProcessManagerResolver: OrderProcessManagerResolver,
        positionActorResolver: PositionActorResolver
        ): Behavior<Command> =
            Behaviors.setup { ctx ->
                OrderActor(PersistenceId.ofUniqueId("OrderActor|$orderId"), orderId, ctx, fixGatewayActor, orderProcessManagerResolver, positionActorResolver)
            }
    }

    // These cannot be serialized, so they can't go into state or events
    @Transient
    private var fillTimeoutCancellable: Cancellable? = null    
    
    // Store ProcessManager's replyTo transiently - we only need it during order placement
    // and will clear it after replying. Actor restarts will lose this reference, but that's
    // acceptable since placement would need to be retried anyway.
    @Transient
    private var processManagerReplyTo: ActorRef<Response>? = null

    // Commands
    sealed interface Command : SerializationMarker
    data class PlaceOrder(
        val orderRequest: OrderRequestDTO,
        val userId: String,
        val amount: BigDecimal,
        val replyTo: ActorRef<Response>
    ) : Command
    data class FixOrderAccepted(val timestamp: Instant) : Command
    data class FixOrderRejected(val reason: String) : Command
    data class FixOrderCancelled(val reason: String) : Command
    data class OrderFillUpdate(val filledQty: BigDecimal, val timestamp: Instant) : Command
    data class OrderFillTimeout(val orderId: String) : Command

    // Events
    sealed interface Event : SerializationMarker
    data class OrderPlacedEvt(val orderId: String, val userId: String, val symbol: String, val amount: BigDecimal, val side: com.betfanatics.exchange.order.actor.common.OrderSide, val timestamp: Instant) : Event
    data class OrderFilledEvt(val userId: String, val symbol: String, val filledQty: BigDecimal, val timestamp: Instant, val status: String) : Event

    // Replies
    sealed interface Response : SerializationMarker
    data class OrderPlaced(val orderId: String, val timestamp: Instant) : Response
    data class OrderRejected(val orderId: String, val reason: String) : Response
    data class OrderFillStatus(val orderId: String, val filledQty: BigDecimal, val status: String, val isTimeout: Boolean = false) : Response

    // State
    data class State(
        val orderId: String = "",
        val userId: String = "",
        val symbol: String = "",
        val amount: BigDecimal = BigDecimal.ZERO,
        val side: com.betfanatics.exchange.order.actor.common.OrderSide? = null,
        val placed: Boolean = false,
        val filledQty: BigDecimal = BigDecimal.ZERO,
        val timestamp: Instant = Instant.EPOCH
    ) : SerializationMarker

    // Receives Events from FixGatewayActor and converts them to Commands for this actor
    // So, for instance, we get events when orders are accepted, and then issue a command to record that here
    private val fixGatewayResponseAdapter: ActorRef<com.betfanatics.exchange.order.actor.FixGatewayActor.Response> =
        context.messageAdapter(com.betfanatics.exchange.order.actor.FixGatewayActor.Response::class.java) { response: com.betfanatics.exchange.order.actor.FixGatewayActor.Response ->
            when (response) {
                is com.betfanatics.exchange.order.actor.FixGatewayActor.OrderAccepted -> FixOrderAccepted(response.timestamp)
                is com.betfanatics.exchange.order.actor.FixGatewayActor.OrderRejected -> FixOrderRejected(response.reason)
                is com.betfanatics.exchange.order.actor.FixGatewayActor.OrderCancelled -> FixOrderCancelled(response.reason)
                else -> FixOrderRejected("Unexpected response from FixGatewayActor")
            }
        }

    override fun emptyState(): State = State(orderId = orderId)

    // Command handlers
    override fun commandHandler(): CommandHandlerWithReply<Command, Event, State> =
        newCommandHandlerWithReplyBuilder()
            .forAnyState()
            .onCommand(PlaceOrder::class.java) { state, cmd ->
                context.log.info("[OrderActor] Received PlaceOrder: orderId={}, userId={}, amount={}, placed={}", cmd.orderRequest.orderId, cmd.userId, cmd.amount, state.placed)
                if (state.placed) {
                    context.log.info("[OrderActor] Order already placed for orderId={}", cmd.orderRequest.orderId)
                    Effect().reply(cmd.replyTo, OrderRejected(cmd.orderRequest.orderId, "Order already placed"))
                } else {

                    // TODO order business logic

                    context.log.info("[OrderActor] Sending order to FixGatewayActor for orderId={}", cmd.orderRequest.orderId)
                    // Store the replyTo to use later when FixGateway accepts the order
                    processManagerReplyTo = cmd.replyTo
                    fixGatewayActor.tell(SendOrder(cmd.orderRequest.orderId, cmd.orderRequest, fixGatewayResponseAdapter))
                    Effect().persist(OrderPlacedEvt(cmd.orderRequest.orderId, cmd.userId, cmd.orderRequest.symbol, cmd.amount, cmd.orderRequest.side, Instant.now()))
                        .thenRun { _: State ->
                            // set a scheduler, so that we will tell the sender if the order is not filled by our timeout
                            context.log.info("[OrderActor] Scheduled fill timeout for orderId={} after {} seconds", cmd.orderRequest.orderId, FILL_TIMEOUT.seconds)
                            fillTimeoutCancellable = context.scheduleOnce(FILL_TIMEOUT, context.self, OrderFillTimeout(cmd.orderRequest.orderId))
                            // Send UpdatePosition to PositionActor for reserved amount (open order)
                            val positionRef = positionActorResolver(cmd.userId)
                            positionRef.tell(
                                PositionActor.UpdatePosition(
                                    symbol = cmd.orderRequest.symbol,
                                    quantity = cmd.amount,
                                    side = if (cmd.orderRequest.side == com.betfanatics.exchange.order.actor.common.OrderSide.BUY) PositionActor.Side.BUY else PositionActor.Side.SELL,
                                    replyTo = null
                                )
                            )
                        }
                        .thenNoReply()
                }
            }
            .onCommand(FixOrderAccepted::class.java) { state, cmd ->
                context.log.info("[OrderActor] FixGateway accepted order for orderId={}, sending OrderPlaced to ProcessManager", state.orderId)
                // Send OrderPlaced response back to ProcessManager (stored in transient replyTo from PlaceOrder command)
                if (processManagerReplyTo != null) {
                    processManagerReplyTo!!.tell(OrderPlaced(state.orderId, cmd.timestamp))
                } else {
                    context.log.warn("[OrderActor] No processManagerReplyTo stored for orderId={}, cannot send OrderPlaced", state.orderId)
                }
                Effect().noReply()
            }
            .onCommand(FixOrderRejected::class.java) { state, cmd ->
                context.log.info("[OrderActor] FixGateway rejected order for orderId={}, reason={}", state.orderId, cmd.reason)
                // Send OrderRejected response back to ProcessManager
                if (processManagerReplyTo != null) {
                    processManagerReplyTo!!.tell(OrderRejected(state.orderId, cmd.reason))
                } else {
                    context.log.warn("[OrderActor] No processManagerReplyTo stored for orderId={}, cannot send OrderRejected", state.orderId)
                }
                Effect().noReply()
            }
            .onCommand(FixOrderCancelled::class.java) { state, cmd ->
                context.log.info("[OrderActor] FixGateway cancelled order for orderId={}, reason={}", state.orderId, cmd.reason)
                // Cancel timeout since order is definitively cancelled
                fillTimeoutCancellable?.let {
                    context.log.info("[OrderActor] Cancelling fill timeout for cancelled orderId={}", state.orderId)
                    it.cancel()
                    fillTimeoutCancellable = null
                }
                // Send cancellation status to ProcessManager for compensation
                val processManagerRef = orderProcessManagerResolver(state.orderId)
                context.log.info("[OrderActor] Sending OrderFillStatus (CANCELLED) to ProcessManager for orderId={}", state.orderId)
                processManagerRef.tell(OrderProcessManager.OrderFillStatusReceived(
                    OrderFillStatus(state.orderId, state.filledQty, "CANCELLED", isTimeout = false)
                ))
                // Send UpdatePosition to PositionActor for cancelled (unfilled) amount, if any
                if (state.filledQty < state.amount) {
                    val cancelQty = state.amount - state.filledQty
                    val positionRef = positionActorResolver(state.userId)
                    positionRef.tell(
                        PositionActor.UpdatePosition(
                            symbol = state.symbol,
                            quantity = cancelQty,
                            side = if (state.side == com.betfanatics.exchange.order.actor.common.OrderSide.BUY) PositionActor.Side.BUY else PositionActor.Side.SELL,
                            replyTo = null
                        )
                    )
                }
                Effect().noReply()
            }
            .onCommand(OrderFillUpdate::class.java) { state, cmd ->
                context.log.info("[OrderActor] Received fill update: orderId={}, filledQty={}, currentFilledQty={}, amount={}", state.orderId, cmd.filledQty, state.filledQty, state.amount)
                val newFilledQty = state.filledQty + cmd.filledQty
                val processManagerRef = orderProcessManagerResolver(state.orderId)
                if (newFilledQty >= state.amount) {

                    context.log.info("[OrderActor] Order fully filled for orderId={}, totalFilledQty={}", state.orderId, newFilledQty)
                    fillTimeoutCancellable?.let {
                        context.log.info("[OrderActor] Cancelling fill timeout for orderId={}", state.orderId)
                        it.cancel()
                        fillTimeoutCancellable = null
                    }

                    Effect().persist(OrderFilledEvt(state.userId, state.symbol, newFilledQty, cmd.timestamp, "FILLED" ))
                        .thenRun { _: State ->
                            context.log.info("[OrderActor] Sending OrderFillStatus (FILLED) to ProcessManager for orderId={}", state.orderId)
                            processManagerRef.tell(OrderProcessManager.OrderFillStatusReceived(OrderFillStatus(state.orderId, newFilledQty, "FILLED")))
                        }
                        .thenNoReply()
                } else {
                    context.log.info("[OrderActor] Order partially filled for orderId={}, newFilledQty={}", state.orderId, newFilledQty)
                    Effect().persist(OrderFilledEvt(state.userId, state.symbol, newFilledQty, cmd.timestamp, "PARTIALLY_FILLED"))
                        .thenRun { _: State ->
                            context.log.info("[OrderActor] Sending OrderFillStatus (PARTIALLY_FILLED) to ProcessManager for orderId={}", state.orderId)
                            processManagerRef.tell(OrderProcessManager.OrderFillStatusReceived(OrderFillStatus(state.orderId, newFilledQty, "PARTIALLY_FILLED")))
                        }
                        .thenNoReply()
                }
            }
            .onCommand(OrderFillTimeout::class.java) { state, cmd ->
                context.log.info("[OrderActor] Fill timeout for orderId={}, filledQty={}", state.orderId, state.filledQty)
                val processManagerRef = orderProcessManagerResolver(state.orderId)
                val status = if (state.filledQty == BigDecimal.ZERO) "NOT_FILLED" else "PARTIALLY_FILLED"
                context.log.info("[OrderActor] Sending OrderFillStatus (timeout: {}) to ProcessManager for orderId={}", status, state.orderId)
                processManagerRef.tell(OrderProcessManager.OrderFillStatusReceived(OrderFillStatus(state.orderId, state.filledQty, status, isTimeout = true)))
                Effect().noReply()
            }
            .build()

    // Event handlers
    override fun eventHandler(): EventHandler<State, Event> =
        newEventHandlerBuilder()
            .forAnyState()
            .onEvent(OrderPlacedEvt::class.java) { state, evt ->
                context.log.info("OrderActor: Handling OrderPlacedEvt for orderId={}, userId={}, symbol={}, amount={}, side={}, timestamp={}", evt.orderId, evt.userId, evt.symbol, evt.amount, evt.side, evt.timestamp)
                state.copy(
                    orderId = evt.orderId,
                    userId = evt.userId,
                    symbol = evt.symbol,
                    amount = evt.amount,
                    side = evt.side,
                    placed = true,
                    timestamp = evt.timestamp
                )
            }
            .onEvent(OrderFilledEvt::class.java) { state, evt ->
                context.log.info("OrderActor: Handling OrderFilledEvt for orderId={}, filledQty={}, timestamp={}", state.orderId, evt.filledQty, evt.timestamp)
                state.copy(
                    userId = evt.userId,
                    symbol = evt.symbol,
                    filledQty = evt.filledQty,
                    timestamp = evt.timestamp
                )
            }
            .build()
}