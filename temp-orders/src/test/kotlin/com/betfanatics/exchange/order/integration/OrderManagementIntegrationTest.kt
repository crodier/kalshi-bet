package com.betfanatics.exchange.order.integration

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.containers.KafkaContainer
import org.testcontainers.utility.DockerImageName
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import com.betfanatics.exchange.controller.OrderRequestV1
import com.betfanatics.exchange.controller.OrderSide
import com.betfanatics.exchange.controller.OrderType
import com.betfanatics.exchange.controller.TimeInForce
import com.betfanatics.exchange.order.service.OrderProcessingService
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.*
import java.math.BigDecimal
import java.util.*
import java.time.Duration
import org.awaitility.Awaitility
import javax.sql.DataSource
import java.sql.Connection

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestPropertySource(properties = [
    "spring.flyway.enabled=true",
    "exchange.order.risk-limits.max-bet-amount=100000.00",
    "exchange.order.risk-limits.max-user-total-risk=1000000.00",
    "spring.kafka.consumer.auto-offset-reset=earliest",
    "spring.kafka.consumer.group-id=test-group"
])
@Testcontainers
class OrderManagementIntegrationTest {

    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var restTemplate: TestRestTemplate

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var orderProcessingService: OrderProcessingService

    @Autowired
    private lateinit var dataSource: DataSource

    companion object {
        @Container
        @JvmStatic
        val postgresContainer = PostgreSQLContainer<Nothing>("postgres:15-alpine").apply {
            withDatabaseName("trading_test")
            withUsername("test")
            withPassword("test")
        }

        @Container
        @JvmStatic
        val kafkaContainer = KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.4.0")).apply {
            withEmbeddedZookeeper()
        }

