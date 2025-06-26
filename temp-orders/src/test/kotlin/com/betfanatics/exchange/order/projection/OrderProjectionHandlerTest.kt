package com.betfanatics.exchange.order.projection

import com.betfanatics.exchange.order.actor.OrderActor
import com.betfanatics.exchange.order.projection.OrderProjectionRepository
import com.betfanatics.exchange.order.projection.OrderProjectionHandler
import io.mockk.*
import org.apache.pekko.Done
import org.apache.pekko.persistence.query.typed.EventEnvelope
import org.apache.pekko.projection.r2dbc.javadsl.R2dbcSession
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import reactor.core.publisher.Mono
import java.math.BigDecimal
import java.time.Instant
import java.util.concurrent.CompletableFuture
import com.betfanatics.exchange.order.actor.common.OrderSide

class OrderProjectionHandlerTest {

    private val repository: OrderProjectionRepository = mockk(relaxed = true)
    private val handler = OrderProjectionHandler(repository)
    private val session: R2dbcSession = mockk()

    @AfterEach
    fun tearDown() {
        clearAllMocks()
    }

    @Test
    fun `should insert order on OrderPlacedEvt`() {
        val event = OrderActor.OrderPlacedEvt(
            orderId = "order-1",
            userId = "user-1",
            symbol = "AAPL",
            amount = BigDecimal("100"),
            side = OrderSide.BUY,
            timestamp = Instant.now()
        )
        val envelope = mockk<EventEnvelope<OrderActor.Event>>() {
            every { event() } returns event
        }
        every { repository.insertOrder(any(), any(), any(), any(), any(), any(), any(), any()) } returns Mono.empty()

        val result = handler.process(session, envelope)
        result.toCompletableFuture().join() // Wait for completion

        verify {
            repository.insertOrder(
                "order-1",
                "user-1",
                "AAPL",
                "BUY",
                BigDecimal("100"),
                BigDecimal.ZERO,
                "PLACED",
                any()
            )
        }
    }

    @Test
    fun `should update order on OrderFilledEvt`() {
        val event = OrderActor.OrderFilledEvt(
            userId = "user-1",
            symbol = "AAPL",
            filledQty = BigDecimal("50"),
            timestamp = Instant.now(),
            status = "FILLED"
        )
        val envelope = mockk<EventEnvelope<OrderActor.Event>>() {
            every { event() } returns event
            every { persistenceId() } returns "OrderActor|order-1"
        }
        every { repository.updateOrderFilled(any(), any(), any(), any()) } returns Mono.empty()

        val result = handler.process(session, envelope)
        result.toCompletableFuture().join() // Wait for completion

        verify {
            repository.updateOrderFilled(
                "order-1",
                BigDecimal("50"),
                "FILLED",
                any()
            )
        }
    }
} 