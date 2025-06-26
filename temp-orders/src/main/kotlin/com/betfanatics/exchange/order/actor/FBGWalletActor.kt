package com.betfanatics.exchange.order.actor

import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.javadsl.Behaviors
import java.math.BigDecimal
import java.time.Instant
import org.apache.pekko.actor.typed.ActorRef
object FBGWalletActor {
    sealed interface Command : SerializationMarker
    data class DebitFunds(val amount: BigDecimal, val userId: String, val correlationId: String, val replyTo: ActorRef<Response>) : Command
    data class CreditFunds(val amount: BigDecimal, val userId: String, val correlationId: String, val replyTo: ActorRef<Response>) : Command

    sealed interface Response : SerializationMarker
    data class DebitCompleted(val correlationId: String, val timestamp: Instant) : Response
    data class DebitFailed(val correlationId: String, val reason: String) : Response
    data class CreditCompleted(val correlationId: String, val timestamp: Instant) : Response
    data class CreditFailed(val correlationId: String, val reason: String) : Response

    // TODO: actually call the wallet
    fun create(): Behavior<Command> = Behaviors.setup { ctx ->
        Behaviors.receiveMessage { msg ->
            when (msg) {
                is DebitFunds -> {
                    ctx.log.info("FBGWalletActor received DebitFunds for userId={} amount={} correlationId={}", msg.userId, msg.amount, msg.correlationId)
                    if (msg.amount == BigDecimal.valueOf(-1)) {
                        msg.replyTo.tell(DebitFailed(msg.correlationId, "Simulated failure"))
                        ctx.log.info("FBGWalletActor sent DebitFailed for correlationId={}", msg.correlationId)
                    } else {
                        msg.replyTo.tell(DebitCompleted(msg.correlationId, Instant.now()))
                        ctx.log.info("FBGWalletActor sent DebitCompleted for correlationId={}", msg.correlationId)
                    }
                    Behaviors.same()
                }
                is CreditFunds -> {
                    ctx.log.info("FBGWalletActor received CreditFunds for userId={} amount={} correlationId={}", msg.userId, msg.amount, msg.correlationId)
                    if (msg.amount == BigDecimal.valueOf(-2)) {
                        msg.replyTo.tell(CreditFailed(msg.correlationId, "Simulated credit failure"))
                        ctx.log.info("FBGWalletActor sent CreditFailed for correlationId={}", msg.correlationId)
                    } else {
                        msg.replyTo.tell(CreditCompleted(msg.correlationId, Instant.now()))
                        ctx.log.info("FBGWalletActor sent CreditCompleted for correlationId={}", msg.correlationId)
                    }
                    Behaviors.same()
                }
                else -> Behaviors.unhandled()
            }
        }
    }
} 