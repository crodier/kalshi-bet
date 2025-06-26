package com.betfanatics.exchange.order.actor

import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import com.typesafe.config.ConfigFactory
import java.math.BigDecimal
import com.betfanatics.exchange.order.actor.common.OrderRequestDTO
import com.betfanatics.exchange.order.actor.common.OrderSide
import com.betfanatics.exchange.order.actor.common.OrderType
import com.betfanatics.exchange.order.actor.common.TimeInForce
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityRef
import java.time.Duration
import io.mockk.mockk
import io.mockk.every
import org.apache.pekko.actor.typed.ActorRef

class OrderProcessManagerTest {
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

    // Helper to wrap a test probe as an EntityRef
    private fun probeBackedEntityRef(probe: ActorRef<PositionActor.Command>): EntityRef<PositionActor.Command> {
        val entityRef = mockk<EntityRef<PositionActor.Command>>()
        every { entityRef.tell(any()) } answers { arg ->
            probe.tell(arg.invocation.args[0] as PositionActor.Command)
            Unit
        }
        return entityRef
    }

    @Test
    fun `should instantiate and start workflow`() {
        val fbgWalletActor = testKit.createTestProbe<FBGWalletActor.Command>()
        val kalshiWalletActor = testKit.createTestProbe<KalshiWalletActor.Command>()
        val orderActorEntityRef = mockk<EntityRef<OrderActor.Command>>(relaxed = true)
        val resolver: com.betfanatics.exchange.order.actor.common.OrderActorResolver = { _ -> orderActorEntityRef }
        val positionActorProbe = testKit.createTestProbe<PositionActor.Command>()
        val positionResolver: com.betfanatics.exchange.order.actor.common.PositionActorResolver = { _ -> probeBackedEntityRef(positionActorProbe.ref) }
        val processManager = testKit.spawn(
            OrderProcessManager.create(
                orderId = "order-1",
                fbgWalletActor = fbgWalletActor.ref,
                kalshiWalletActor = kalshiWalletActor.ref,
                orderActorResolver = resolver,
                positionActorResolver = positionResolver
            )
        )
        val replyProbe = testKit.createTestProbe<OrderProcessManager.Confirmation>()
        val orderRequest = OrderRequestDTO(
            orderId = "order-1", 
            symbol = "AAPL", 
            side = OrderSide.BUY, 
            quantity = BigDecimal.ONE, 
            price = BigDecimal(100), 
            orderType = OrderType.LIMIT,
            timeInForce = TimeInForce.GTC,
            userId = "user-1"
        )

        processManager.tell(OrderProcessManager.StartWorkflow(orderRequest, replyProbe.ref))
        // Expect GetPosition and reply
        val getPosition = positionActorProbe.expectMessageClass(PositionActor.GetPosition::class.java)
        getPosition.replyTo.tell(PositionActor.PositionResult(getPosition.symbol, BigDecimal.ZERO))
        assertNotNull(processManager)
    }

    @Test
    fun `should return initial status on GetStatus`() {
        val fbgWalletActor = testKit.createTestProbe<FBGWalletActor.Command>()
        val kalshiWalletActor = testKit.createTestProbe<KalshiWalletActor.Command>()
        val orderActorEntityRef = mockk<EntityRef<OrderActor.Command>>(relaxed = true)
        val resolver: com.betfanatics.exchange.order.actor.common.OrderActorResolver = { _ -> orderActorEntityRef }
        val positionActorProbe = testKit.createTestProbe<PositionActor.Command>()
        val positionResolver: com.betfanatics.exchange.order.actor.common.PositionActorResolver = { _ -> probeBackedEntityRef(positionActorProbe.ref) }
        val processManager = testKit.spawn(OrderProcessManager.create(
            orderId = "order-123",
            fbgWalletActor = fbgWalletActor.ref,
            kalshiWalletActor = kalshiWalletActor.ref,
            orderActorResolver = resolver,
            positionActorResolver = positionResolver
        ))
        val probe = testKit.createTestProbe<OrderProcessManager.Confirmation>()
        processManager.tell(OrderProcessManager.GetStatus(probe.ref))
        val reply = probe.receiveMessage()
        assert(reply is OrderProcessManager.OrderResult)
        val status = reply as OrderProcessManager.OrderResult
        assert(status.orderId == "")
        assert(status.filledQty == java.math.BigDecimal.ZERO)
        assert(status.outcome == "PENDING")
    }

