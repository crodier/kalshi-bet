package com.betfanatics.exchange.order.integration

import com.betfanatics.exchange.order.actor.common.*
import com.betfanatics.exchange.order.service.ClOrdIdMappingService
import com.betfanatics.exchange.order.service.ExecutionReportEnrichmentService
import com.betfanatics.exchange.order.util.FixClOrdIdGenerator
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.ValueOperations
import quickfix.field.*
import quickfix.fix50sp2.*
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@ExtendWith(MockitoExtension::class)
class FixOrderFlowIntegrationTest {

    @Mock
    private lateinit var redisTemplate: RedisTemplate<String, String>

    @Mock
    private lateinit var valueOperations: ValueOperations<String, String>

    private lateinit var fixClOrdIdGenerator: FixClOrdIdGenerator
    private lateinit var objectMapper: ObjectMapper
    private lateinit var clOrdIdMappingService: ClOrdIdMappingService
    
    // FIX timestamp formatter matching the example: 20250623-16:29:15.106
    private val fixTimestampFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HH:mm:ss.SSS")
        .withZone(ZoneOffset.UTC)

    @BeforeEach
    fun setup() {
        fixClOrdIdGenerator = FixClOrdIdGenerator()
        objectMapper = ObjectMapper()
        clOrdIdMappingService = ClOrdIdMappingService(redisTemplate, fixClOrdIdGenerator, objectMapper)
        
        `when`(redisTemplate.opsForValue()).thenReturn(valueOperations)
    }

