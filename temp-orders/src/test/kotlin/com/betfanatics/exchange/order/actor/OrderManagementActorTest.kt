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
import io.mockk.just
import io.mockk.runs
import com.betfanatics.exchange.order.messaging.KafkaSender
import com.betfanatics.exchange.order.config.RiskLimitsConfig
import com.betfanatics.exchange.order.config.KafkaTopicsConfig
import com.betfanatics.exchange.order.model.messaging.KafkaSendResult
import java.util.concurrent.CompletableFuture
import javax.sql.DataSource
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.time.Duration

class OrderManagementActorTest {
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
    private lateinit var riskLimitsConfig: RiskLimitsConfig
    private lateinit var kafkaTopicsConfig: KafkaTopicsConfig
    private lateinit var dataSource: DataSource
    private lateinit var connection: Connection
    private lateinit var preparedStatement: PreparedStatement
    private lateinit var resultSet: ResultSet

    @BeforeEach
    fun setup() {
        kafkaSender = mockk()
        riskLimitsConfig = RiskLimitsConfig().apply {
            maxBetAmount = BigDecimal("100000.00")
            maxUserTotalRisk = BigDecimal("1000000.00")
        }
        kafkaTopicsConfig = KafkaTopicsConfig().apply {
            fixOrderTopic = KafkaTopicsConfig.Topic().apply { name = "FIX-ORDER-TEST" }
        }
        
        // Mock database components
        dataSource = mockk()
        connection = mockk()
        preparedStatement = mockk()
        resultSet = mockk()
        
        every { dataSource.connection } returns connection
        every { connection.prepareStatement(any<String>()) } returns preparedStatement
        every { connection.autoCommit = any() } just runs
        every { connection.commit() } just runs
        every { connection.rollback() } just runs
        every { preparedStatement.setString(any(), any()) } just runs
        every { preparedStatement.setBigDecimal(any(), any()) } just runs
        every { preparedStatement.setBoolean(any(), any()) } just runs
        every { preparedStatement.setTimestamp(any(), any()) } just runs
        every { preparedStatement.executeUpdate() } returns 1
        every { preparedStatement.executeQuery() } returns resultSet
        every { preparedStatement.close() } just runs
        every { connection.close() } just runs
    }

    @Test
    fun `should process valid order within risk limits`() {
        // Given
        val orderId = "test-order-1"
        val userId = "test-user-1"
        
        // Mock successful Kafka send
        every { kafkaSender.send(any(), any(), any()) } returns 
            CompletableFuture.completedFuture(KafkaSendResult.success())
        
        // Mock database - no existing user risk
        every { resultSet.next() } returns false
        every { resultSet.getBigDecimal("total_risk_amount") } returns null
        
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

        val actor = testKit.spawn(OrderManagementActor.create(
            orderId, kafkaSender, riskLimitsConfig, kafkaTopicsConfig, dataSource))
        val replyProbe = testKit.createTestProbe<OrderManagementActor.Response>()

        // When
        actor.tell(OrderManagementActor.ProcessOrder(orderRequest, replyProbe.ref))

        // Then
        val response = replyProbe.receiveMessage(Duration.ofSeconds(5))
        assertTrue(response is OrderManagementActor.OrderAccepted)
        assertEquals(orderId, (response as OrderManagementActor.OrderAccepted).orderId)

        // Verify database interactions
        verify(exactly = 1) { preparedStatement.executeUpdate() }
        verify(exactly = 1) { kafkaSender.send(any(), any(), any()) }
    }

    @Test
    fun `should reject order exceeding per-bet limit`() {
        // Given
        val orderId = "test-order-2"
        val userId = "test-user-2"
        
        // Mock database - no existing user risk
        every { resultSet.next() } returns false
        every { resultSet.getBigDecimal("total_risk_amount") } returns null
        
        val orderRequest = OrderRequestDTO(
            orderId = orderId,
            symbol = "TRUMP-2024",
            side = OrderSide.BUY,
            quantity = BigDecimal("10000"), // Large quantity
            price = BigDecimal("50.00"), // Risk = 10000 * 50 = 500,000 > 100,000 limit
            orderType = OrderType.LIMIT,
            timeInForce = TimeInForce.GTC,
            userId = userId
        )

        val actor = testKit.spawn(OrderManagementActor.create(
            orderId, kafkaSender, riskLimitsConfig, kafkaTopicsConfig, dataSource))
        val replyProbe = testKit.createTestProbe<OrderManagementActor.Response>()

        // When
        actor.tell(OrderManagementActor.ProcessOrder(orderRequest, replyProbe.ref))

        // Then
        val response = replyProbe.receiveMessage(Duration.ofSeconds(5))
        assertTrue(response is OrderManagementActor.OrderRejected)
        val rejectedResponse = response as OrderManagementActor.OrderRejected
        assertEquals(orderId, rejectedResponse.orderId)
        assertTrue(rejectedResponse.reason.contains("per-bet limit"))

        // Should not interact with Kafka or database for order persistence
        verify(exactly = 0) { kafkaSender.send(any(), any(), any()) }
    }

