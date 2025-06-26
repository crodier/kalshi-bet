package com.betfanatics.exchange.order.service

import com.betfanatics.exchange.order.actor.common.OrderRequestDTO
import com.betfanatics.exchange.order.actor.common.OrderSide
import com.betfanatics.exchange.order.actor.common.OrderType
import com.betfanatics.exchange.order.actor.common.TimeInForce
import com.betfanatics.exchange.order.util.FixClOrdIdGenerator
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.jupiter.MockitoExtension
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.ValueOperations
import java.math.BigDecimal
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@ExtendWith(MockitoExtension::class)
class ClOrdIdMappingServiceTest {

    @Mock
    private lateinit var redisTemplate: RedisTemplate<String, String>

    @Mock
    private lateinit var valueOperations: ValueOperations<String, String>

    private lateinit var fixClOrdIdGenerator: FixClOrdIdGenerator
    private lateinit var objectMapper: ObjectMapper
    private lateinit var clOrdIdMappingService: ClOrdIdMappingService

    @BeforeEach
    fun setup() {
        fixClOrdIdGenerator = FixClOrdIdGenerator()
        objectMapper = ObjectMapper()
        clOrdIdMappingService = ClOrdIdMappingService(redisTemplate, fixClOrdIdGenerator, objectMapper)
        
        `when`(redisTemplate.opsForValue()).thenReturn(valueOperations)
    }

    @Test
    fun `test cancel order with new order ClOrdID in tag 41`() {
        // Given
        val betOrderId = "test-order-123"
        val newOrderClOrdId = "FBG_test_order_123"
        
        // Mock Redis responses
        `when`(valueOperations.get("fix-orders/orderId/$betOrderId")).thenReturn(newOrderClOrdId)
        `when`(valueOperations.get("fix-orders/latestModifyAccepted/$betOrderId")).thenReturn(null)
        `when`(redisTemplate.executePipelined(any())).thenReturn(listOf())
        
        // When
        val (cancelClOrdId, origClOrdId) = clOrdIdMappingService.generateAndStoreCancelClOrdId(betOrderId)
        
        // Then
        assertNotNull(cancelClOrdId)
        assert(cancelClOrdId.startsWith("FBG_test_order_123_C_"))
        assertEquals(newOrderClOrdId, origClOrdId, "Tag 41 should contain the original new order ClOrdID")
        
        // Verify Redis interactions
        verify(valueOperations).get("fix-orders/latestModifyAccepted/$betOrderId")
        verify(valueOperations).get("fix-orders/orderId/$betOrderId")
        verify(redisTemplate).executePipelined(any())
    }

    @Test
    fun `test cancel order with accepted modify ClOrdID in tag 41`() {
        // Given
        val betOrderId = "test-order-456"
        val newOrderClOrdId = "FBG_test_order_456"
        val modifyClOrdId = "FBG_test_order_456_M_1234567890"
        
        // Mock Redis responses - modify was accepted
        `when`(valueOperations.get("fix-orders/orderId/$betOrderId")).thenReturn(newOrderClOrdId)
        `when`(valueOperations.get("fix-orders/latestModifyAccepted/$betOrderId")).thenReturn(modifyClOrdId)
        `when`(redisTemplate.executePipelined(any())).thenReturn(listOf())
        
        // When
        val (cancelClOrdId, origClOrdId) = clOrdIdMappingService.generateAndStoreCancelClOrdId(betOrderId)
        
        // Then
        assertNotNull(cancelClOrdId)
        assert(cancelClOrdId.startsWith("FBG_test_order_456_C_"))
        assertEquals(modifyClOrdId, origClOrdId, "Tag 41 should contain the accepted modify ClOrdID")
        
        // Verify Redis interactions
        verify(valueOperations).get("fix-orders/latestModifyAccepted/$betOrderId")
        verify(redisTemplate).executePipelined(any())
    }

    @Test
    fun `test modify order acceptance updates latestModifyAccepted bucket`() {
        // Given
        val betOrderId = "test-order-789"
        val modifyClOrdId = "FBG_test_order_789_M_9876543210"
        
        // When
        clOrdIdMappingService.updateLatestAcceptedModifyClOrdId(betOrderId, modifyClOrdId)
        
        // Then
        verify(valueOperations).set(
            "fix-orders/latestModifyAccepted/$betOrderId", 
            modifyClOrdId, 
            Duration.ofDays(30)
        )
    }

    @Test
    fun `test generateAndStoreClOrdId for new order`() {
        // Given
        val betOrderId = "550e8400-e29b-41d4-a716-446655440000"
        val orderRequest = OrderRequestDTO(
            orderId = betOrderId,
            symbol = "INXD-23DEC31-B5748",
            side = OrderSide.BUY,
            quantity = BigDecimal("100"),
            price = BigDecimal("0.45"),
            orderType = OrderType.LIMIT,
            timeInForce = TimeInForce.GTC,
            userId = "test-user"
        )
        
        // Mock Redis - no existing order
        `when`(valueOperations.get("fix-orders/orderId/$betOrderId")).thenReturn(null)
        `when`(redisTemplate.executePipelined(any())).thenReturn(listOf())
        
        // When
        val clOrdId = clOrdIdMappingService.generateAndStoreClOrdId(betOrderId, orderRequest)
        
        // Then
        assertEquals("FBG_550e8400_e29b_41d4_a716_446655440000", clOrdId)
        verify(redisTemplate).executePipelined(any())
    }

    @Test
    fun `test modify order with new order ClOrdID as origClOrdId when no previous modify`() {
        // Given
        val betOrderId = "modify-test-123"
        val newOrderClOrdId = "FBG_modify_test_123"
        
        // Mock Redis responses
        `when`(valueOperations.get("fix-orders/orderId/$betOrderId")).thenReturn(newOrderClOrdId)
        `when`(valueOperations.get("fix-orders/latestModifyAccepted/$betOrderId")).thenReturn(null)
        `when`(redisTemplate.executePipelined(any())).thenReturn(listOf())
        
        // When
        val (modifyClOrdId, origClOrdId) = clOrdIdMappingService.generateAndStoreModifyClOrdId(betOrderId)
        
        // Then
        assertNotNull(modifyClOrdId)
        assert(modifyClOrdId.startsWith("FBG_modify_test_123_M_"))
        assertEquals(newOrderClOrdId, origClOrdId, "Tag 41 should contain the original new order ClOrdID")
    }

    @Test
    fun `test extractOrderId from various ClOrdID formats`() {
        // Test new order ClOrdID
        val betOrderId1 = fixClOrdIdGenerator.extractOrderId("FBG_550e8400_e29b_41d4_a716_446655440000")
        assertEquals("550e8400-e29b-41d4-a716-446655440000", betOrderId1)
        
        // Test modify ClOrdID
        val betOrderId2 = fixClOrdIdGenerator.extractOrderId("FBG_550e8400_e29b_41d4_a716_446655440000_M_1234567890")
        assertEquals("550e8400-e29b-41d4-a716-446655440000", betOrderId2)
        
        // Test cancel ClOrdID
        val betOrderId3 = fixClOrdIdGenerator.extractOrderId("FBG_550e8400_e29b_41d4_a716_446655440000_C_9876543210")
        assertEquals("550e8400-e29b-41d4-a716-446655440000", betOrderId3)
    }
}