package com.betfanatics.exchange.order.projection

import com.betfanatics.exchange.order.actor.OrderActor
import org.apache.pekko.Done
import org.apache.pekko.persistence.query.typed.EventEnvelope
import org.apache.pekko.projection.r2dbc.javadsl.R2dbcHandler
import org.apache.pekko.projection.r2dbc.javadsl.R2dbcSession
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage

@Component
open class OrderProjectionHandler(
    private val repository: OrderProjectionRepository
) : R2dbcHandler<EventEnvelope<OrderActor.Event>>() {
    private val log = LoggerFactory.getLogger(OrderProjectionHandler::class.java)

    // Consumes the events from the order actor, and produces a read view
    // TODO be more defensive.
    // NB this is currently more of a list of orders, than a view of positions, which we may want to change
    override open fun process(session: R2dbcSession, envelope: EventEnvelope<OrderActor.Event>): CompletionStage<Done> {
        val event = envelope.event()

        log.debug("Processing order event: {}", event)

        return when (event) {
            is OrderActor.OrderPlacedEvt -> {
                repository.insertOrder(
                    orderId = event.orderId,
                    userId = event.userId,
                    symbol = event.symbol,
                    side = event.side.name,
                    amount = event.amount,
                    filledQty = java.math.BigDecimal.ZERO,
                    status = "PLACED",
                    timestamp = event.timestamp
                ).thenReturn(Done.getInstance()).toFuture()
            }
            is OrderActor.OrderFilledEvt -> {
                val orderId = envelope.persistenceId().split("|").last() // this is kind of horrid, more idomatic way?

                repository.updateOrderFilled(
                    orderId = orderId,
                    filledQty = event.filledQty,
                    status = event.status,
                    timestamp = event.timestamp
                ).thenReturn(Done.getInstance()).toFuture()
            }
            // TODO handle other events
            else -> CompletableFuture.completedFuture(Done.getInstance())
        }
    }
} 