    @Test
    fun `should fail workflow if FBG debit fails with error response`() {
        val fbgWalletActor = testKit.createTestProbe<FBGWalletActor.Command>()
        val kalshiWalletActor = testKit.createTestProbe<KalshiWalletActor.Command>()
        val orderActorEntityRef = mockk<EntityRef<OrderActor.Command>>(relaxed = true)
        val resolver: com.betfanatics.exchange.order.actor.common.OrderActorResolver = { _ -> orderActorEntityRef }
        val positionActorProbe = testKit.createTestProbe<PositionActor.Command>()
        val positionResolver: com.betfanatics.exchange.order.actor.common.PositionActorResolver = { _ -> probeBackedEntityRef(positionActorProbe.ref) }
        val processManager = testKit.spawn(
            OrderProcessManager.create(
                orderId = "order-fbg-fail-response",
                fbgWalletActor = fbgWalletActor.ref,
                kalshiWalletActor = kalshiWalletActor.ref,
                orderActorResolver = resolver,
                positionActorResolver = positionResolver
            )
        )
        val replyProbe = testKit.createTestProbe<OrderProcessManager.Confirmation>()
        val orderRequest = OrderRequestDTO(
            orderId = "order-fbg-fail-response", 
            symbol = "AAPL", 
            side = OrderSide.BUY, 
            quantity = BigDecimal.valueOf(-1), 
            price = BigDecimal(100), 
            orderType = OrderType.LIMIT,
            timeInForce = TimeInForce.GTC,
            userId = "user-1"
        )

        processManager.tell(OrderProcessManager.StartWorkflow(orderRequest, replyProbe.ref))
        // Expect GetPosition and reply
        val getPosition = positionActorProbe.expectMessageClass(PositionActor.GetPosition::class.java)
        getPosition.replyTo.tell(PositionActor.PositionResult(getPosition.symbol, BigDecimal.ZERO))
        // FBGWalletActor should receive DebitFunds
        val debitFunds = fbgWalletActor.expectMessageClass(FBGWalletActor.DebitFunds::class.java)
        // Simulate failure response
        debitFunds.replyTo.tell(FBGWalletActor.DebitFailed(debitFunds.correlationId, "Simulated failure"))
        // Should receive WorkflowFailed response
        val reply = replyProbe.receiveMessage()
        assert(reply is OrderProcessManager.WorkflowFailed)
        val failed = reply as OrderProcessManager.WorkflowFailed
        assert(failed.reason.contains("FBG debit failed"))
        // Assert no further messages sent to FBGWalletActor or KalshiWalletActor
        fbgWalletActor.expectNoMessage(Duration.ofMillis(100))
        kalshiWalletActor.expectNoMessage(Duration.ofMillis(100))
    }

    @Test
    fun `should compensate FBG wallet if Kalshi deposit fails with error response`() {
        val fbgWalletActor = testKit.createTestProbe<FBGWalletActor.Command>()
        val kalshiWalletActor = testKit.createTestProbe<KalshiWalletActor.Command>()
        val orderActorEntityRef = mockk<EntityRef<OrderActor.Command>>(relaxed = true)
        val resolver: com.betfanatics.exchange.order.actor.common.OrderActorResolver = { _ -> orderActorEntityRef }
        val positionActorProbe = testKit.createTestProbe<PositionActor.Command>()
        val positionResolver: com.betfanatics.exchange.order.actor.common.PositionActorResolver = { _ -> probeBackedEntityRef(positionActorProbe.ref) }
        val processManager = testKit.spawn(
            OrderProcessManager.create(
                orderId = "order-kalshi-fail-error",
                fbgWalletActor = fbgWalletActor.ref,
                kalshiWalletActor = kalshiWalletActor.ref,
                orderActorResolver = resolver,
                positionActorResolver = positionResolver
            )
        )
        val replyProbe = testKit.createTestProbe<OrderProcessManager.Confirmation>()
        val orderRequest = OrderRequestDTO(
            orderId = "order-kalshi-fail-error", 
            symbol = "AAPL", 
            side = OrderSide.BUY, 
            quantity = BigDecimal.TEN, 
            price = BigDecimal(100), 
            orderType = OrderType.LIMIT,
            timeInForce = TimeInForce.GTC,
            userId = "user-1"
        )

        processManager.tell(OrderProcessManager.StartWorkflow(orderRequest, replyProbe.ref))
        // Expect GetPosition and reply
        val getPosition = positionActorProbe.expectMessageClass(PositionActor.GetPosition::class.java)
        getPosition.replyTo.tell(PositionActor.PositionResult(getPosition.symbol, BigDecimal.ZERO))
        // FBGWalletActor: DebitFunds succeeds
        val fbgDebitFunds = fbgWalletActor.expectMessageClass(FBGWalletActor.DebitFunds::class.java)
        fbgDebitFunds.replyTo.tell(FBGWalletActor.DebitCompleted(fbgDebitFunds.correlationId, java.time.Instant.now()))
        // KalshiWalletActor: DebitFunds (omnibus) succeeds
        val omnibusDebitFunds = kalshiWalletActor.expectMessageClass(KalshiWalletActor.DebitFunds::class.java)
        omnibusDebitFunds.replyTo.tell(KalshiWalletActor.DebitCompleted(omnibusDebitFunds.correlationId, java.time.Instant.now()))
        // KalshiWalletActor: CreditFunds fails (send negative amount to trigger failure)
        val kalshiCreditFunds = kalshiWalletActor.expectMessageClass(KalshiWalletActor.CreditFunds::class.java)
        kalshiCreditFunds.replyTo.tell(KalshiWalletActor.CreditFailed(kalshiCreditFunds.correlationId, "Simulated credit failure"))
        // FBGWalletActor should receive CreditFunds to compensate
        val fbgCreditFunds = fbgWalletActor.expectMessageClass(FBGWalletActor.CreditFunds::class.java)
        fbgCreditFunds.replyTo.tell(FBGWalletActor.CreditCompleted(fbgCreditFunds.correlationId, java.time.Instant.now()))
        // Should receive WorkflowFailed response
        val reply = replyProbe.receiveMessage()
        assert(reply is OrderProcessManager.WorkflowFailed)
        val failed = reply as OrderProcessManager.WorkflowFailed
        assert(failed.reason.contains("failed"))
        // Assert no further messages sent to FBGWalletActor or KalshiWalletActor
        fbgWalletActor.expectNoMessage(Duration.ofMillis(100))
        kalshiWalletActor.expectNoMessage(Duration.ofMillis(100))
    }

