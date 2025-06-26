package com.betfanatics.exchange.order.actor

import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit
import org.apache.pekko.actor.testkit.typed.javadsl.TestProbe
import org.apache.pekko.actor.typed.ActorRef
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import com.typesafe.config.ConfigFactory

class FBGWalletActorTest {
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
    fun `should instantiate FBGWalletActor`() {
        val actor = testKit.spawn(FBGWalletActor.create())
        assertNotNull(actor)
    }
} 