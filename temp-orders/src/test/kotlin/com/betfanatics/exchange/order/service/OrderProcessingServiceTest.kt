package com.betfanatics.exchange.order.service

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Assertions.*
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.*
import org.apache.pekko.cluster.sharding.typed.javadsl.ClusterSharding
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityTypeKey
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityRef
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.Scheduler
import org.apache.pekko.actor.typed.javadsl.AskPattern
import com.betfanatics.exchange.order.actor.OrderManagementActor
import com.betfanatics.exchange.order.actor.EMSActor
import com.betfanatics.exchange.order.actor.common.OrderRequestDTO
import com.betfanatics.exchange.order.actor.common.OrderSide
import com.betfanatics.exchange.order.actor.common.OrderType
import com.betfanatics.exchange.order.actor.common.TimeInForce
import com.betfanatics.exchange.order.config.KafkaTopicsConfig
import com.fasterxml.jackson.databind.ObjectMapper
import java.math.BigDecimal
import java.time.Instant
import java.util.concurrent.CompletableFuture
import java.time.Duration

class OrderProcessingServiceTest {

    @Mock
    private lateinit var clusterSharding: ClusterSharding

    @Mock
    private lateinit var actorSystem: ActorSystem<Void>

    @Mock
    private lateinit var scheduler: Scheduler

    @Mock
    private lateinit var kafkaTopicsConfig: KafkaTopicsConfig

    @Mock
    private lateinit var objectMapper: ObjectMapper

    @Mock
    private lateinit var orderManagementEntityRef: EntityRef<OrderManagementActor.Command>

    @Mock
    private lateinit var emsActorEntityRef: EntityRef<EMSActor.Command>

    private lateinit var orderProcessingService: OrderProcessingService

    @BeforeEach
    fun setup() {
        MockitoAnnotations.openMocks(this)
        
        // Setup Kafka topics config
        val fixOrderTopic = KafkaTopicsConfig.Topic().apply { name = "FIX-ORDER-TEST" }
        val fixExecutionTopic = KafkaTopicsConfig.Topic().apply { name = "FIX-EXECUTION-TEST" }
        
        whenever(kafkaTopicsConfig.fixOrderTopic).thenReturn(fixOrderTopic)
        whenever(kafkaTopicsConfig.fixExecutionTopic).thenReturn(fixExecutionTopic)
        
        // Setup actor system
        whenever(actorSystem.scheduler()).thenReturn(scheduler)
        
        // Setup cluster sharding
        whenever(clusterSharding.entityRefFor(any<EntityTypeKey<OrderManagementActor.Command>>(), any()))
            .thenReturn(orderManagementEntityRef)
        whenever(clusterSharding.entityRefFor(any<EntityTypeKey<EMSActor.Command>>(), any()))
            .thenReturn(emsActorEntityRef)

        orderProcessingService = OrderProcessingService(
            clusterSharding,
            actorSystem,
            kafkaTopicsConfig,
            objectMapper
        )
    }

    @Test
    fun `should process order successfully via OrderManagementActor`() {
        // Given
        val orderId = "test-order-1"
        val userId = "test-user-1"
        
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
        
        val expectedResponse = OrderManagementActor.OrderAccepted(orderId, Instant.now())
        
        // Mock the Ask pattern
        val completableFuture = CompletableFuture.completedFuture(expectedResponse)
        mockStatic<AskPattern>().use { askPattern ->
            askPattern.`when`<CompletableFuture<OrderManagementActor.Response>> {
                AskPattern.ask(
                    eq(orderManagementEntityRef),
                    any(),
                    any<Duration>(),
                    eq(scheduler)
                )
            }.thenReturn(completableFuture)

            // When
            val result = orderProcessingService.processOrder(orderRequest)

            // Then
            val response = result.get()
            assertTrue(response is OrderManagementActor.OrderAccepted)
            assertEquals(orderId, (response as OrderManagementActor.OrderAccepted).orderId)
            
            // Verify entity ref was obtained with correct orderId
            verify(clusterSharding).entityRefFor(any<EntityTypeKey<OrderManagementActor.Command>>(), eq(orderId))
        }
    }

    @Test
    fun `should handle order rejection from OrderManagementActor`() {
        // Given
        val orderId = "test-order-2"
        val userId = "test-user-2"
        
        val orderRequest = OrderRequestDTO(
            orderId = orderId,
            symbol = "TRUMP-2024",
            side = OrderSide.BUY,
            quantity = BigDecimal("100000"), // Large amount to trigger rejection
            price = BigDecimal("50.00"),
            orderType = OrderType.LIMIT,
            timeInForce = TimeInForce.GTC,
            userId = userId
        )
        
        val expectedResponse = OrderManagementActor.OrderRejected(orderId, "Exceeds risk limit")
        
        // Mock the Ask pattern
        val completableFuture = CompletableFuture.completedFuture(expectedResponse)
        mockStatic<AskPattern>().use { askPattern ->
            askPattern.`when`<CompletableFuture<OrderManagementActor.Response>> {
                AskPattern.ask(
                    eq(orderManagementEntityRef),
                    any(),
                    any<Duration>(),
                    eq(scheduler)
                )
            }.thenReturn(completableFuture)

            // When
            val result = orderProcessingService.processOrder(orderRequest)

            // Then
            val response = result.get()
            assertTrue(response is OrderManagementActor.OrderRejected)
            assertEquals(orderId, (response as OrderManagementActor.OrderRejected).orderId)
            assertTrue(response.reason.contains("risk limit"))
        }
    }