    @Test
    fun `should compensate omnibus then FBG when order placement fails`() {
        val fbgWalletActor = testKit.createTestProbe<FBGWalletActor.Command>()
        val kalshiWalletActor = testKit.createTestProbe<KalshiWalletActor.Command>()
        val orderActorEntityRef = mockk<EntityRef<OrderActor.Command>>(relaxed = true)
        val resolver: com.betfanatics.exchange.order.actor.common.OrderActorResolver = { _ -> orderActorEntityRef }
        val positionActorProbe = testKit.createTestProbe<PositionActor.Command>()
        val positionResolver: com.betfanatics.exchange.order.actor.common.PositionActorResolver = { _ -> probeBackedEntityRef(positionActorProbe.ref) }
        val processManager = testKit.spawn(
            OrderProcessManager.create(
                orderId = "order-placement-fail",
                fbgWalletActor = fbgWalletActor.ref,
                kalshiWalletActor = kalshiWalletActor.ref,
                orderActorResolver = resolver,
                positionActorResolver = positionResolver
            )
        )
        val replyProbe = testKit.createTestProbe<OrderProcessManager.Confirmation>()
        val orderRequest = OrderRequestDTO(
            orderId = "order-placement-fail", 
            symbol = "AAPL", 
            side = OrderSide.BUY, 
            quantity = BigDecimal.TEN, 
            price = BigDecimal(100), 
            orderType = OrderType.LIMIT,
            timeInForce = TimeInForce.GTC,
            userId = "user-1"
        )

        processManager.tell(OrderProcessManager.StartWorkflow(orderRequest, replyProbe.ref))
        // Expect GetPosition and reply
        val getPosition = positionActorProbe.expectMessageClass(PositionActor.GetPosition::class.java)
        getPosition.replyTo.tell(PositionActor.PositionResult(getPosition.symbol, BigDecimal.ZERO))
        // Complete the wallet steps successfully
        val fbgDebitFunds = fbgWalletActor.expectMessageClass(FBGWalletActor.DebitFunds::class.java)
        fbgDebitFunds.replyTo.tell(FBGWalletActor.DebitCompleted(fbgDebitFunds.correlationId, java.time.Instant.now()))

        val omnibusDebitFunds = kalshiWalletActor.expectMessageClass(KalshiWalletActor.DebitFunds::class.java)
        omnibusDebitFunds.replyTo.tell(KalshiWalletActor.DebitCompleted(omnibusDebitFunds.correlationId, java.time.Instant.now()))

        val kalshiCreditFunds = kalshiWalletActor.expectMessageClass(KalshiWalletActor.CreditFunds::class.java)
        kalshiCreditFunds.replyTo.tell(KalshiWalletActor.CreditCompleted(kalshiCreditFunds.correlationId, java.time.Instant.now()))

        // Order placement fails with exchange rejection
        processManager.tell(OrderProcessManager.OrderPlacementFailed("Order rejected by exchange: Invalid symbol"))

        // Should receive OrderResult for the rejection
        val orderResult = replyProbe.receiveMessage()
        assert(orderResult is OrderProcessManager.OrderResult)
        val result = orderResult as OrderProcessManager.OrderResult
        assert(result.outcome == "REJECTED")
        assert(result.reason!!.contains("rejected by exchange"))

        // Compensation flow: First compensate omnibus (debit from Kalshi)
        val omnibusCompensationDebit = kalshiWalletActor.expectMessageClass(KalshiWalletActor.DebitFunds::class.java)
        omnibusCompensationDebit.replyTo.tell(KalshiWalletActor.DebitCompleted(omnibusCompensationDebit.correlationId, java.time.Instant.now()))

        // Then compensate FBG (credit back to user)
        val fbgCompensationCredit = fbgWalletActor.expectMessageClass(FBGWalletActor.CreditFunds::class.java)
        fbgCompensationCredit.replyTo.tell(FBGWalletActor.CreditCompleted(fbgCompensationCredit.correlationId, java.time.Instant.now()))

        // Should receive WorkflowFailed after compensation completes
        val failureReply = replyProbe.receiveMessage()
        assert(failureReply is OrderProcessManager.WorkflowFailed)
        val failed = failureReply as OrderProcessManager.WorkflowFailed
        assert(failed.reason.contains("failed"))
    }

