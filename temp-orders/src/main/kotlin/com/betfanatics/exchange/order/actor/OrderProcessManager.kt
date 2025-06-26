package com.betfanatics.exchange.order.actor

import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.javadsl.Behaviors
import org.apache.pekko.persistence.typed.PersistenceId
import org.apache.pekko.persistence.typed.javadsl.CommandHandlerWithReply
import org.apache.pekko.persistence.typed.javadsl.EventHandler
import org.apache.pekko.persistence.typed.javadsl.EventSourcedBehaviorWithEnforcedReplies
import org.apache.pekko.persistence.typed.javadsl.ReplyEffect
import org.apache.pekko.actor.typed.javadsl.ActorContext
import java.math.BigDecimal
import java.time.Instant
import java.time.Duration
import com.betfanatics.exchange.order.actor.common.OrderRequestDTO
import org.apache.pekko.actor.typed.ActorRef
import com.betfanatics.exchange.order.actor.common.OrderActorResolver
import com.betfanatics.exchange.order.actor.common.PositionActorResolver

class OrderProcessManager(
    persistenceId: PersistenceId,
    private val context: ActorContext<OrderProcessManager.Command>,
    private val fbgWalletActor: org.apache.pekko.actor.typed.ActorRef<FBGWalletActor.Command>,
    private val kalshiWalletActor: org.apache.pekko.actor.typed.ActorRef<KalshiWalletActor.Command>,
    private val fbgWalletResponseAdapter: org.apache.pekko.actor.typed.ActorRef<FBGWalletActor.Response>,
    private val kalshiWalletResponseAdapter: org.apache.pekko.actor.typed.ActorRef<KalshiWalletActor.Response>,
    private val orderResponseAdapter: org.apache.pekko.actor.typed.ActorRef<OrderActor.Response>,
    private val orderActorResolver: OrderActorResolver,
    private val positionActorResolver: PositionActorResolver,
    private val positionResponseAdapter: org.apache.pekko.actor.typed.ActorRef<PositionActor.Confirmation>
) : EventSourcedBehaviorWithEnforcedReplies<OrderProcessManager.Command, OrderProcessManager.Event, OrderProcessManager.State>(persistenceId) {
    init {
        context.log.info("OrderProcessManager: constructor called, actor path: {}", context.self.path())
    }

    companion object {
        fun create(
            orderId: String,
            fbgWalletActor: org.apache.pekko.actor.typed.ActorRef<FBGWalletActor.Command>,
            kalshiWalletActor: org.apache.pekko.actor.typed.ActorRef<KalshiWalletActor.Command>,
            orderActorResolver: OrderActorResolver,
            positionActorResolver: PositionActorResolver
        ): Behavior<Command> =
            Behaviors.setup { ctx ->
                ctx.log.info("[create] OrderProcessManager for entityId(orderId)={}, actorPath={}", orderId, ctx.self.path())
                // Message adapters for wallet responses - convert wallet responses to ProcessManager commands
                val fbgWalletResponseAdapter = ctx.messageAdapter(FBGWalletActor.Response::class.java) { response ->
                    when (response) {
                        is FBGWalletActor.DebitCompleted -> FBGDebitCompleted(response.timestamp)
                        is FBGWalletActor.DebitFailed -> FBGDebitFailed(response.reason)
                        is FBGWalletActor.CreditCompleted -> FBGCreditCompleted(response.timestamp)
                        is FBGWalletActor.CreditFailed -> FBGCreditFailed(response.reason)
                        else -> OrderProcessManager.Fail("Unexpected response from FBGWalletActor: $response")
                    }
                }
                // Message adapter for KalshiWalletActor responses - convert KalshiWalletActor responses to ProcessManager commands
                val kalshiWalletResponseAdapter = ctx.messageAdapter(KalshiWalletActor.Response::class.java) { response ->
                    when (response) {
                        is KalshiWalletActor.CreditCompleted -> KalshiCreditCompleted(response.timestamp)
                        is KalshiWalletActor.CreditFailed -> KalshiCreditFailed(response.reason)
                        is KalshiWalletActor.DebitCompleted -> OmnibusDebitCompleted(response.timestamp)
                        is KalshiWalletActor.DebitFailed -> OmnibusDebitFailed(response.reason)
                        else -> OrderProcessManager.Fail("Unexpected response from KalshiWalletActor: $response")
                    }
                }
                // Message adapter for OrderActor responses - convert OrderActor responses to ProcessManager commands
                val orderResponseAdapter = ctx.messageAdapter(OrderActor.Response::class.java) { response ->
                    ctx.log.info("ProcessManager: Received response from OrderActor: {}", response)
                    when (response) {
                        is OrderActor.OrderPlaced -> OrderPlacedCmd(response.timestamp)
                        is OrderActor.OrderRejected -> OrderPlacementFailed("Order rejected by exchange: ${response.reason}")
                        is OrderActor.OrderFillStatus -> OrderFillStatusReceived(response)
                        else -> Fail("Unexpected response from OrderActor: $response")
                    }
                }
                // Message adapter for PositionActor responses
                val positionResponseAdapter = ctx.messageAdapter(PositionActor.Confirmation::class.java) { response ->
                    when (response) {
                        is PositionActor.PositionResult -> PositionQueried(response.symbol, response.netPosition)
                        else -> PositionQueryFailed("Unexpected reply from PositionActor")
                    }
                }
                OrderProcessManager(
                    PersistenceId.ofUniqueId("OrderProcessManager|$orderId"),
                    ctx,
                    fbgWalletActor,
                    kalshiWalletActor,
                    fbgWalletResponseAdapter,
                    kalshiWalletResponseAdapter,
                    orderResponseAdapter,
                    orderActorResolver,
                    positionActorResolver,
                    positionResponseAdapter
                )
            }
    }

    // Commands
    sealed interface Command : SerializationMarker
    data class StartWorkflow(
        val orderRequest: OrderRequestDTO,
        val replyTo: ActorRef<Confirmation>
    ) : Command
    data class FBGDebitCompleted(val timestamp: Instant) : Command
    data class OmnibusDebitCompleted(val timestamp: Instant) : Command
    data class KalshiCreditCompleted(val timestamp: Instant) : Command
    data class OrderPlacedCmd(val timestamp: Instant) : Command
    data class Fail(val reason: String) : Command
    data class OrderFillStatusReceived(val status: OrderActor.OrderFillStatus) : Command
    data class GetStatus(val replyTo: org.apache.pekko.actor.typed.ActorRef<Confirmation>) : Command
    data class KalshiCreditFailed(val reason: String) : Command
    data class FBGCreditCompleted(val timestamp: Instant) : Command
    data class FBGCreditFailed(val reason: String) : Command
    data class OmnibusDebitFailed(val reason: String) : Command
    data class FBGDebitFailed(val reason: String) : Command
    data class OrderPlacementFailed(val reason: String) : Command
    data class PositionQueried(val symbol: String, val netPosition: java.math.BigDecimal) : Command
    data class PositionQueryFailed(val reason: String) : Command

    // Events
    sealed interface Event : SerializationMarker
    data class WorkflowStartedEvt(
        val orderRequest: OrderRequestDTO,
        val timestamp: Instant
    ) : Event
    data class FBGDebitCompletedEvt(val timestamp: Instant) : Event
    data class OmnibusDebitCompletedEvt(val timestamp: Instant) : Event
    data class KalshiCreditCompletedEvt(val timestamp: Instant) : Event
    data class OrderPlacedEvt(val timestamp: Instant) : Event
    data class FailedEvt(val reason: String, val timestamp: Instant) : Event
    data class CompensatingFBGStartedEvt(val timestamp: Instant) : Event
    data class CompensatingOmnibusStartedEvt(val timestamp: Instant) : Event
    data class OrderFillStatusUpdatedEvt(val fillStatus: String, val filledQty: BigDecimal, val timestamp: Instant) : Event
    data class PositionQueryCompletedEvt(val timestamp: Instant) : Event

    // Replies
    sealed interface Confirmation : SerializationMarker
    data class WorkflowStarted(val orderId: String) : Confirmation
    data class WorkflowFailed(val reason: String) : Confirmation // Only for catastrophic failures
    data class OrderResult(
        val orderId: String, 
        val outcome: String, // "FILLED", "PARTIALLY_FILLED", "NOT_FILLED", "REJECTED"
        val filledQty: BigDecimal,
        val reason: String? = null, // For rejections
        val isTimeout: Boolean = false
    ) : Confirmation
    object Ack : Confirmation

    // State
    data class State(
        val step: Step = Step.INIT,
        val orderRequest: OrderRequestDTO? = null,
        val startTime: Instant = Instant.EPOCH,
        val lastActivity: Instant = Instant.EPOCH,
        val fillStatus: String? = null,
        val filledQty: BigDecimal = BigDecimal.ZERO
    ) : SerializationMarker {
        val orderId: String get() = orderRequest?.orderId ?: ""
        val userId: String get() = orderRequest?.userId ?: ""
        val amount: BigDecimal get() = orderRequest?.quantity ?: BigDecimal.ZERO
    }

    enum class Step { INIT, QUERYING_POSITION, DEBITING_FBG, DEBITING_OMNIBUS, CREDITING_KALSHI, PLACING_ORDER, COMPLETED, FAILED, COMPENSATING_FBG, COMPENSATING_OMNIBUS }

    // Persist a reference to the caller, so we can reply - this can't be in state
    // since it can't be serialized
    // if the actor is restarted or passivated, we won't be able to reply
    // and the caller will need to use GetStatus
    @Transient
    private var transientReplyTo: ActorRef<Confirmation>? = null

    override fun emptyState(): State = State()

    override fun commandHandler(): CommandHandlerWithReply<Command, Event, State> {
        return newCommandHandlerWithReplyBuilder()
            .forAnyState()
            .onCommand(StartWorkflow::class.java) { state, cmd ->
                context.log.info("[commandHandler] StartWorkflow: entityId={}, actorPath={}, cmd={}, stateBefore={}", 
                    context.self.path().name(), context.self.path(), cmd, state)
                if (state.step != Step.INIT) return@onCommand Effect().reply(cmd.replyTo, WorkflowFailed("Already started"))
                val event = WorkflowStartedEvt(cmd.orderRequest, Instant.now())
                transientReplyTo = cmd.replyTo
                Effect().persist(event).thenRun { newState: State ->
                    // Now the state is updated with orderRequest, etc.
                    val positionRef = positionActorResolver(cmd.orderRequest.userId)
                    // Use tell with message adapter instead of ask
                    positionRef.tell(PositionActor.GetPosition(cmd.orderRequest.symbol, positionResponseAdapter))
                }.thenNoReply()
            }
            .onCommand(FBGDebitCompleted::class.java) { state, cmd ->
                context.log.info("[commandHandler] FBGDebitCompleted: entityId={}, actorPath={}, cmd={}, state={}", 
                    context.self.path().name(), context.self.path(), cmd, state)
                handleFBGDebitCompleted(state, cmd)
            }
            .onCommand(OmnibusDebitCompleted::class.java) { state, cmd ->
                context.log.info("[commandHandler] OmnibusDebitCompleted: entityId={}, actorPath={}, cmd={}, state={}", 
                    context.self.path().name(), context.self.path(), cmd, state)
                if (state.step == Step.COMPENSATING_OMNIBUS) {
                    // Omnibus compensation completed - now compensate FBG
                    Effect().persist(CompensatingFBGStartedEvt(cmd.timestamp)).thenRun { newState: State ->
                        executeNextAction(newState)
                    }.thenNoReply()
                } else {
                    handleOmnibusDebitCompleted(state, cmd)
                }
            }
            .onCommand(KalshiCreditCompleted::class.java) { state, cmd ->
                context.log.info("[commandHandler] KalshiCreditCompleted: entityId={}, actorPath={}, cmd={}, state={}", 
                    context.self.path().name(), context.self.path(), cmd, state)
                handleKalshiCreditCompleted(state, cmd)
            }
            .onCommand(OrderPlacedCmd::class.java) { state, cmd ->
                context.log.info("[commandHandler] OrderPlacedCmd: entityId={}, actorPath={}, cmd={}, state={}", 
                    context.self.path().name(), context.self.path(), cmd, state)
                handleOrderPlaced(state, cmd)
            }
            .onCommand(Fail::class.java) { state, cmd ->
                context.log.info("[commandHandler] Fail: entityId={}, actorPath={}, cmd={}, state={}", 
                    context.self.path().name(), context.self.path(), cmd, state)
                handleFail(state, cmd)
            }
            .onCommand(OrderFillStatusReceived::class.java) { state, cmd ->
                context.log.info("[commandHandler] OrderFillStatusReceived: entityId={}, actorPath={}, cmd={}, state={}", 
                    context.self.path().name(), context.self.path(), cmd, state)
                handleOrderFillStatus(state, cmd)
            }
            .onCommand(KalshiCreditFailed::class.java) { state, cmd ->
                context.log.info("[commandHandler] KalshiCreditFailed: entityId={}, actorPath={}, state={}, reason={}", context.self.path().name(), context.self.path(), state, cmd.reason)
                if (state.step != Step.CREDITING_KALSHI) return@onCommand Effect().noReply()

                Effect().persist(CompensatingFBGStartedEvt(Instant.now())).thenRun { newState: State ->
                    executeNextAction(newState)
                }.thenNoReply()
            }
            .onCommand(FBGCreditCompleted::class.java) { state, cmd ->
                context.log.info("[commandHandler] FBGCreditCompleted: entityId={}, actorPath={}, state={}", context.self.path().name(), context.self.path(), state)
                if (state.step != Step.COMPENSATING_FBG) return@onCommand Effect().noReply()

                Effect().persist(FailedEvt("Workflow failed, FBG compensated", cmd.timestamp))
                    .thenRun { _: State ->
                        context.log.info("[commandHandler] Compensation complete, replying WorkflowFailed")
                        transientReplyTo?.tell(WorkflowFailed("Workflow failed"))
                    }
                    .thenNoReply()
            }
            .onCommand(FBGCreditFailed::class.java) { state, cmd ->
                context.log.error("[commandHandler] FBGCreditFailed during compensation: entityId={}, actorPath={}, state={}, reason={}", 
                    context.self.path().name(), context.self.path(), state, cmd.reason)
                if (state.step != Step.COMPENSATING_FBG) return@onCommand Effect().noReply()

                // Catastrophic failure - compensation itself failed
                Effect().persist(FailedEvt("Compensation failed: ${cmd.reason}", Instant.now()))
                    .thenRun { _: State ->
                        context.log.error("[commandHandler] FBG compensation failed, replying WorkflowFailed")
                        transientReplyTo?.tell(WorkflowFailed("Compensation failed: ${cmd.reason}"))
                    }
                    .thenNoReply()
            }
            .onCommand(OmnibusDebitFailed::class.java) { state, cmd ->
                context.log.info("[commandHandler] OmnibusDebitFailed: entityId={}, actorPath={}, state={}, reason={}", 
                    context.self.path().name(), context.self.path(), state, cmd.reason)
                if (state.step != Step.DEBITING_OMNIBUS) return@onCommand Effect().noReply()

                Effect().persist(CompensatingFBGStartedEvt(Instant.now())).thenRun { newState: State ->
                    executeNextAction(newState)
                }.thenNoReply()
            }
            .onCommand(FBGDebitFailed::class.java) { state, cmd ->
                context.log.info("[commandHandler] FBGDebitFailed: entityId={}, actorPath={}, state={}, reason={}", 
                    context.self.path().name(), context.self.path(), state, cmd.reason)
                if (state.step != Step.DEBITING_FBG) return@onCommand Effect().noReply()

                // No compensation needed - just fail
                Effect().persist(FailedEvt("FBG debit failed: ${cmd.reason}", Instant.now()))
                    .thenRun { _: State ->
                        transientReplyTo?.tell(WorkflowFailed("FBG debit failed"))
                    }
                    .thenNoReply()
            }
            .onCommand(OrderPlacementFailed::class.java) { state, cmd ->
                context.log.info("[commandHandler] OrderPlacementFailed: entityId={}, actorPath={}, state={}, reason={}", 
                    context.self.path().name(), context.self.path(), state, cmd.reason)
                if (state.step != Step.PLACING_ORDER) return@onCommand Effect().noReply()

                // If this is an exchange rejection, reply with OrderResult and compensate
                if (cmd.reason.contains("rejected by exchange")) {
                    context.log.info("[commandHandler] Order rejected by exchange, replying with OrderResult and compensating")
                    transientReplyTo?.tell(OrderResult(
                        orderId = state.orderId,
                        outcome = "REJECTED",
                        filledQty = BigDecimal.ZERO,
                        reason = cmd.reason
                    ))
                }
                
                // Always compensate when order placement fails
                Effect().persist(CompensatingOmnibusStartedEvt(Instant.now())).thenRun { newState: State ->
                    executeNextAction(newState)
                }.thenNoReply()
            }
            .onCommand(PositionQueried::class.java) { state, cmd ->
                context.log.info("[commandHandler] PositionQueried: symbol={}, netPosition={}", cmd.symbol, cmd.netPosition)
                if (state.step != Step.QUERYING_POSITION) {
                    context.log.warn("Received PositionQueried in step {}, ignoring", state.step)
                    return@onCommand Effect().noReply()
                }

                // TODO!

                Effect().persist(PositionQueryCompletedEvt(Instant.now()))
                    .thenRun { newState: State ->
                        executeNextAction(newState)
                    }
                    .thenNoReply()
            }
            .onCommand(PositionQueryFailed::class.java) { state, cmd ->
                context.log.error("[commandHandler] PositionQueryFailed: reason={}", cmd.reason)
                if (state.step != Step.QUERYING_POSITION) {
                    context.log.warn("Received PositionQueryFailed in step {}, ignoring", state.step)
                    return@onCommand Effect().noReply()
                }
                Effect().persist(FailedEvt("Position query failed: ${cmd.reason}", java.time.Instant.now()))
                    .thenRun { _: State ->
                        transientReplyTo?.tell(WorkflowFailed("Position query failed: ${cmd.reason}"))
                    }
                    .thenNoReply()
            }
            .onCommand(GetStatus::class.java) { state, cmd ->
                context.log.info("[commandHandler] GetStatus: entityId={}, actorPath={}, state={}", context.self.path().name(), context.self.path(), state)
                // GetStatus returns the current status, NOT workflow step
                val outcome = state.fillStatus ?: when (state.step) {
                    Step.INIT, Step.QUERYING_POSITION, Step.DEBITING_FBG, Step.DEBITING_OMNIBUS, Step.CREDITING_KALSHI -> "PENDING"
                    Step.PLACING_ORDER, Step.COMPLETED -> "PLACED"
                    Step.FAILED -> "FAILED"
                    Step.COMPENSATING_FBG, Step.COMPENSATING_OMNIBUS -> "COMPENSATING"
                }
                Effect().reply(cmd.replyTo, OrderResult(state.orderId, outcome, state.filledQty))
            }
            .build()
    }

    // State Transition Methods
    private fun transitionTo(state: State, newStep: Step, timestamp: Instant): State {
        context.log.info("[transitionTo] entityId={}, from={} to={}", context.self.path().name(), state.step, newStep)
        return state.copy(step = newStep, lastActivity = timestamp)
    }

    private fun executeNextAction(state: State) {
        when (state.step) {
            Step.QUERYING_POSITION -> {
                context.log.debug("No action needed for step QUERYING_POSITION")
            }
            Step.DEBITING_FBG -> {
                context.log.info("Next action: Debit FBG for orderId={}", state.orderId)
                fbgWalletActor.tell(FBGWalletActor.DebitFunds(state.amount, state.userId, state.orderId, fbgWalletResponseAdapter))
            }
            Step.DEBITING_OMNIBUS -> {
                context.log.info("Next action: Debit Omnibus for orderId={}", state.orderId)
                kalshiWalletActor.tell(KalshiWalletActor.DebitFunds(state.amount, state.userId, state.orderId, kalshiWalletResponseAdapter))
            }
            Step.CREDITING_KALSHI -> {
                context.log.info("Next action: Credit Kalshi for orderId={}", state.orderId)
                kalshiWalletActor.tell(KalshiWalletActor.CreditFunds(state.amount, state.userId, state.orderId, kalshiWalletResponseAdapter))
            }
            Step.PLACING_ORDER -> {
                context.log.info("Next action: Place Order for orderId={}", state.orderId)
                val orderRef = orderActorResolver(state.orderId)
                orderRef.tell(OrderActor.PlaceOrder(state.orderRequest!!, state.userId, state.amount, orderResponseAdapter))
            }
            Step.COMPENSATING_FBG -> {
                context.log.info("Next action: Compensate FBG for orderId={}", state.orderId)
                fbgWalletActor.tell(FBGWalletActor.CreditFunds(state.amount, state.userId, state.orderId, fbgWalletResponseAdapter))
            }
            Step.COMPENSATING_OMNIBUS -> {
                context.log.info("Next action: Compensate Omnibus for orderId={}", state.orderId)
                kalshiWalletActor.tell(KalshiWalletActor.DebitFunds(state.amount, state.userId, state.orderId, kalshiWalletResponseAdapter))
            }
            Step.COMPLETED, Step.FAILED, Step.INIT -> {
                context.log.debug("No action needed for step {}", state.step)
            }
        }
    }

    private fun isValidTransition(from: Step, to: Step): Boolean {
        return when (Pair(from, to)) {
            Pair(Step.INIT, Step.QUERYING_POSITION) -> true
            Pair(Step.QUERYING_POSITION, Step.DEBITING_FBG) -> true
            Pair(Step.DEBITING_FBG, Step.DEBITING_OMNIBUS) -> true
            Pair(Step.DEBITING_FBG, Step.FAILED) -> true
            Pair(Step.DEBITING_OMNIBUS, Step.CREDITING_KALSHI) -> true
            Pair(Step.DEBITING_OMNIBUS, Step.COMPENSATING_FBG) -> true
            Pair(Step.CREDITING_KALSHI, Step.PLACING_ORDER) -> true
            Pair(Step.CREDITING_KALSHI, Step.COMPENSATING_FBG) -> true
            Pair(Step.PLACING_ORDER, Step.COMPLETED) -> true
            Pair(Step.PLACING_ORDER, Step.COMPENSATING_OMNIBUS) -> true
            Pair(Step.COMPENSATING_FBG, Step.FAILED) -> true
            Pair(Step.COMPENSATING_OMNIBUS, Step.COMPENSATING_FBG) -> true
            else -> false
        }
    }

    // Event handlers
    override fun eventHandler(): EventHandler<State, Event> {
        return newEventHandlerBuilder()
            .forAnyState()
            .onEvent(WorkflowStartedEvt::class.java) { state, event ->
                context.log.info("[eventHandler] WorkflowStartedEvt: entityId={}, actorPath={}, event={}, stateBefore={}", 
                    context.self.path().name(), context.self.path(), event, state)
                val newState = state.copy(
                    step = Step.QUERYING_POSITION, // first step
                    orderRequest = event.orderRequest,
                    startTime = event.timestamp,
                    lastActivity = event.timestamp
                )
                context.log.info("[eventHandler] After WorkflowStartedEvt: entityId={}, actorPath={}, newState={}", 
                    context.self.path().name(), context.self.path(), newState)
                newState
            }
            .onEvent(FBGDebitCompletedEvt::class.java) { state, event ->
                context.log.info("[eventHandler] FBGDebitCompletedEvt: entityId={}, actorPath={}, event={}, stateBefore={}", 
                    context.self.path().name(), context.self.path(), event, state)
                transitionTo(state, Step.DEBITING_OMNIBUS, event.timestamp)
            }
            .onEvent(OmnibusDebitCompletedEvt::class.java) { state, event ->
                context.log.info("[eventHandler] OmnibusDebitCompletedEvt: entityId={}, actorPath={}, event={}, stateBefore={}", 
                    context.self.path().name(), context.self.path(), event, state)
                transitionTo(state, Step.CREDITING_KALSHI, event.timestamp)
            }
            .onEvent(KalshiCreditCompletedEvt::class.java) { state, event ->
                context.log.info("[eventHandler] KalshiCreditCompletedEvt: entityId={}, actorPath={}, event={}, stateBefore={}", 
                    context.self.path().name(), context.self.path(), event, state)
                transitionTo(state, Step.PLACING_ORDER, event.timestamp)
            }
            .onEvent(OrderPlacedEvt::class.java) { state, event ->
                context.log.info("[eventHandler] OrderPlacedEvt: entityId={}, actorPath={}, event={}, stateBefore={}", 
                    context.self.path().name(), context.self.path(), event, state)
                transitionTo(state, Step.COMPLETED, event.timestamp)
            }
            .onEvent(FailedEvt::class.java) { state, event ->
                context.log.info("[eventHandler] FailedEvt: entityId={}, actorPath={}, event={}, stateBefore={}", 
                    context.self.path().name(), context.self.path(), event, state)
                transitionTo(state, Step.FAILED, event.timestamp)
            }
            .onEvent(CompensatingFBGStartedEvt::class.java) { state, event ->
                context.log.info("[eventHandler] CompensatingFBGStartedEvt: entityId={}, actorPath={}, event={}, stateBefore={}", context.self.path().name(), context.self.path(), event, state)
                transitionTo(state, Step.COMPENSATING_FBG, event.timestamp)
            }
            .onEvent(CompensatingOmnibusStartedEvt::class.java) { state, event ->
                context.log.info("[eventHandler] CompensatingOmnibusStartedEvt: entityId={}, actorPath={}, event={}, stateBefore={}", context.self.path().name(), context.self.path(), event, state)
                transitionTo(state, Step.COMPENSATING_OMNIBUS, event.timestamp)
            }
            .onEvent(OrderFillStatusUpdatedEvt::class.java) { state, event ->
                context.log.info("[eventHandler] OrderFillStatusUpdatedEvt: entityId={}, actorPath={}, fillStatus={}, filledQty={}", 
                    context.self.path().name(), context.self.path(), event.fillStatus, event.filledQty)
                state.copy(fillStatus = event.fillStatus, filledQty = event.filledQty, lastActivity = event.timestamp)
            }
            .onEvent(PositionQueryCompletedEvt::class.java) { state, event ->
                transitionTo(state, Step.DEBITING_FBG, event.timestamp)
            }
            .build()
    }

    // Command handlers
    private fun handleStartWorkflow(state: State, cmd: StartWorkflow): ReplyEffect<Event, State> {
        context.log.info("[handleStartWorkflow] entityId={}, actorPath={}, stateBefore={}, cmd={}", 
            context.self.path().name(), context.self.path(), state, cmd)
        val event = WorkflowStartedEvt(cmd.orderRequest, Instant.now())
        transientReplyTo = cmd.replyTo
        context.log.info("[handleStartWorkflow] transientReplyTo is now {}", transientReplyTo)
        context.log.info("OrderProcessManager: handleStartWorkflow for orderId={}", cmd.orderRequest.orderId)
        if (state.step != Step.INIT) return Effect().reply(cmd.replyTo, WorkflowFailed("Already started"))
        return Effect().persist(event).thenRun { newState: State ->
            context.log.info("OrderProcessManager: Persisted WorkflowStartedEvt, executing next action")
            executeNextAction(newState)
        }.thenNoReply()
    }

    private fun handleFBGDebitCompleted(state: State, cmd: FBGDebitCompleted): ReplyEffect<Event, State> {
        context.log.info("OrderProcessManager: handleFBGDebitCompleted for orderId={}", state.orderId)
        if (state.step != Step.DEBITING_FBG) return Effect().noReply()
        val event = FBGDebitCompletedEvt(cmd.timestamp)
        return Effect().persist(event).thenRun { newState: State ->
            executeNextAction(newState)
        }.thenNoReply()
    }

    private fun handleOmnibusDebitCompleted(state: State, cmd: OmnibusDebitCompleted): ReplyEffect<Event, State> {
        context.log.info("OrderProcessManager: handleOmnibusDebitCompleted for orderId={}", state.orderId)
        if (state.step != Step.DEBITING_OMNIBUS) return Effect().noReply()
        val event = OmnibusDebitCompletedEvt(cmd.timestamp)
        return Effect().persist(event).thenRun { newState: State ->
            executeNextAction(newState)
        }.thenNoReply()
    }

    private fun handleKalshiCreditCompleted(state: State, cmd: KalshiCreditCompleted): ReplyEffect<Event, State> {
        context.log.info("OrderProcessManager: handleKalshiCreditCompleted for orderId={}", state.orderId)
        if (state.step != Step.CREDITING_KALSHI) return Effect().noReply()
        val event = KalshiCreditCompletedEvt(cmd.timestamp)
        return Effect().persist(event).thenRun { newState: State ->
            executeNextAction(newState)
        }.thenNoReply()
    }

    private fun handleOrderPlaced(state: State, cmd: OrderPlacedCmd): ReplyEffect<Event, State> {
        context.log.info("OrderProcessManager: handleOrderPlaced for orderId={}", state.orderId)
        if (state.step != Step.PLACING_ORDER) return Effect().noReply()
        val event = OrderPlacedEvt(cmd.timestamp)
        return Effect().persist(event).thenNoReply()
    }

    private fun handleFail(state: State, cmd: Fail): ReplyEffect<Event, State> {
        context.log.info("handleFail: transientReplyTo = {}", transientReplyTo)
        val event = FailedEvt(cmd.reason, Instant.now())
        return Effect().persist(event).thenRun { newState: State ->
            context.log.info("handleFail: replying to controller with WorkflowFailed, transientReplyTo = {}", transientReplyTo)
            transientReplyTo?.tell(WorkflowFailed(cmd.reason))
        }.thenNoReply()
    }

    private fun handleOrderFillStatus(state: State, cmd: OrderFillStatusReceived): ReplyEffect<Event, State> {
        context.log.info("handleOrderFillStatus: using transientReplyTo = {}, status={}, filledQty={}, isTimeout={}", 
            transientReplyTo, cmd.status.status, cmd.status.filledQty, cmd.status.isTimeout)
        val fillEvent = OrderFillStatusUpdatedEvt(cmd.status.status, cmd.status.filledQty, Instant.now())
        
        // Only reply to controller if:
        // 1. Order is fully filled, OR  
        // 2. This is a timeout update, OR
        // 3. Order is cancelled by exchange
        val shouldReply = when {
            cmd.status.status == "FILLED" -> true     // Always reply when fully filled
            cmd.status.status == "CANCELLED" -> true  // Always reply when cancelled - this is very unlikely to happen
            cmd.status.isTimeout -> true              // Always reply for timeout updates
            else -> false                             // Don't reply for regular partial fills
        }
        
        return Effect().persist(fillEvent).thenRun { newState: State ->
            if (shouldReply) {
                context.log.info("handleOrderFillStatus: replying to controller with order outcome={}, filledQty={}, isTimeout={}", 
                    cmd.status.status, newState.filledQty, cmd.status.isTimeout)
                transientReplyTo?.tell(OrderResult(
                    orderId = cmd.status.orderId, 
                    outcome = cmd.status.status, 
                    filledQty = newState.filledQty,
                    isTimeout = cmd.status.isTimeout
                ))
                
                // Trigger compensation for orders that are definitively cancelled by exchange
                if (cmd.status.status == "CANCELLED") {
                    val unfilledAmount = state.amount - newState.filledQty
                    if (unfilledAmount > BigDecimal.ZERO) {
                        context.log.info("handleOrderFillStatus: order cancelled with unfilled amount={}, triggering partial compensation for orderId={}", 
                            unfilledAmount, cmd.status.orderId)
                        // TODO: Implement partial compensation for cancelled remainder
                        // This requires tracking the unfilled amount and compensating proportionally
                        // For now, log the need for partial compensation - this is a complex scenario
                        context.log.warn("PARTIAL COMPENSATION NEEDED: orderId={}, filledQty={}, cancelledQty={} - requires manual intervention", 
                            cmd.status.orderId, newState.filledQty, unfilledAmount)
                    } else {
                        context.log.info("handleOrderFillStatus: order cancelled but was fully filled, no compensation needed for orderId={}", cmd.status.orderId)
                    }
                }
                
                // Note: Timeouts are normal - orders can fill much later. We only compensate on
                // explicit exchange cancellations, not timeouts.
            } else {
                context.log.info("handleOrderFillStatus: not replying to controller for status={} isTimeout={} (regular partial fill)", 
                    cmd.status.status, cmd.status.isTimeout)
            }
        }.thenNoReply()
    }
}