    @Test
    fun `should handle order from Kafka successfully`() {
        // Given
        val orderId = "test-order-3"
        val orderMessage = mapOf(
            "orderId" to orderId,
            "userId" to "test-user-3",
            "symbol" to "TRUMP-2024",
            "side" to "BUY",
            "quantity" to 100,
            "price" to 50.00,
            "orderType" to "LIMIT",
            "timeInForce" to "GTC"
        )
        
        val messageJson = """{"orderId":"$orderId","userId":"test-user-3","symbol":"TRUMP-2024","side":"BUY","quantity":100,"price":50.00,"orderType":"LIMIT","timeInForce":"GTC"}"""
        
        whenever(objectMapper.readValue(eq(messageJson), eq(Map::class.java)))
            .thenReturn(orderMessage)
        
        val expectedResponse = EMSActor.OrderProcessingStarted(orderId)
        val completableFuture = CompletableFuture.completedFuture(expectedResponse)
        
        mockStatic<AskPattern>().use { askPattern ->
            askPattern.`when`<CompletableFuture<EMSActor.Response>> {
                AskPattern.ask(
                    eq(emsActorEntityRef),
                    any(),
                    any<Duration>(),
                    eq(scheduler)
                )
            }.thenReturn(completableFuture)

            // When
            orderProcessingService.handleOrderFromKafka(messageJson)

            // Then
            verify(objectMapper).readValue(eq(messageJson), eq(Map::class.java))
            verify(clusterSharding).entityRefFor(any<EntityTypeKey<EMSActor.Command>>(), eq(orderId))
        }
    }

    @Test
    fun `should handle invalid JSON in Kafka message`() {
        // Given
        val invalidJson = "invalid json"
        
        whenever(objectMapper.readValue(eq(invalidJson), eq(Map::class.java)))
            .thenThrow(RuntimeException("Invalid JSON"))

        // When - should not throw exception
        assertDoesNotThrow {
            orderProcessingService.handleOrderFromKafka(invalidJson)
        }

        // Then
        verify(objectMapper).readValue(eq(invalidJson), eq(Map::class.java))
        // Should not interact with cluster sharding for invalid messages
        verify(clusterSharding, never()).entityRefFor(any<EntityTypeKey<EMSActor.Command>>(), any())
    }

    @Test
    fun `should handle execution report from Kafka successfully`() {
        // Given
        val orderId = "test-order-4"
        val executionMessage = mapOf(
            "orderId" to orderId,
            "executionId" to "exec-123",
            "status" to "FILLED",
            "filledQuantity" to 100,
            "remainingQuantity" to 0,
            "avgPrice" to 50.00
        )
        
        val messageJson = """{"orderId":"$orderId","executionId":"exec-123","status":"FILLED","filledQuantity":100,"remainingQuantity":0,"avgPrice":50.00}"""
        
        whenever(objectMapper.readValue(eq(messageJson), eq(Map::class.java)))
            .thenReturn(executionMessage)

        // When - should not throw exception
        assertDoesNotThrow {
            orderProcessingService.handleExecutionReport(messageJson)
        }

        // Then
        verify(objectMapper).readValue(eq(messageJson), eq(Map::class.java))
    }

    @Test
    fun `should handle invalid execution report JSON`() {
        // Given
        val invalidJson = "invalid execution json"
        
        whenever(objectMapper.readValue(eq(invalidJson), eq(Map::class.java)))
            .thenThrow(RuntimeException("Invalid JSON"))

        // When - should not throw exception
        assertDoesNotThrow {
            orderProcessingService.handleExecutionReport(invalidJson)
        }

        // Then
        verify(objectMapper).readValue(eq(invalidJson), eq(Map::class.java))
    }

    @Test
    fun `should handle timeout from OrderManagementActor`() {
        // Given
        val orderId = "test-order-5"
        val userId = "test-user-5"
        
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
        
        // Mock timeout
        val timeoutFuture = CompletableFuture<OrderManagementActor.Response>()
        timeoutFuture.completeExceptionally(RuntimeException("Timeout"))
        
        mockStatic<AskPattern>().use { askPattern ->
            askPattern.`when`<CompletableFuture<OrderManagementActor.Response>> {
                AskPattern.ask(
                    eq(orderManagementEntityRef),
                    any(),
                    any<Duration>(),
                    eq(scheduler)
                )
            }.thenReturn(timeoutFuture)

            // When/Then
            val result = orderProcessingService.processOrder(orderRequest)
            
            assertThrows(RuntimeException::class.java) {
                result.get()
            }
        }
    }

    @Test
    fun `should handle actor system failure gracefully`() {
        // Given
        val orderId = "test-order-6"
        val orderMessage = mapOf(
            "orderId" to orderId,
            "userId" to "test-user-6",
            "symbol" to "TRUMP-2024",
            "side" to "BUY",
            "quantity" to 100,
            "price" to 50.00,
            "orderType" to "LIMIT",
            "timeInForce" to "GTC"
        )
        
        val messageJson = """{"orderId":"$orderId","userId":"test-user-6","symbol":"TRUMP-2024","side":"BUY","quantity":100,"price":50.00,"orderType":"LIMIT","timeInForce":"GTC"}"""
        
        whenever(objectMapper.readValue(eq(messageJson), eq(Map::class.java)))
            .thenReturn(orderMessage)
        
        // Mock actor system failure
        whenever(clusterSharding.entityRefFor(any<EntityTypeKey<EMSActor.Command>>(), any()))
            .thenThrow(RuntimeException("Actor system failure"))

        // When - should not throw exception (should be handled gracefully)
        assertDoesNotThrow {
            orderProcessingService.handleOrderFromKafka(messageJson)
        }

        // Then
        verify(clusterSharding).entityRefFor(any<EntityTypeKey<EMSActor.Command>>(), eq(orderId))
    }
}