    @Test
    fun `should handle FBG compensation failure catastrophically`() {
        val fbgWalletActor = testKit.createTestProbe<FBGWalletActor.Command>()
        val kalshiWalletActor = testKit.createTestProbe<KalshiWalletActor.Command>()
        val orderActorEntityRef = mockk<EntityRef<OrderActor.Command>>(relaxed = true)
        val resolver: com.betfanatics.exchange.order.actor.common.OrderActorResolver = { _ -> orderActorEntityRef }
        val positionActorProbe = testKit.createTestProbe<PositionActor.Command>()
        val positionResolver: com.betfanatics.exchange.order.actor.common.PositionActorResolver = { _ -> probeBackedEntityRef(positionActorProbe.ref) }
        val processManager = testKit.spawn(
            OrderProcessManager.create(
                orderId = "fbg-compensation-fail",
                fbgWalletActor = fbgWalletActor.ref,
                kalshiWalletActor = kalshiWalletActor.ref,
                orderActorResolver = resolver,
                positionActorResolver = positionResolver
            )
        )
        val replyProbe = testKit.createTestProbe<OrderProcessManager.Confirmation>()
        val orderRequest = OrderRequestDTO(
            orderId = "fbg-compensation-fail", 
            symbol = "AAPL", 
            side = OrderSide.BUY, 
            quantity = BigDecimal.TEN, 
            price = BigDecimal(100), 
            orderType = OrderType.LIMIT,
            timeInForce = TimeInForce.GTC,
            userId = "user-1"
        )

        processManager.tell(OrderProcessManager.StartWorkflow(orderRequest, replyProbe.ref))
        // Expect GetPosition and reply
        val getPosition = positionActorProbe.expectMessageClass(PositionActor.GetPosition::class.java)
        getPosition.replyTo.tell(PositionActor.PositionResult(getPosition.symbol, BigDecimal.ZERO))
        // Complete FBG and omnibus debits
        val fbgDebitFunds = fbgWalletActor.expectMessageClass(FBGWalletActor.DebitFunds::class.java)
        fbgDebitFunds.replyTo.tell(FBGWalletActor.DebitCompleted(fbgDebitFunds.correlationId, java.time.Instant.now()))

        val omnibusDebitFunds = kalshiWalletActor.expectMessageClass(KalshiWalletActor.DebitFunds::class.java)
        omnibusDebitFunds.replyTo.tell(KalshiWalletActor.DebitCompleted(omnibusDebitFunds.correlationId, java.time.Instant.now()))

        // Kalshi credit fails - triggers FBG compensation
        val kalshiCreditFunds = kalshiWalletActor.expectMessageClass(KalshiWalletActor.CreditFunds::class.java)
        kalshiCreditFunds.replyTo.tell(KalshiWalletActor.CreditFailed(kalshiCreditFunds.correlationId, "Kalshi service unavailable"))

        // FBG compensation also fails - catastrophic
        val fbgCompensationCredit = fbgWalletActor.expectMessageClass(FBGWalletActor.CreditFunds::class.java)
        fbgCompensationCredit.replyTo.tell(FBGWalletActor.CreditFailed(fbgCompensationCredit.correlationId, "FBG service down"))

        // Should receive WorkflowFailed with compensation failure message
        val failureReply = replyProbe.receiveMessage()
        assert(failureReply is OrderProcessManager.WorkflowFailed)
        val failed = failureReply as OrderProcessManager.WorkflowFailed
        assert(failed.reason.contains("Compensation failed"))
        assert(failed.reason.contains("FBG service down"))
    }

    @Test
    fun `should handle omnibus debit failure during normal workflow`() {
        val fbgWalletActor = testKit.createTestProbe<FBGWalletActor.Command>()
        val kalshiWalletActor = testKit.createTestProbe<KalshiWalletActor.Command>()
        val orderActorEntityRef = mockk<EntityRef<OrderActor.Command>>(relaxed = true)
        val resolver: com.betfanatics.exchange.order.actor.common.OrderActorResolver = { _ -> orderActorEntityRef }
        val positionActorProbe = testKit.createTestProbe<PositionActor.Command>()
        val positionResolver: com.betfanatics.exchange.order.actor.common.PositionActorResolver = { _ -> probeBackedEntityRef(positionActorProbe.ref) }
        val processManager = testKit.spawn(
            OrderProcessManager.create(
                orderId = "omnibus-debit-fail",
                fbgWalletActor = fbgWalletActor.ref,
                kalshiWalletActor = kalshiWalletActor.ref,
                orderActorResolver = resolver,
                positionActorResolver = positionResolver
            )
        )
        val replyProbe = testKit.createTestProbe<OrderProcessManager.Confirmation>()
        val orderRequest = OrderRequestDTO(
            orderId = "omnibus-debit-fail", 
            symbol = "AAPL", 
            side = OrderSide.BUY, 
            quantity = BigDecimal.valueOf(50), 
            price = BigDecimal(100), 
            orderType = OrderType.LIMIT,
            timeInForce = TimeInForce.GTC,
            userId = "user-1"
        )

        processManager.tell(OrderProcessManager.StartWorkflow(orderRequest, replyProbe.ref))
        // Expect GetPosition and reply
        val getPosition = positionActorProbe.expectMessageClass(PositionActor.GetPosition::class.java)
        getPosition.replyTo.tell(PositionActor.PositionResult(getPosition.symbol, BigDecimal.ZERO))
        // FBG debit succeeds
        val fbgDebitFunds = fbgWalletActor.expectMessageClass(FBGWalletActor.DebitFunds::class.java)
        fbgDebitFunds.replyTo.tell(FBGWalletActor.DebitCompleted(fbgDebitFunds.correlationId, java.time.Instant.now()))

        // Omnibus debit fails
        val omnibusDebitFunds = kalshiWalletActor.expectMessageClass(KalshiWalletActor.DebitFunds::class.java)
        omnibusDebitFunds.replyTo.tell(KalshiWalletActor.DebitFailed(omnibusDebitFunds.correlationId, "Insufficient omnibus funds"))

        // Should trigger FBG compensation
        val fbgCompensationCredit = fbgWalletActor.expectMessageClass(FBGWalletActor.CreditFunds::class.java)
        fbgCompensationCredit.replyTo.tell(FBGWalletActor.CreditCompleted(fbgCompensationCredit.correlationId, java.time.Instant.now()))

        // Should receive WorkflowFailed
        val failureReply = replyProbe.receiveMessage()
        assert(failureReply is OrderProcessManager.WorkflowFailed)
        val failed = failureReply as OrderProcessManager.WorkflowFailed
        assert(failed.reason.contains("failed"))
    }