    @Test
    fun `test complete order flow from REST to FIX and back`() {
        // Step 1: Incoming REST order request
        val betOrderId = "f61f15b1-f1ad-47f3-8643-27007f9e3eaf"
        val orderRequest = OrderRequestDTO(
            orderId = betOrderId,
            symbol = "KXETHD-25JUN2311-T1509.99",
            side = OrderSide.BUY,
            quantity = BigDecimal("2"),
            price = BigDecimal("49"),
            orderType = OrderType.LIMIT,
            timeInForce = TimeInForce.GTC,
            userId = "test-user-2"
        )
        
        // Step 2: Generate and store ClOrdID
        `when`(valueOperations.get("fix-orders/orderId/$betOrderId")).thenReturn(null)
        `when`(redisTemplate.executePipelined(any())).thenReturn(listOf())
        
        val clOrdId = clOrdIdMappingService.generateAndStoreClOrdId(betOrderId, orderRequest)
        assertEquals("FBG_f61f15b1_f1ad_47f3_8643_27007f9e3eaf", clOrdId)
        
        // Step 3: Generate NewOrderSingle FIX message
        val newOrder = NewOrderSingle()
        newOrder.set(ClOrdID(clOrdId))                           // Tag 11
        newOrder.set(OrderQty(orderRequest.quantity.toDouble())) // Tag 38 = 2
        newOrder.set(OrdType(OrdType.LIMIT))                     // Tag 40 = 2
        newOrder.set(Price(orderRequest.price!!.toDouble()))     // Tag 44 = 49
        newOrder.set(Side(Side.BUY))                             // Tag 54 = 1
        newOrder.set(Symbol(orderRequest.symbol))                // Tag 55
        newOrder.set(TimeInForce(TimeInForce.GOOD_TILL_CANCEL)) // Tag 59 = 1
        newOrder.set(TransactTime())                             // Tag 60
        
        // Add party information
        newOrder.setInt(NoPartyIDs.FIELD, 1)                    // Tag 453 = 1
        val partyGroup = NewOrderSingle.NoPartyIDs()
        partyGroup.set(PartyID("b0f5a944-8dbd-46ad-a48b-f82e29f57599_${orderRequest.userId}"))
        partyGroup.set(PartyRole(24))
        newOrder.addGroup(partyGroup)
        
        // Verify the generated message matches expected format
        val fixString = newOrder.toString()
        println("Generated NewOrderSingle: ${fixString.replace('\u0001', '|')}")
        
        // Step 4: Simulate receiving ExecutionReport for order acceptance
        val execReport = ExecutionReport()
        execReport.set(ClOrdID(clOrdId))
        execReport.set(OrderID("kalshi-order-12345"))
        execReport.set(ExecID("exec-001"))
        execReport.set(ExecType(ExecType.NEW))
        execReport.set(OrdStatus(OrdStatus.NEW))
        execReport.set(Symbol(orderRequest.symbol))
        execReport.set(Side(Side.BUY))
        execReport.set(OrderQty(2.0))
        execReport.set(Price(49.0))
        execReport.set(CumQty(0.0))
        execReport.set(LeavesQty(2.0))
        execReport.set(TransactTime())
        
        // Verify we can extract betOrderId from ClOrdID
        val extractedBetOrderId = fixClOrdIdGenerator.extractOrderId(clOrdId)
        assertEquals(betOrderId, extractedBetOrderId)
        
        // Step 5: Simulate partial fill
        val partialFillReport = ExecutionReport()
        partialFillReport.set(ClOrdID(clOrdId))
        partialFillReport.set(OrderID("kalshi-order-12345"))
        partialFillReport.set(ExecID("exec-002"))
        partialFillReport.set(ExecType(ExecType.PARTIAL_FILL))
        partialFillReport.set(OrdStatus(OrdStatus.PARTIALLY_FILLED))
        partialFillReport.set(LastQty(1.0))
        partialFillReport.set(LastPx(49.0))
        partialFillReport.set(CumQty(1.0))
        partialFillReport.set(LeavesQty(1.0))
        partialFillReport.set(AvgPx(49.0))
        
        // Step 6: Simulate order modification
        val modifyPair = clOrdIdMappingService.generateAndStoreModifyClOrdId(betOrderId)
        val modifyClOrdId = modifyPair.first
        val origClOrdIdForModify = modifyPair.second
        
        assertTrue(modifyClOrdId.contains("_M_"))
        assertEquals(clOrdId, origClOrdIdForModify) // Should reference original order
        
        // Step 7: Simulate modify acceptance
        val modifyAcceptReport = ExecutionReport()
        modifyAcceptReport.set(ClOrdID(modifyClOrdId))
        modifyAcceptReport.set(OrderID("kalshi-order-12345"))
        modifyAcceptReport.set(ExecID("exec-003"))
        modifyAcceptReport.set(ExecType(ExecType.REPLACED))
        modifyAcceptReport.set(OrdStatus(OrdStatus.REPLACED))
        
        // Update latest accepted modify
        clOrdIdMappingService.updateLatestAcceptedModifyClOrdId(betOrderId, modifyClOrdId)
        
        // Step 8: Simulate order cancellation
        `when`(valueOperations.get("fix-orders/latestModifyAccepted/$betOrderId")).thenReturn(modifyClOrdId)
        `when`(valueOperations.get("fix-orders/orderId/$betOrderId")).thenReturn(clOrdId)
        
        val cancelPair = clOrdIdMappingService.generateAndStoreCancelClOrdId(betOrderId)
        val cancelClOrdId = cancelPair.first
        val origClOrdIdForCancel = cancelPair.second
        
        assertTrue(cancelClOrdId.contains("_C_"))
        assertEquals(modifyClOrdId, origClOrdIdForCancel) // Should reference the accepted modify
        
        // Generate OrderCancelRequest
        val cancelRequest = OrderCancelRequest(
            OrigClOrdID(origClOrdIdForCancel),
            ClOrdID(cancelClOrdId),
            Side(Side.BUY),
            TransactTime()
        )
        cancelRequest.set(OrderID("kalshi-order-12345"))
        
        // Verify cancel request structure
        assertEquals(cancelClOrdId, cancelRequest.getString(ClOrdID.FIELD))
        assertEquals(modifyClOrdId, cancelRequest.getString(OrigClOrdID.FIELD))
        
        // Step 9: Simulate cancel acceptance
        val cancelAcceptReport = ExecutionReport()
        cancelAcceptReport.set(ClOrdID(cancelClOrdId))
        cancelAcceptReport.set(OrderID("kalshi-order-12345"))
        cancelAcceptReport.set(ExecID("exec-004"))
        cancelAcceptReport.set(ExecType(ExecType.CANCELED))
        cancelAcceptReport.set(OrdStatus(OrdStatus.CANCELED))
        cancelAcceptReport.set(CumQty(1.0))
        cancelAcceptReport.set(LeavesQty(0.0))
        
        // Verify we can extract betOrderId from all ClOrdID formats
        assertEquals(betOrderId, fixClOrdIdGenerator.extractOrderId(clOrdId))
        assertEquals(betOrderId, fixClOrdIdGenerator.extractOrderId(modifyClOrdId))
        assertEquals(betOrderId, fixClOrdIdGenerator.extractOrderId(cancelClOrdId))
    }

    @Test
    fun `test timestamp format matches FIX specification`() {
        // Test that our timestamp generation matches the required format
        val now = Instant.now()
        val formatted = fixTimestampFormatter.format(now)
        
        // Should match format: 20250623-16:29:15.106
        assertTrue(formatted.matches(Regex("\\d{8}-\\d{2}:\\d{2}:\\d{2}\\.\\d{3}")))
        
        // Verify with TransactTime
        val order = NewOrderSingle()
        order.set(TransactTime())
        val transactTime = order.getString(TransactTime.FIELD)
        
        assertTrue(transactTime.matches(Regex("\\d{8}-\\d{2}:\\d{2}:\\d{2}\\.\\d{3}")))
    }
}