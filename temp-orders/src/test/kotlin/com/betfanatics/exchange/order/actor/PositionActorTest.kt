package com.betfanatics.exchange.order.actor

import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit
import org.apache.pekko.actor.testkit.typed.javadsl.TestProbe
import org.apache.pekko.actor.typed.ActorRef
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import com.typesafe.config.ConfigFactory

class PositionActorTest {
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
    fun `position is updated correctly after buys and sells`() {
        val userId = "user-1"
        val symbol = "KXUSDTMIN-25DEC31-0.95"
        val positionActor = testKit.spawn(PositionActor.create(userId))
        val probe = testKit.createTestProbe<PositionActor.Confirmation>()

        // Buy 2 contracts
        positionActor.tell(PositionActor.UpdatePosition(symbol, BigDecimal(2), PositionActor.Side.BUY, probe.ref))
        probe.expectMessage(PositionActor.Ack)

        // Query position
        positionActor.tell(PositionActor.GetPosition(symbol, probe.ref))
        val result1 = probe.expectMessageClass(PositionActor.PositionResult::class.java)
        assertEquals(BigDecimal(2), result1.netPosition)

        // Sell 1 contract
        positionActor.tell(PositionActor.UpdatePosition(symbol, BigDecimal(1), PositionActor.Side.SELL, probe.ref))
        probe.expectMessage(PositionActor.Ack)

        // Query position again
        positionActor.tell(PositionActor.GetPosition(symbol, probe.ref))
        val result2 = probe.expectMessageClass(PositionActor.PositionResult::class.java)
        assertEquals(BigDecimal(1), result2.netPosition)
    }
} 