    @Test
    fun `should handle order timeout with partial fill`() {
        val fbgWalletActor = testKit.createTestProbe<FBGWalletActor.Command>()
        val kalshiWalletActor = testKit.createTestProbe<KalshiWalletActor.Command>()
        val orderActorEntityRef = mockk<EntityRef<OrderActor.Command>>(relaxed = true)
        val resolver: com.betfanatics.exchange.order.actor.common.OrderActorResolver = { _ -> orderActorEntityRef }
        val positionActorProbe = testKit.createTestProbe<PositionActor.Command>()
        val positionResolver: com.betfanatics.exchange.order.actor.common.PositionActorResolver = { _ -> probeBackedEntityRef(positionActorProbe.ref) }
        val processManager = testKit.spawn(
            OrderProcessManager.create(
                orderId = "order-timeout-partial",
                fbgWalletActor = fbgWalletActor.ref,
                kalshiWalletActor = kalshiWalletActor.ref,
                orderActorResolver = resolver,
                positionActorResolver = positionResolver
            )
        )
        val replyProbe = testKit.createTestProbe<OrderProcessManager.Confirmation>()
        val orderRequest = OrderRequestDTO(
            orderId = "order-timeout-partial", 
            symbol = "AAPL", 
            side = OrderSide.BUY, 
            quantity = BigDecimal.valueOf(100), 
            price = BigDecimal(100), 
            orderType = OrderType.LIMIT,
            timeInForce = TimeInForce.GTC,
            userId = "user-1"
        )

        processManager.tell(OrderProcessManager.StartWorkflow(orderRequest, replyProbe.ref))
        // Expect GetPosition and reply
        val getPosition = positionActorProbe.expectMessageClass(PositionActor.GetPosition::class.java)
        getPosition.replyTo.tell(PositionActor.PositionResult(getPosition.symbol, BigDecimal.valueOf(0)))
        // Complete all wallet steps and order placement
        val fbgDebitFunds = fbgWalletActor.expectMessageClass(FBGWalletActor.DebitFunds::class.java)
        fbgDebitFunds.replyTo.tell(FBGWalletActor.DebitCompleted(fbgDebitFunds.correlationId, java.time.Instant.now()))

        val omnibusDebitFunds = kalshiWalletActor.expectMessageClass(KalshiWalletActor.DebitFunds::class.java)
        omnibusDebitFunds.replyTo.tell(KalshiWalletActor.DebitCompleted(omnibusDebitFunds.correlationId, java.time.Instant.now()))

        val kalshiCreditFunds = kalshiWalletActor.expectMessageClass(KalshiWalletActor.CreditFunds::class.java)
        kalshiCreditFunds.replyTo.tell(KalshiWalletActor.CreditCompleted(kalshiCreditFunds.correlationId, java.time.Instant.now()))

        // Order placed successfully, but then times out with partial fill
        processManager.tell(OrderProcessManager.OrderFillStatusReceived(
            OrderActor.OrderFillStatus("order-timeout-partial", BigDecimal.valueOf(30), "PARTIALLY_FILLED", isTimeout = true)
        ))

        // Should receive OrderResult indicating timeout with partial fill
        val orderResult = replyProbe.receiveMessage()
        assert(orderResult is OrderProcessManager.OrderResult)
        val result = orderResult as OrderProcessManager.OrderResult
        assert(result.outcome == "PARTIALLY_FILLED")
        assert(result.filledQty == BigDecimal.valueOf(30))
        assert(result.isTimeout == true)

        // No further wallet operations expected (orders can fill later)
        fbgWalletActor.expectNoMessage(Duration.ofMillis(100))
        kalshiWalletActor.expectNoMessage(Duration.ofMillis(100))
    }

