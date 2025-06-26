package com.betfanatics.exchange.order.actor

import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.javadsl.Behaviors
import org.apache.pekko.persistence.typed.PersistenceId
import org.apache.pekko.persistence.typed.javadsl.EventSourcedBehaviorWithEnforcedReplies
import org.apache.pekko.persistence.typed.javadsl.CommandHandlerWithReply
import org.apache.pekko.persistence.typed.javadsl.EventHandler
import org.apache.pekko.persistence.typed.javadsl.ReplyEffect
import org.apache.pekko.persistence.typed.javadsl.Effect
import org.apache.pekko.actor.typed.javadsl.ActorContext
import org.apache.pekko.actor.typed.ActorRef
import java.math.BigDecimal

// This should perhaps be called "potentialpositionactor"
// It includes unfilled orders
class PositionActor(
    persistenceId: PersistenceId,
    private val context: ActorContext<PositionActor.Command>
) : EventSourcedBehaviorWithEnforcedReplies<PositionActor.Command, PositionActor.Event, PositionActor.State>(persistenceId) {

    companion object {
        fun create(userId: String): Behavior<Command> =
            Behaviors.setup { ctx ->
                PositionActor(PersistenceId.ofUniqueId("PositionActor|$userId"), ctx)
            }
    }

    // Commands
    sealed interface Command : SerializationMarker
    data class UpdatePosition(
        val symbol: String,
        val quantity: BigDecimal,
        val side: Side,
        val replyTo: ActorRef<Confirmation>? = null
    ) : Command
    data class GetPosition(
        val symbol: String,
        val replyTo: ActorRef<Confirmation>
    ) : Command

    // Events
    sealed interface Event : SerializationMarker
    data class PositionUpdatedEvt(
        val symbol: String,
        val quantity: BigDecimal,
        val side: Side
    ) : Event

    // Replies
    sealed interface Confirmation : SerializationMarker
    data class PositionResult(val symbol: String, val netPosition: BigDecimal) : Confirmation
    object Ack : Confirmation

    // State
    data class State(
        val positions: Map<String, BigDecimal> = emptyMap()
    ) : SerializationMarker

    enum class Side { BUY, SELL }

    override fun emptyState(): State = State()

    override fun commandHandler(): CommandHandlerWithReply<Command, Event, State> =
        newCommandHandlerWithReplyBuilder()
            .forAnyState()
            .onCommand(UpdatePosition::class.java) { state, cmd ->
                val delta = if (cmd.side == Side.BUY) cmd.quantity else cmd.quantity.negate()
                if (cmd.replyTo != null) {
                    Effect().persist(PositionUpdatedEvt(cmd.symbol, cmd.quantity, cmd.side))
                        .thenReply(cmd.replyTo) { Ack }
                } else {
                    Effect().persist(PositionUpdatedEvt(cmd.symbol, cmd.quantity, cmd.side))
                        .thenNoReply()
                }
            }
            .onCommand(GetPosition::class.java) { state, cmd ->
                val net = state.positions[cmd.symbol] ?: BigDecimal.ZERO
                Effect().reply(cmd.replyTo, PositionResult(cmd.symbol, net))
            }
            .build()

    override fun eventHandler(): EventHandler<State, Event> =
        newEventHandlerBuilder()
            .forAnyState()
            .onEvent(PositionUpdatedEvt::class.java) { state, evt ->
                val current = state.positions[evt.symbol] ?: BigDecimal.ZERO
                val delta = if (evt.side == Side.BUY) evt.quantity else evt.quantity.negate()
                val updated = current + delta
                state.copy(positions = state.positions + (evt.symbol to updated))
            }
            .build()
} 