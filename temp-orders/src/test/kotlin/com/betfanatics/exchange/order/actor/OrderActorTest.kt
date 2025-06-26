package com.betfanatics.exchange.order.actor

import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityRef
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import com.betfanatics.exchange.order.actor.common.OrderRequestDTO
import com.betfanatics.exchange.order.actor.common.OrderSide
import com.betfanatics.exchange.order.actor.common.OrderType
import com.betfanatics.exchange.order.actor.common.TimeInForce
import com.typesafe.config.ConfigFactory
import io.mockk.mockk
import com.betfanatics.exchange.order.actor.common.PositionActorResolver
import io.mockk.every
import com.betfanatics.exchange.order.actor.common.OrderProcessManagerResolver
class OrderActorTest {
    companion object {
        private val config = ConfigFactory.parseString("""
            pekko.persistence.journal.plugin = "pekko.persistence.journal.inmem"
            pekko.persistence.journal.inmem.class = "org.apache.pekko.persistence.journal.inmem.InmemJournal"
            pekko.persistence.snapshot-store.plugin = "pekko.persistence.snapshot-store.local"
            pekko.persistence.snapshot-store.local.dir = "target/snapshots"
        """.trimIndent())
        
        private val testKit = ActorTestKit.create(config)
        @JvmStatic
        @AfterAll
        fun teardown() {
            testKit.shutdownTestKit()
        }
    }

    @Test
    fun `should reply with OrderRejected if order already placed`() {
        val fixGatewayActor = testKit.createTestProbe<FixGatewayActor.Command>()
        val processManagerEntityRef = mockk<EntityRef<OrderProcessManager.Command>>(relaxed = true)
        val resolver: com.betfanatics.exchange.order.actor.common.OrderProcessManagerResolver = { _ -> processManagerEntityRef }
        val dummyPositionActorRef = mockk<EntityRef<PositionActor.Command>>()
        every { dummyPositionActorRef.tell(any()) } returns Unit
        val dummyPositionActorResolver: PositionActorResolver = { dummyPositionActorRef }
        val orderActor = testKit.spawn(OrderActor.create("order-1", fixGatewayActor.ref, resolver, dummyPositionActorResolver))
        val replyProbe = testKit.createTestProbe<OrderActor.Response>()
        val orderRequest = OrderRequestDTO(
            orderId = "order-1", 
            symbol = "AAPL", 
            side = OrderSide.BUY, 
            quantity = BigDecimal.TEN, 
            price = BigDecimal(100), 
            orderType = OrderType.LIMIT,
            timeInForce = TimeInForce.GTC,
            userId = "user-1"
        )

        // Place order first time
        orderActor.tell(OrderActor.PlaceOrder(orderRequest, orderRequest.userId, orderRequest.quantity, replyProbe.ref))
        // Try to place again
        orderActor.tell(OrderActor.PlaceOrder(orderRequest, orderRequest.userId, orderRequest.quantity, replyProbe.ref))
        
        val response = replyProbe.receiveMessage()
        assert(response is OrderActor.OrderRejected)
        assertEquals("order-1", (response as OrderActor.OrderRejected).orderId)
    }
} 