    @Test
    fun `should handle order timeout with no fill`() {
        val fbgWalletActor = testKit.createTestProbe<FBGWalletActor.Command>()
        val kalshiWalletActor = testKit.createTestProbe<KalshiWalletActor.Command>()
        val orderActorEntityRef = mockk<EntityRef<OrderActor.Command>>(relaxed = true)
        val resolver: com.betfanatics.exchange.order.actor.common.OrderActorResolver = { _ -> orderActorEntityRef }
        val positionActorProbe = testKit.createTestProbe<PositionActor.Command>()
        val positionResolver: com.betfanatics.exchange.order.actor.common.PositionActorResolver = { _ -> probeBackedEntityRef(positionActorProbe.ref) }
        val processManager = testKit.spawn(
            OrderProcessManager.create(
                orderId = "order-timeout-none",
                fbgWalletActor = fbgWalletActor.ref,
                kalshiWalletActor = kalshiWalletActor.ref,
                orderActorResolver = resolver,
                positionActorResolver = positionResolver
            )
        )
        val replyProbe = testKit.createTestProbe<OrderProcessManager.Confirmation>()
        val orderRequest = OrderRequestDTO(
            orderId = "order-timeout-none", 
            symbol = "AAPL", 
            side = OrderSide.BUY, 
            quantity = BigDecimal.valueOf(100), 
            price = BigDecimal(100), 
            orderType = OrderType.LIMIT,
            timeInForce = TimeInForce.GTC,
            userId = "user-1"
        )

        processManager.tell(OrderProcessManager.StartWorkflow(orderRequest, replyProbe.ref))
        // Expect GetPosition and reply
        val getPosition = positionActorProbe.expectMessageClass(PositionActor.GetPosition::class.java)
        getPosition.replyTo.tell(PositionActor.PositionResult(getPosition.symbol, BigDecimal.valueOf(0)))
        // Complete all wallet steps and order placement
        val fbgDebitFunds = fbgWalletActor.expectMessageClass(FBGWalletActor.DebitFunds::class.java)
        fbgDebitFunds.replyTo.tell(FBGWalletActor.DebitCompleted(fbgDebitFunds.correlationId, java.time.Instant.now()))

        val omnibusDebitFunds = kalshiWalletActor.expectMessageClass(KalshiWalletActor.DebitFunds::class.java)
        omnibusDebitFunds.replyTo.tell(KalshiWalletActor.DebitCompleted(omnibusDebitFunds.correlationId, java.time.Instant.now()))

        val kalshiCreditFunds = kalshiWalletActor.expectMessageClass(KalshiWalletActor.CreditFunds::class.java)
        kalshiCreditFunds.replyTo.tell(KalshiWalletActor.CreditCompleted(kalshiCreditFunds.correlationId, java.time.Instant.now()))

        // Order placed successfully, but then times out with no fill
        processManager.tell(OrderProcessManager.OrderFillStatusReceived(
            OrderActor.OrderFillStatus("order-timeout-none", BigDecimal.ZERO, "NOT_FILLED", isTimeout = true)
        ))

        // Should receive OrderResult indicating timeout with no fill
        val orderResult = replyProbe.receiveMessage()
        assert(orderResult is OrderProcessManager.OrderResult)
        val result = orderResult as OrderProcessManager.OrderResult
        assert(result.outcome == "NOT_FILLED")
        assert(result.filledQty == BigDecimal.ZERO)
        assert(result.isTimeout == true)

        // No compensation yet - orders can still fill later
        fbgWalletActor.expectNoMessage(Duration.ofMillis(100))
        kalshiWalletActor.expectNoMessage(Duration.ofMillis(100))
    }

