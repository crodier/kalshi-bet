package com.betfanatics.exchange.order.actor

import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.javadsl.Behaviors
import java.math.BigDecimal
import java.time.Instant
import org.apache.pekko.actor.typed.ActorRef
object KalshiWalletActor {
    sealed interface Command : SerializationMarker
    data class CreditFunds(val amount: BigDecimal, val userId: String, val correlationId: String, val replyTo: ActorRef<Response>) : Command
    data class DebitFunds(val amount: BigDecimal, val userId: String, val correlationId: String, val replyTo: ActorRef<Response>) : Command

    sealed interface Response : SerializationMarker
    data class CreditCompleted(val correlationId: String, val timestamp: Instant) : Response
    data class DebitCompleted(val correlationId: String, val timestamp: Instant) : Response
    data class CreditFailed(val correlationId: String, val reason: String) : Response
    data class DebitFailed(val correlationId: String, val reason: String) : Response

    // TODO: actually call the wallet
    fun create(): Behavior<Command> = Behaviors.setup { ctx ->
        Behaviors.receiveMessage { msg ->
            when (msg) {
                is CreditFunds -> {
                    ctx.log.info("KalshiWalletActor received CreditFunds for userId={} amount={} correlationId={}", msg.userId, msg.amount, msg.correlationId)
                    if (msg.amount == BigDecimal.valueOf(-1)) {
                        msg.replyTo.tell(CreditFailed(msg.correlationId, "Simulated credit failure"))
                        ctx.log.info("KalshiWalletActor sent CreditFailed for correlationId={}", msg.correlationId)
                    } else {
                        msg.replyTo.tell(CreditCompleted(msg.correlationId, Instant.now()))
                        ctx.log.info("KalshiWalletActor sent CreditCompleted for correlationId={}", msg.correlationId)
                    }
                    Behaviors.same()
                }
                is DebitFunds -> {
                    ctx.log.info("KalshiWalletActor received DebitFunds for userId={} amount={} correlationId={}", msg.userId, msg.amount, msg.correlationId)
                    msg.replyTo.tell(DebitCompleted(msg.correlationId, Instant.now()))
                    ctx.log.info("KalshiWalletActor sent DebitCompleted for correlationId={}", msg.correlationId)
                    Behaviors.same()
                }
                else -> Behaviors.unhandled()
            }
        }
    }
} 