    @Test
    fun `should reject order exceeding user total risk limit`() {
        // Given
        val orderId = "test-order-3"
        val userId = "test-user-3"
        
        // Mock database - existing user has high risk
        every { resultSet.next() } returns true
        every { resultSet.getBigDecimal("total_risk_amount") } returns BigDecimal("950000.00")
        
        val orderRequest = OrderRequestDTO(
            orderId = orderId,
            symbol = "TRUMP-2024",
            side = OrderSide.BUY,
            quantity = BigDecimal("2000"),
            price = BigDecimal("30.00"), // Risk = 2000 * 30 = 60,000
            orderType = OrderType.LIMIT,
            timeInForce = TimeInForce.GTC,
            userId = userId
        )
        // Total risk would be 950,000 + 60,000 = 1,010,000 > 1,000,000 limit

        val actor = testKit.spawn(OrderManagementActor.create(
            orderId, kafkaSender, riskLimitsConfig, kafkaTopicsConfig, dataSource))
        val replyProbe = testKit.createTestProbe<OrderManagementActor.Response>()

        // When
        actor.tell(OrderManagementActor.ProcessOrder(orderRequest, replyProbe.ref))

        // Then
        val response = replyProbe.receiveMessage(Duration.ofSeconds(5))
        assertTrue(response is OrderManagementActor.OrderRejected)
        val rejectedResponse = response as OrderManagementActor.OrderRejected
        assertEquals(orderId, rejectedResponse.orderId)
        assertTrue(rejectedResponse.reason.contains("user total risk limit"))

        // Should query database for existing risk but not persist order
        verify(exactly = 1) { preparedStatement.executeQuery() }
        verify(exactly = 0) { kafkaSender.send(any(), any(), any()) }
    }

    @Test
    fun `should calculate risk correctly for SELL orders`() {
        // Given
        val orderId = "test-order-4"
        val userId = "test-user-4"
        
        // Mock successful Kafka send
        every { kafkaSender.send(any(), any(), any()) } returns 
            CompletableFuture.completedFuture(KafkaSendResult.success())
        
        // Mock database - no existing user risk
        every { resultSet.next() } returns false
        every { resultSet.getBigDecimal("total_risk_amount") } returns null
        
        val orderRequest = OrderRequestDTO(
            orderId = orderId,
            symbol = "TRUMP-2024",
            side = OrderSide.SELL, // SELL order
            quantity = BigDecimal("100"),
            price = BigDecimal("30.00"), // Risk = 100 * (100 - 30) = 7,000
            orderType = OrderType.LIMIT,
            timeInForce = TimeInForce.GTC,
            userId = userId
        )

        val actor = testKit.spawn(OrderManagementActor.create(
            orderId, kafkaSender, riskLimitsConfig, kafkaTopicsConfig, dataSource))
        val replyProbe = testKit.createTestProbe<OrderManagementActor.Response>()

        // When
        actor.tell(OrderManagementActor.ProcessOrder(orderRequest, replyProbe.ref))

        // Then
        val response = replyProbe.receiveMessage(Duration.ofSeconds(5))
        assertTrue(response is OrderManagementActor.OrderAccepted)
        assertEquals(orderId, (response as OrderManagementActor.OrderAccepted).orderId)

        // Verify correct risk calculation was used (7,000 should be within limits)
        verify(exactly = 1) { kafkaSender.send(any(), any(), any()) }
    }

    @Test
    fun `should handle duplicate order processing`() {
        // Given
        val orderId = "test-order-5"
        val userId = "test-user-5"
        
        // Mock successful first Kafka send
        every { kafkaSender.send(any(), any(), any()) } returns 
            CompletableFuture.completedFuture(KafkaSendResult.success())
        
        // Mock database - no existing user risk
        every { resultSet.next() } returns false
        every { resultSet.getBigDecimal("total_risk_amount") } returns null
        
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

        val actor = testKit.spawn(OrderManagementActor.create(
            orderId, kafkaSender, riskLimitsConfig, kafkaTopicsConfig, dataSource))
        val replyProbe1 = testKit.createTestProbe<OrderManagementActor.Response>()
        val replyProbe2 = testKit.createTestProbe<OrderManagementActor.Response>()

        // When - send first order
        actor.tell(OrderManagementActor.ProcessOrder(orderRequest, replyProbe1.ref))
        val response1 = replyProbe1.receiveMessage(Duration.ofSeconds(5))
        
        // When - send duplicate order
        actor.tell(OrderManagementActor.ProcessOrder(orderRequest, replyProbe2.ref))
        val response2 = replyProbe2.receiveMessage(Duration.ofSeconds(5))

        // Then
        assertTrue(response1 is OrderManagementActor.OrderAccepted)
        assertTrue(response2 is OrderManagementActor.OrderRejected)
        assertTrue((response2 as OrderManagementActor.OrderRejected).reason.contains("already processed"))
    }

    @Test
    fun `should handle Kafka publishing failure`() {
        // Given
        val orderId = "test-order-6"
        val userId = "test-user-6"
        
        // Mock failed Kafka send
        every { kafkaSender.send(any(), any(), any()) } returns 
            CompletableFuture.completedFuture(KafkaSendResult.failed("Kafka error"))
        
        // Mock database - no existing user risk
        every { resultSet.next() } returns false
        every { resultSet.getBigDecimal("total_risk_amount") } returns null
        
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

        val actor = testKit.spawn(OrderManagementActor.create(
            orderId, kafkaSender, riskLimitsConfig, kafkaTopicsConfig, dataSource))
        val replyProbe = testKit.createTestProbe<OrderManagementActor.Response>()

        // When
        actor.tell(OrderManagementActor.ProcessOrder(orderRequest, replyProbe.ref))

        // Then
        val response = replyProbe.receiveMessage(Duration.ofSeconds(5))
        assertTrue(response is OrderManagementActor.OrderRejected)
        assertTrue((response as OrderManagementActor.OrderRejected).reason.contains("Kafka publication failed"))
    }
}