    @Test
    fun `should handle successful order completion with full fill`() {
        val fbgWalletActor = testKit.createTestProbe<FBGWalletActor.Command>()
        val kalshiWalletActor = testKit.createTestProbe<KalshiWalletActor.Command>()
        val orderActorEntityRef = mockk<EntityRef<OrderActor.Command>>(relaxed = true)
        val resolver: com.betfanatics.exchange.order.actor.common.OrderActorResolver = { _ -> orderActorEntityRef }
        val positionActorProbe = testKit.createTestProbe<PositionActor.Command>()
        val positionResolver: com.betfanatics.exchange.order.actor.common.PositionActorResolver = { _ -> probeBackedEntityRef(positionActorProbe.ref) }
        val processManager = testKit.spawn(
            OrderProcessManager.create(
                orderId = "order-success-full",
                fbgWalletActor = fbgWalletActor.ref,
                kalshiWalletActor = kalshiWalletActor.ref,
                orderActorResolver = resolver,
                positionActorResolver = positionResolver
            )
        )
        val replyProbe = testKit.createTestProbe<OrderProcessManager.Confirmation>()
        val orderRequest = OrderRequestDTO(
            orderId = "order-success-full", 
            symbol = "AAPL", 
            side = OrderSide.BUY, 
            quantity = BigDecimal.valueOf(100), 
            price = BigDecimal(100), 
            orderType = OrderType.LIMIT,
            timeInForce = TimeInForce.GTC,
            userId = "user-1"
        )

        processManager.tell(OrderProcessManager.StartWorkflow(orderRequest, replyProbe.ref))
        // Expect GetPosition and reply
        val getPosition = positionActorProbe.expectMessageClass(PositionActor.GetPosition::class.java)
        getPosition.replyTo.tell(PositionActor.PositionResult(getPosition.symbol, BigDecimal.valueOf(0)))
        // Complete all wallet steps and order placement
        val fbgDebitFunds = fbgWalletActor.expectMessageClass(FBGWalletActor.DebitFunds::class.java)
        fbgDebitFunds.replyTo.tell(FBGWalletActor.DebitCompleted(fbgDebitFunds.correlationId, java.time.Instant.now()))

        val omnibusDebitFunds = kalshiWalletActor.expectMessageClass(KalshiWalletActor.DebitFunds::class.java)
        omnibusDebitFunds.replyTo.tell(KalshiWalletActor.DebitCompleted(omnibusDebitFunds.correlationId, java.time.Instant.now()))

        val kalshiCreditFunds = kalshiWalletActor.expectMessageClass(KalshiWalletActor.CreditFunds::class.java)
        kalshiCreditFunds.replyTo.tell(KalshiWalletActor.CreditCompleted(kalshiCreditFunds.correlationId, java.time.Instant.now()))

        // Order fills completely
        processManager.tell(OrderProcessManager.OrderFillStatusReceived(
            OrderActor.OrderFillStatus("order-success-full", BigDecimal.valueOf(100), "FILLED", isTimeout = false)
        ))

        // Should receive OrderResult indicating successful completion
        val orderResult = replyProbe.receiveMessage()
        assert(orderResult is OrderProcessManager.OrderResult)
        val result = orderResult as OrderProcessManager.OrderResult
        assert(result.outcome == "FILLED")
        assert(result.filledQty == BigDecimal.valueOf(100))
        assert(result.isTimeout == false)

        // No compensation needed for successful orders
        fbgWalletActor.expectNoMessage(Duration.ofMillis(100))
        kalshiWalletActor.expectNoMessage(Duration.ofMillis(100))
    }

    @Test
    fun `should handle order cancellation after partial fill`() {
        val fbgWalletActor = testKit.createTestProbe<FBGWalletActor.Command>()
        val kalshiWalletActor = testKit.createTestProbe<KalshiWalletActor.Command>()
        val orderActorEntityRef = mockk<EntityRef<OrderActor.Command>>(relaxed = true)
        val resolver: com.betfanatics.exchange.order.actor.common.OrderActorResolver = { _ -> orderActorEntityRef }
        val positionActorProbe = testKit.createTestProbe<PositionActor.Command>()
        val positionResolver: com.betfanatics.exchange.order.actor.common.PositionActorResolver = { _ -> probeBackedEntityRef(positionActorProbe.ref) }
        val processManager = testKit.spawn(
            OrderProcessManager.create(
                orderId = "order-partial-cancel",
                fbgWalletActor = fbgWalletActor.ref,
                kalshiWalletActor = kalshiWalletActor.ref,
                orderActorResolver = resolver,
                positionActorResolver = positionResolver
            )
        )
        val replyProbe = testKit.createTestProbe<OrderProcessManager.Confirmation>()
        val orderRequest = OrderRequestDTO(
            orderId = "order-partial-cancel", 
            symbol = "AAPL", 
            side = OrderSide.BUY, 
            quantity = BigDecimal.valueOf(100), 
            price = BigDecimal(100), 
            orderType = OrderType.LIMIT,
            timeInForce = TimeInForce.GTC,
            userId = "user-1"
        )

        processManager.tell(OrderProcessManager.StartWorkflow(orderRequest, replyProbe.ref))
        // Expect GetPosition and reply
        val getPosition = positionActorProbe.expectMessageClass(PositionActor.GetPosition::class.java)
        getPosition.replyTo.tell(PositionActor.PositionResult(getPosition.symbol, BigDecimal.valueOf(0)))
        // Complete all wallet steps and order placement
        val fbgDebitFunds = fbgWalletActor.expectMessageClass(FBGWalletActor.DebitFunds::class.java)
        fbgDebitFunds.replyTo.tell(FBGWalletActor.DebitCompleted(fbgDebitFunds.correlationId, java.time.Instant.now()))

        val omnibusDebitFunds = kalshiWalletActor.expectMessageClass(KalshiWalletActor.DebitFunds::class.java)
        omnibusDebitFunds.replyTo.tell(KalshiWalletActor.DebitCompleted(omnibusDebitFunds.correlationId, java.time.Instant.now()))

        val kalshiCreditFunds = kalshiWalletActor.expectMessageClass(KalshiWalletActor.CreditFunds::class.java)
        kalshiCreditFunds.replyTo.tell(KalshiWalletActor.CreditCompleted(kalshiCreditFunds.correlationId, java.time.Instant.now()))

        // Order placed successfully, partially fills, then gets cancelled
        processManager.tell(OrderProcessManager.OrderFillStatusReceived(
            OrderActor.OrderFillStatus("order-partial-cancel", BigDecimal.valueOf(30), "PARTIALLY_FILLED", isTimeout = false)
        ))

        // No reply expected for partial fill
        replyProbe.expectNoMessage(Duration.ofMillis(100))

        // Then order gets cancelled by exchange
        processManager.tell(OrderProcessManager.OrderFillStatusReceived(
            OrderActor.OrderFillStatus("order-partial-cancel", BigDecimal.valueOf(30), "CANCELLED", isTimeout = false)
        ))

        // Should receive OrderResult indicating cancellation with partial fill
        val orderResult = replyProbe.receiveMessage()
        assert(orderResult is OrderProcessManager.OrderResult)
        val result = orderResult as OrderProcessManager.OrderResult
        assert(result.outcome == "CANCELLED")
        assert(result.filledQty == BigDecimal.valueOf(30))
        assert(result.isTimeout == false)

        // No automatic compensation triggered (requires manual intervention for partial cancellations)
        fbgWalletActor.expectNoMessage(Duration.ofMillis(100))
        kalshiWalletActor.expectNoMessage(Duration.ofMillis(100))
    }

