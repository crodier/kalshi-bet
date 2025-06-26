package com.betfanatics.exchange.order.actor

import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import java.math.BigDecimal
import com.betfanatics.exchange.order.actor.common.OrderRequestDTO
import com.betfanatics.exchange.order.actor.common.OrderSide
import com.betfanatics.exchange.order.actor.common.OrderType
import com.betfanatics.exchange.order.actor.common.TimeInForce
import com.typesafe.config.ConfigFactory
import io.mockk.mockk
import io.mockk.every
import io.mockk.verify
import com.betfanatics.exchange.order.messaging.KafkaSender
import com.betfanatics.exchange.order.config.KafkaTopicsConfig
import com.betfanatics.exchange.order.model.messaging.KafkaSendResult
import org.apache.pekko.actor.typed.ActorRef
import java.util.concurrent.CompletableFuture
import java.time.Duration
import java.time.Instant

class EMSActorTest {
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

    private lateinit var kafkaSender: KafkaSender
    private lateinit var kafkaTopicsConfig: KafkaTopicsConfig
    private lateinit var fixGatewayActor: ActorRef<FixGatewayActor.Command>

    @BeforeEach
    fun setup() {
        kafkaSender = mockk()
        kafkaTopicsConfig = KafkaTopicsConfig().apply {
            fixExecutionTopic = KafkaTopicsConfig.Topic().apply { name = "FIX-EXECUTION-TEST" }
        }
        fixGatewayActor = mockk()
        
        // Mock successful Kafka send by default
        every { kafkaSender.send(any(), any(), any()) } returns 
            CompletableFuture.completedFuture(KafkaSendResult.success())
    }

    @Test
    fun `should process order from Kafka message successfully`() {
        // Given
        val orderId = "test-order-1"
        val userId = "test-user-1"
        
        val orderMessage = mapOf(
            "orderId" to orderId,
            "userId" to userId,
            "symbol" to "TRUMP-2024",
            "side" to "BUY",
            "quantity" to BigDecimal("100"),
            "price" to BigDecimal("50.00"),
            "orderType" to "LIMIT",
            "timeInForce" to "GTC"
        )

        val actor = testKit.spawn(EMSActor.create(
            orderId, kafkaSender, kafkaTopicsConfig, null)) // No FIX gateway for testing
        val replyProbe = testKit.createTestProbe<EMSActor.Response>()

        // When
        actor.tell(EMSActor.ProcessOrderFromKafka(orderMessage, replyProbe.ref))

        // Then
        val response = replyProbe.receiveMessage(Duration.ofSeconds(5))
        assertTrue(response is EMSActor.OrderProcessingStarted)
        assertEquals(orderId, (response as EMSActor.OrderProcessingStarted).orderId)

        // Verify execution status was published to Kafka
        verify(exactly = 1) { kafkaSender.send(any(), any(), any()) }
    }

    @Test
    fun `should handle invalid Kafka message format`() {
        // Given
        val orderId = "test-order-2"
        
        val invalidOrderMessage = mapOf(
            "orderId" to orderId,
            // Missing required fields
            "symbol" to "TRUMP-2024"
        )

        val actor = testKit.spawn(EMSActor.create(
            orderId, kafkaSender, kafkaTopicsConfig, null))
        val replyProbe = testKit.createTestProbe<EMSActor.Response>()

        // When
        actor.tell(EMSActor.ProcessOrderFromKafka(invalidOrderMessage, replyProbe.ref))

        // Then
        val response = replyProbe.receiveMessage(Duration.ofSeconds(5))
        assertTrue(response is EMSActor.ProcessingFailed)
        assertTrue((response as EMSActor.ProcessingFailed).reason.contains("parse Kafka message"))
    }

