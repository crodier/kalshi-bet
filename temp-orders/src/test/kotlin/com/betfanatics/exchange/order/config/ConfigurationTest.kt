package com.betfanatics.exchange.order.config

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext
import org.apache.pekko.cluster.sharding.typed.javadsl.ClusterSharding
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityTypeKey
import org.apache.pekko.actor.typed.ActorSystem
import com.betfanatics.exchange.order.actor.OrderManagementActor
import com.betfanatics.exchange.order.actor.EMSActor
import com.betfanatics.exchange.order.messaging.KafkaSender
import com.betfanatics.exchange.order.service.OrderProcessingService
import java.math.BigDecimal

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = [
    "exchange.order.risk-limits.max-bet-amount=50000.00",
    "exchange.order.risk-limits.max-user-total-risk=500000.00",
    "exchange.order.kafka-config.topics.fix-order-topic.name=TEST-ORDER-TOPIC",
    "exchange.order.kafka-config.topics.fix-execution-topic.name=TEST-EXECUTION-TOPIC",
    "spring.kafka.bootstrap-servers=localhost:9092",
    "spring.datasource.url=jdbc:h2:mem:testdb",
    "spring.r2dbc.url=r2dbc:h2:mem:///testdb",
    "spring.flyway.enabled=false",
    "quickfixj.client.enabled=false"
])
class ConfigurationTest {

    @Autowired
    private lateinit var applicationContext: ApplicationContext

    @Autowired
    private lateinit var riskLimitsConfig: RiskLimitsConfig

    @Autowired
    private lateinit var kafkaTopicsConfig: KafkaTopicsConfig

    @Autowired
    private lateinit var clusterSharding: ClusterSharding

    @Autowired
    private lateinit var actorSystem: ActorSystem<Void>

    @Autowired
    private lateinit var kafkaSender: KafkaSender

    @Autowired
    private lateinit var orderProcessingService: OrderProcessingService

    @Test
    fun `should load application context successfully`() {
        assertNotNull(applicationContext)
        assertTrue(applicationContext.isActive)
    }

    @Test
    fun `should configure risk limits correctly`() {
        assertNotNull(riskLimitsConfig)
        assertEquals(BigDecimal("50000.00"), riskLimitsConfig.maxBetAmount)
        assertEquals(BigDecimal("500000.00"), riskLimitsConfig.maxUserTotalRisk)
    }

    @Test
    fun `should configure Kafka topics correctly`() {
        assertNotNull(kafkaTopicsConfig)
        assertEquals("TEST-ORDER-TOPIC", kafkaTopicsConfig.fixOrderTopic.name)
        assertEquals("TEST-EXECUTION-TOPIC", kafkaTopicsConfig.fixExecutionTopic.name)
    }

    @Test
    fun `should create actor system components`() {
        assertNotNull(clusterSharding)
        assertNotNull(actorSystem)
    }

    @Test
    fun `should create Kafka sender`() {
        assertNotNull(kafkaSender)
    }

    @Test
    fun `should create order processing service`() {
        assertNotNull(orderProcessingService)
    }

    @Test
    fun `should register OrderManagementActor type key`() {
        val typeKey = applicationContext.getBean(
            "orderManagementActorTypeKey", 
            EntityTypeKey::class.java
        ) as EntityTypeKey<OrderManagementActor.Command>
        
        assertNotNull(typeKey)
        assertEquals("OrderManagementActor", typeKey.name)
    }

    @Test
    fun `should register EMSActor type key`() {
        val typeKey = applicationContext.getBean(
            "emsActorTypeKey", 
            EntityTypeKey::class.java
        ) as EntityTypeKey<EMSActor.Command>
        
        assertNotNull(typeKey)
        assertEquals("EMSActor", typeKey.name)
    }

    @Test
    fun `should have required beans for order processing`() {
        // Verify all required beans are present
        assertTrue(applicationContext.containsBean("riskLimitsConfig"))
        assertTrue(applicationContext.containsBean("kafkaTopicsConfig"))
        assertTrue(applicationContext.containsBean("kafkaSender"))
        assertTrue(applicationContext.containsBean("orderProcessingService"))
        assertTrue(applicationContext.containsBean("orderManagementActorTypeKey"))
        assertTrue(applicationContext.containsBean("emsActorTypeKey"))
    }

    @Test
    fun `should configure Jackson ObjectMapper correctly`() {
        val objectMapper = applicationContext.getBean("jacksonObjectMapper", com.fasterxml.jackson.databind.ObjectMapper::class.java)
        assertNotNull(objectMapper)
        
        // Test that it can serialize/deserialize our data classes
        val testData = mapOf(
            "orderId" to "test-123",
            "quantity" to BigDecimal("100.50"),
            "price" to BigDecimal("75.25")
        )
        
        val json = objectMapper.writeValueAsString(testData)
        assertNotNull(json)
        assertTrue(json.contains("test-123"))
        
        @Suppress("UNCHECKED_CAST")
        val parsed = objectMapper.readValue(json, Map::class.java) as Map<String, Any>
        assertEquals("test-123", parsed["orderId"])
    }

    @Test
    fun `should have proper Spring Security configuration`() {
        // Verify security beans are configured
        assertTrue(applicationContext.containsBean("securityFilterChain"))
    }

    @Test
    fun `should have Kafka configuration`() {
        // Verify Kafka beans are configured
        assertTrue(applicationContext.containsBean("kafkaTemplate"))
        assertTrue(applicationContext.containsBean("kafkaProducerFactory"))
    }

    @Test
    fun `should have database configuration`() {
        // Verify database beans are configured for testing
        assertTrue(applicationContext.containsBean("dataSource"))
    }

    @Test
    fun `should validate configuration properties`() {
        // Test that configuration properties are validated correctly
        assertTrue(riskLimitsConfig.maxBetAmount > BigDecimal.ZERO)
        assertTrue(riskLimitsConfig.maxUserTotalRisk > BigDecimal.ZERO)
        assertTrue(riskLimitsConfig.maxUserTotalRisk > riskLimitsConfig.maxBetAmount)
        
        assertFalse(kafkaTopicsConfig.fixOrderTopic.name.isBlank())
        assertFalse(kafkaTopicsConfig.fixExecutionTopic.name.isBlank())
        assertNotEquals(kafkaTopicsConfig.fixOrderTopic.name, kafkaTopicsConfig.fixExecutionTopic.name)
    }
}