    @Test
    fun `should handle order cancellation with no fill`() {
        val fbgWalletActor = testKit.createTestProbe<FBGWalletActor.Command>()
        val kalshiWalletActor = testKit.createTestProbe<KalshiWalletActor.Command>()
        val orderActorEntityRef = mockk<EntityRef<OrderActor.Command>>(relaxed = true)
        val resolver: com.betfanatics.exchange.order.actor.common.OrderActorResolver = { _ -> orderActorEntityRef }
        val positionActorProbe = testKit.createTestProbe<PositionActor.Command>()
        val positionResolver: com.betfanatics.exchange.order.actor.common.PositionActorResolver = { _ -> probeBackedEntityRef(positionActorProbe.ref) }
        val processManager = testKit.spawn(
            OrderProcessManager.create(
                orderId = "order-full-cancel",
                fbgWalletActor = fbgWalletActor.ref,
                kalshiWalletActor = kalshiWalletActor.ref,
                orderActorResolver = resolver,
                positionActorResolver = positionResolver
            )
        )
        val replyProbe = testKit.createTestProbe<OrderProcessManager.Confirmation>()
        val orderRequest = OrderRequestDTO(
            orderId = "order-full-cancel", 
            symbol = "AAPL", 
            side = OrderSide.BUY, 
            quantity = BigDecimal.valueOf(100), 
            price = BigDecimal(100), 
            orderType = OrderType.LIMIT,
            timeInForce = TimeInForce.GTC,
            userId = "user-1"
        )

        processManager.tell(OrderProcessManager.StartWorkflow(orderRequest, replyProbe.ref))
        // Expect GetPosition and reply
        val getPosition = positionActorProbe.expectMessageClass(PositionActor.GetPosition::class.java)
        getPosition.replyTo.tell(PositionActor.PositionResult(getPosition.symbol, BigDecimal.valueOf(0)))
        // Complete all wallet steps and order placement
        val fbgDebitFunds = fbgWalletActor.expectMessageClass(FBGWalletActor.DebitFunds::class.java)
        fbgDebitFunds.replyTo.tell(FBGWalletActor.DebitCompleted(fbgDebitFunds.correlationId, java.time.Instant.now()))

        val omnibusDebitFunds = kalshiWalletActor.expectMessageClass(KalshiWalletActor.DebitFunds::class.java)
        omnibusDebitFunds.replyTo.tell(KalshiWalletActor.DebitCompleted(omnibusDebitFunds.correlationId, java.time.Instant.now()))

        val kalshiCreditFunds = kalshiWalletActor.expectMessageClass(KalshiWalletActor.CreditFunds::class.java)
        kalshiCreditFunds.replyTo.tell(KalshiWalletActor.CreditCompleted(kalshiCreditFunds.correlationId, java.time.Instant.now()))

        // Order placed but then gets cancelled without any fills
        processManager.tell(OrderProcessManager.OrderFillStatusReceived(
            OrderActor.OrderFillStatus("order-full-cancel", BigDecimal.ZERO, "CANCELLED", isTimeout = false)
        ))

        // Should receive OrderResult indicating cancellation with no fill
        val orderResult = replyProbe.receiveMessage()
        assert(orderResult is OrderProcessManager.OrderResult)
        val result = orderResult as OrderProcessManager.OrderResult
        assert(result.outcome == "CANCELLED")
        assert(result.filledQty == BigDecimal.ZERO)
        assert(result.isTimeout == false)

        // TODO: In future, this should trigger full compensation since no shares were received
        // For now, requires manual intervention
        fbgWalletActor.expectNoMessage(Duration.ofMillis(100))
        kalshiWalletActor.expectNoMessage(Duration.ofMillis(100))
    }

    // TODO NEXT - implement partial compensation logic for cancelled orders
    // TODO NEXT - consider event-driven compensation that works even when controller is gone
    // TODO NEXT - add monitoring/alerting for orders requiring manual compensation intervention
} 