    @Test
    fun `should convert order to FIX message successfully`() {
        // Given
        val orderId = "test-order-3"
        val userId = "test-user-3"
        
        val orderRequest = OrderRequestDTO(
            orderId = orderId,
            symbol = "TRUMP-2024",
            side = OrderSide.BUY,
            quantity = BigDecimal("100"),
            price = BigDecimal("50.00"),
            orderType = OrderType.LIMIT,
            timeInForce = TimeInForce.GTC,
            userId = userId
        )

        val actor = testKit.spawn(EMSActor.create(
            orderId, kafkaSender, kafkaTopicsConfig, null))
        val replyProbe = testKit.createTestProbe<EMSActor.Response>()

        // When
        actor.tell(EMSActor.ConvertToFixMessage(orderRequest, replyProbe.ref))

        // Then
        val response = replyProbe.receiveMessage(Duration.ofSeconds(5))
        assertTrue(response is EMSActor.FixMessageCreated)
        val fixResponse = response as EMSActor.FixMessageCreated
        assertEquals(orderId, fixResponse.orderId)
        assertTrue(fixResponse.fixMessageId.startsWith("FIX_$orderId"))

        // Verify execution status was published to Kafka
        verify(exactly = 1) { kafkaSender.send(any(), any(), any()) }
    }

    @Test
    fun `should handle FIX message send success`() {
        // Given
        val orderId = "test-order-4"
        val fixMessageId = "FIX_test-order-4_123456"

        val actor = testKit.spawn(EMSActor.create(
            orderId, kafkaSender, kafkaTopicsConfig, null))

        // When
        actor.tell(EMSActor.FixMessageSent(orderId, true, fixMessageId))

        // Then - should publish success status to Kafka
        verify(exactly = 1) { kafkaSender.send(any(), any(), any()) }
    }

    @Test
    fun `should handle FIX message send failure`() {
        // Given
        val orderId = "test-order-5"
        val fixMessageId = "FIX_test-order-5_123456"

        val actor = testKit.spawn(EMSActor.create(
            orderId, kafkaSender, kafkaTopicsConfig, null))

        // When
        actor.tell(EMSActor.FixMessageSent(orderId, false, fixMessageId))

        // Then - should publish failure status to Kafka
        verify(exactly = 1) { kafkaSender.send(any(), any(), any()) }
    }

    @Test
    fun `should process execution report successfully`() {
        // Given
        val orderId = "test-order-6"
        val executionId = "exec-123"
        
        val executionReport = EMSActor.ExecutionReport(
            orderId = orderId,
            executionId = executionId,
            status = "FILLED",
            filledQuantity = BigDecimal("100"),
            remainingQuantity = BigDecimal.ZERO,
            avgPrice = BigDecimal("50.00"),
            timestamp = Instant.now()
        )

        val actor = testKit.spawn(EMSActor.create(
            orderId, kafkaSender, kafkaTopicsConfig, null))

        // When
        actor.tell(EMSActor.ExecutionReportReceived(orderId, executionReport))

        // Then - should publish execution report to Kafka
        verify(exactly = 1) { kafkaSender.send(any(), any(), any()) }
    }

    @Test
    fun `should handle Kafka publishing failure gracefully`() {
        // Given
        val orderId = "test-order-7"
        
        // Mock Kafka failure
        every { kafkaSender.send(any(), any(), any()) } returns 
            CompletableFuture.completedFuture(KafkaSendResult.failed("Kafka error"))
        
        val orderMessage = mapOf(
            "orderId" to orderId,
            "userId" to "test-user",
            "symbol" to "TRUMP-2024",
            "side" to "BUY",
            "quantity" to BigDecimal("100"),
            "price" to BigDecimal("50.00"),
            "orderType" to "LIMIT",
            "timeInForce" to "GTC"
        )

        val actor = testKit.spawn(EMSActor.create(
            orderId, kafkaSender, kafkaTopicsConfig, null))
        val replyProbe = testKit.createTestProbe<EMSActor.Response>()

        // When
        actor.tell(EMSActor.ProcessOrderFromKafka(orderMessage, replyProbe.ref))

        // Then - should still process but log error
        val response = replyProbe.receiveMessage(Duration.ofSeconds(5))
        assertTrue(response is EMSActor.OrderProcessingStarted)
        
        // Kafka send should still be attempted
        verify(exactly = 1) { kafkaSender.send(any(), any(), any()) }
    }