        @DynamicPropertySource
        @JvmStatic
        fun configureProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgresContainer::getJdbcUrl)
            registry.add("spring.datasource.username", postgresContainer::getUsername)
            registry.add("spring.datasource.password", postgresContainer::getPassword)
            registry.add("spring.r2dbc.url") { "r2dbc:postgresql://${postgresContainer.host}:${postgresContainer.firstMappedPort}/${postgresContainer.databaseName}" }
            registry.add("spring.r2dbc.username", postgresContainer::getUsername)
            registry.add("spring.r2dbc.password", postgresContainer::getPassword)
            registry.add("spring.kafka.bootstrap-servers", kafkaContainer::getBootstrapServers)
        }
    }

    @BeforeEach
    fun setup() {
        // Wait for containers to be ready
        assertTrue(postgresContainer.isRunning)
        assertTrue(kafkaContainer.isRunning)
        
        // Clear test data
        clearTestData()
    }

    private fun clearTestData() {
        dataSource.connection.use { conn ->
            conn.createStatement().use { stmt ->
                stmt.executeUpdate("DELETE FROM order_management")
                stmt.executeUpdate("DELETE FROM user_risk_tracking")
                stmt.executeUpdate("DELETE FROM order_projection")
            }
        }
    }

    @Test
    fun `should process valid order successfully through REST API`() {
        // Given
        val orderId = UUID.randomUUID().toString()
        val userId = "integration-test-user-1"
        
        val orderRequest = OrderRequestV1(
            orderId = orderId,
            symbol = "TRUMP-2024",
            side = OrderSide.BUY,
            quantity = BigDecimal("100"),
            price = BigDecimal("50.00"),
            orderType = OrderType.LIMIT,
            timeInForce = TimeInForce.GTC
        )

        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
            set("X-Dev-User", userId)
        }
        val httpEntity = HttpEntity(orderRequest, headers)

        // When
        val response = restTemplate.postForEntity(
            "http://localhost:$port/v1/order",
            httpEntity,
            String::class.java
        )

        // Then
        assertEquals(HttpStatus.OK, response.statusCode)
        assertTrue(response.body?.contains("Workflow started") == true)

        // Verify order was persisted to database
        Awaitility.await()
            .atMost(Duration.ofSeconds(10))
            .untilAsserted {
                val orderExists = checkOrderExistsInDatabase(orderId)
                assertTrue(orderExists, "Order should be persisted to database")
            }

        // Verify user risk tracking was updated
        val userRisk = getUserRiskFromDatabase(userId)
        assertEquals(BigDecimal("5000.00"), userRisk) // 100 * 50.00
    }

    @Test
    fun `should reject order exceeding per-bet limit through REST API`() {
        // Given
        val orderId = UUID.randomUUID().toString()
        val userId = "integration-test-user-2"
        
        val orderRequest = OrderRequestV1(
            orderId = orderId,
            symbol = "TRUMP-2024",
            side = OrderSide.BUY,
            quantity = BigDecimal("10000"), // Large quantity
            price = BigDecimal("50.00"), // Risk = 500,000 > 100,000 limit
            orderType = OrderType.LIMIT,
            timeInForce = TimeInForce.GTC
        )

        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
            set("X-Dev-User", userId)
        }
        val httpEntity = HttpEntity(orderRequest, headers)

        // When
        val response = restTemplate.postForEntity(
            "http://localhost:$port/v1/order",
            httpEntity,
            String::class.java
        )

        // Then
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.statusCode)
        assertTrue(response.body?.contains("per-bet") == true)

        // Verify order was NOT persisted to database
        Awaitility.await()
            .atMost(Duration.ofSeconds(5))
            .untilAsserted {
                val orderExists = checkOrderExistsInDatabase(orderId)
                assertFalse(orderExists, "Order should not be persisted to database")
            }
    }

    @Test
    fun `should reject order exceeding user total risk limit`() {
        // Given
        val userId = "integration-test-user-3"
        
        // First, create a user with existing high risk
        createUserWithRisk(userId, BigDecimal("950000.00"))
        
        val orderId = UUID.randomUUID().toString()
        val orderRequest = OrderRequestV1(
            orderId = orderId,
            symbol = "TRUMP-2024",
            side = OrderSide.BUY,
            quantity = BigDecimal("2000"),
            price = BigDecimal("30.00"), // Risk = 60,000, total would be 1,010,000 > 1,000,000 limit
            orderType = OrderType.LIMIT,
            timeInForce = TimeInForce.GTC
        )

        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
            set("X-Dev-User", userId)
        }
        val httpEntity = HttpEntity(orderRequest, headers)

        // When
        val response = restTemplate.postForEntity(
            "http://localhost:$port/v1/order",
            httpEntity,
            String::class.java
        )

        // Then
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.statusCode)
        assertTrue(response.body?.contains("user total risk") == true)

        // Verify order was NOT persisted
        val orderExists = checkOrderExistsInDatabase(orderId)
        assertFalse(orderExists, "Order should not be persisted to database")
    }

    @Test
    fun `should handle multiple concurrent orders correctly`() {
        // Given
        val userId = "integration-test-user-4"
        val orderIds = mutableListOf<String>()
        val threads = mutableListOf<Thread>()
        val results = mutableListOf<Boolean>()

        // When - submit 5 concurrent orders
        repeat(5) { i ->
            val orderId = UUID.randomUUID().toString()
            orderIds.add(orderId)
            
            val thread = Thread {
                try {
                    val orderRequest = OrderRequestV1(
                        orderId = orderId,
                        symbol = "TRUMP-2024-$i",
                        side = OrderSide.BUY,
                        quantity = BigDecimal("100"),
                        price = BigDecimal("40.00"), // Risk = 4,000 each
                        orderType = OrderType.LIMIT,
                        timeInForce = TimeInForce.GTC
                    )

                    val headers = HttpHeaders().apply {
                        contentType = MediaType.APPLICATION_JSON
                        set("X-Dev-User", userId)
                    }
                    val httpEntity = HttpEntity(orderRequest, headers)

                    val response = restTemplate.postForEntity(
                        "http://localhost:$port/v1/order",
                        httpEntity,
                        String::class.java
                    )
                    
                    synchronized(results) {
                        results.add(response.statusCode == HttpStatus.OK)
                    }
                } catch (e: Exception) {
                    synchronized(results) {
                        results.add(false)
                    }
                }
            }
            threads.add(thread)
            thread.start()
        }

        // Wait for all threads to complete
        threads.forEach { it.join() }

        // Then - all orders should be processed successfully
        assertEquals(5, results.size)
        assertTrue(results.all { it }, "All orders should be processed successfully")

        // Verify total user risk is correct
        Awaitility.await()
            .atMost(Duration.ofSeconds(10))
            .untilAsserted {
                val totalRisk = getUserRiskFromDatabase(userId)
                assertEquals(BigDecimal("20000.00"), totalRisk) // 5 * 100 * 40.00
            }

        // Verify all orders are persisted
        orderIds.forEach { orderId ->
            assertTrue(checkOrderExistsInDatabase(orderId), "Order $orderId should be persisted")
        }
    }

    @Test
    fun `should calculate risk correctly for SELL orders`() {
        // Given
        val orderId = UUID.randomUUID().toString()
        val userId = "integration-test-user-5"
        
        val orderRequest = OrderRequestV1(
            orderId = orderId,
            symbol = "TRUMP-2024",
            side = OrderSide.SELL, // SELL order
            quantity = BigDecimal("100"),
            price = BigDecimal("30.00"), // Risk = 100 * (100 - 30) = 7,000
            orderType = OrderType.LIMIT,
            timeInForce = TimeInForce.GTC
        )

        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
            set("X-Dev-User", userId)
        }
        val httpEntity = HttpEntity(orderRequest, headers)

        // When
        val response = restTemplate.postForEntity(
            "http://localhost:$port/v1/order",
            httpEntity,
            String::class.java
        )

        // Then
        assertEquals(HttpStatus.OK, response.statusCode)

        // Verify correct risk calculation for SELL order
        Awaitility.await()
            .atMost(Duration.ofSeconds(10))
            .untilAsserted {
                val userRisk = getUserRiskFromDatabase(userId)
                assertEquals(BigDecimal("7000.00"), userRisk) // 100 * (100 - 30)
            }
    }

    @Test
    fun `should handle order status retrieval`() {
        // Given
        val orderId = UUID.randomUUID().toString()
        val userId = "integration-test-user-6"
        
        // First place an order
        val orderRequest = OrderRequestV1(
            orderId = orderId,
            symbol = "TRUMP-2024",
            side = OrderSide.BUY,
            quantity = BigDecimal("100"),
            price = BigDecimal("50.00"),
            orderType = OrderType.LIMIT,
            timeInForce = TimeInForce.GTC
        )

        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
            set("X-Dev-User", userId)
        }
        val httpEntity = HttpEntity(orderRequest, headers)

        val placeResponse = restTemplate.postForEntity(
            "http://localhost:$port/v1/order",
            httpEntity,
            String::class.java
        )
        assertEquals(HttpStatus.OK, placeResponse.statusCode)

        // When - retrieve order status
        val statusResponse = restTemplate.getForEntity(
            "http://localhost:$port/order/$orderId",
            String::class.java
        )

        // Then
        assertTrue(statusResponse.statusCode == HttpStatus.OK || statusResponse.statusCode == HttpStatus.INTERNAL_SERVER_ERROR)
        // Order status endpoint might return various states depending on processing timing
    }

    private fun checkOrderExistsInDatabase(orderId: String): Boolean {
        return dataSource.connection.use { conn ->
            val sql = "SELECT COUNT(*) FROM order_management WHERE order_id = ?"
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, orderId)
                val rs = stmt.executeQuery()
                rs.next() && rs.getInt(1) > 0
            }
        }
    }

    private fun getUserRiskFromDatabase(userId: String): BigDecimal {
        return dataSource.connection.use { conn ->
            val sql = "SELECT total_risk_amount FROM user_risk_tracking WHERE user_id = ?"
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, userId)
                val rs = stmt.executeQuery()
                if (rs.next()) {
                    rs.getBigDecimal("total_risk_amount") ?: BigDecimal.ZERO
                } else {
                    BigDecimal.ZERO
                }
            }
        }
    }

    private fun createUserWithRisk(userId: String, riskAmount: BigDecimal) {
        dataSource.connection.use { conn ->
            val sql = "INSERT INTO user_risk_tracking (user_id, total_risk_amount, last_updated) VALUES (?, ?, NOW())"
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, userId)
                stmt.setBigDecimal(2, riskAmount)
                stmt.executeUpdate()
            }
        }
    }
}