    @Test
    fun `should generate unique FIX message IDs`() {
        // Given
        val orderId1 = "test-order-8"
        val orderId2 = "test-order-9"
        
        val orderRequest1 = OrderRequestDTO(
            orderId = orderId1,
            symbol = "TRUMP-2024",
            side = OrderSide.BUY,
            quantity = BigDecimal("100"),
            price = BigDecimal("50.00"),
            orderType = OrderType.LIMIT,
            timeInForce = TimeInForce.GTC,
            userId = "test-user-1"
        )
        
        val orderRequest2 = OrderRequestDTO(
            orderId = orderId2,
            symbol = "BIDEN-2024",
            side = OrderSide.SELL,
            quantity = BigDecimal("200"),
            price = BigDecimal("30.00"),
            orderType = OrderType.LIMIT,
            timeInForce = TimeInForce.GTC,
            userId = "test-user-2"
        )

        val actor1 = testKit.spawn(EMSActor.create(
            orderId1, kafkaSender, kafkaTopicsConfig, null))
        val actor2 = testKit.spawn(EMSActor.create(
            orderId2, kafkaSender, kafkaTopicsConfig, null))
        
        val replyProbe1 = testKit.createTestProbe<EMSActor.Response>()
        val replyProbe2 = testKit.createTestProbe<EMSActor.Response>()

        // When
        actor1.tell(EMSActor.ConvertToFixMessage(orderRequest1, replyProbe1.ref))
        actor2.tell(EMSActor.ConvertToFixMessage(orderRequest2, replyProbe2.ref))

        // Then
        val response1 = replyProbe1.receiveMessage(Duration.ofSeconds(5))
        val response2 = replyProbe2.receiveMessage(Duration.ofSeconds(5))
        
        assertTrue(response1 is EMSActor.FixMessageCreated)
        assertTrue(response2 is EMSActor.FixMessageCreated)
        
        val fixId1 = (response1 as EMSActor.FixMessageCreated).fixMessageId
        val fixId2 = (response2 as EMSActor.FixMessageCreated).fixMessageId
        
        // FIX message IDs should be different
        assertNotEquals(fixId1, fixId2)
        assertTrue(fixId1.startsWith("FIX_$orderId1"))
        assertTrue(fixId2.startsWith("FIX_$orderId2"))
    }

    @Test
    fun `should maintain state across multiple commands`() {
        // Given
        val orderId = "test-order-10"
        val userId = "test-user-10"
        
        val orderMessage = mapOf(
            "orderId" to orderId,
            "userId" to userId,
            "symbol" to "TRUMP-2024",
            "side" to "BUY",
            "quantity" to BigDecimal("100"),
            "price" to BigDecimal("50.00"),
            "orderType" to "LIMIT",
            "timeInForce" to "GTC"
        )

        val actor = testKit.spawn(EMSActor.create(
            orderId, kafkaSender, kafkaTopicsConfig, null))
        val replyProbe = testKit.createTestProbe<EMSActor.Response>()

        // When - process order from Kafka
        actor.tell(EMSActor.ProcessOrderFromKafka(orderMessage, replyProbe.ref))
        val response1 = replyProbe.receiveMessage(Duration.ofSeconds(5))
        
        // When - simulate FIX message sent
        actor.tell(EMSActor.FixMessageSent(orderId, true, "FIX_123"))
        
        // When - simulate execution report
        val executionReport = EMSActor.ExecutionReport(
            orderId = orderId,
            executionId = "exec-456",
            status = "PARTIALLY_FILLED",
            filledQuantity = BigDecimal("50"),
            remainingQuantity = BigDecimal("50"),
            avgPrice = BigDecimal("50.00"),
            timestamp = Instant.now()
        )
        actor.tell(EMSActor.ExecutionReportReceived(orderId, executionReport))

        // Then - all commands should be processed successfully
        assertTrue(response1 is EMSActor.OrderProcessingStarted)
        
        // Multiple Kafka publishes should occur
        verify(atLeast = 3) { kafkaSender.send(any(), any(), any()) }
    }
}