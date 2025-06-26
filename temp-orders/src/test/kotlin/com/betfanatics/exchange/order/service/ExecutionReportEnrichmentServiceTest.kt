package com.betfanatics.exchange.order.service

import com.betfanatics.exchange.order.actor.common.OrderRequestDTO
import com.betfanatics.exchange.order.actor.common.OrderSide
import com.betfanatics.exchange.order.actor.common.OrderType
import com.betfanatics.exchange.order.actor.common.TimeInForce
import com.betfanatics.exchange.order.metrics.FixMessageMetrics
import com.betfanatics.exchange.order.util.FixClOrdIdGenerator
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.jupiter.MockitoExtension
import quickfix.Message
import quickfix.field.*
import quickfix.fix50sp2.ExecutionReport
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@ExtendWith(MockitoExtension::class)
class ExecutionReportEnrichmentServiceTest {

    @Mock
    private lateinit var clOrdIdMappingService: ClOrdIdMappingService

    @Mock
    private lateinit var fixErrorService: FixErrorService

    @Mock
    private lateinit var fixMessageMetrics: FixMessageMetrics

    private lateinit var fixClOrdIdGenerator: FixClOrdIdGenerator
    private lateinit var executionReportEnrichmentService: ExecutionReportEnrichmentService

    @BeforeEach
    fun setup() {
        fixClOrdIdGenerator = FixClOrdIdGenerator()
        executionReportEnrichmentService = ExecutionReportEnrichmentService(
            clOrdIdMappingService, 
            fixErrorService, 
            fixMessageMetrics,
            fixClOrdIdGenerator
        )
    }

    @Test
    fun `test execution report for accepted modify order updates latestModifyAccepted`() {
        // Given
        val betOrderId = "test-order-123"
        val modifyClOrdId = "FBG_test_order_123_M_1234567890"
        val orderData = OrderRequestDTO(
            orderId = betOrderId,
            symbol = "INXD-23DEC31-B5748",
            side = OrderSide.BUY,
            quantity = BigDecimal("100"),
            price = BigDecimal("0.50"),
            orderType = OrderType.LIMIT,
            timeInForce = TimeInForce.GTC,
            userId = "test-user"
        )
        
        // Create execution report for accepted modify (REPLACED)
        val executionReport = ExecutionReport()
        executionReport.set(ClOrdID(modifyClOrdId))
        executionReport.set(ExecID("exec-123"))
        executionReport.set(ExecType(ExecType.REPLACED)) // 5 = REPLACED
        executionReport.set(OrdStatus(OrdStatus.REPLACED)) // 5 = REPLACED
        executionReport.set(CumQty(0.0))
        executionReport.set(LeavesQty(100.0))
        executionReport.set(OrderID("kalshi-order-456"))
        executionReport.set(Symbol("INXD-23DEC31-B5748"))
        executionReport.set(Side(Side.BUY))
        executionReport.set(OrderQty(100.0))
        executionReport.set(Price(0.50))
        
        // Mock Redis responses
        `when`(clOrdIdMappingService.getOrderIdByClOrdId(modifyClOrdId)).thenReturn(betOrderId)
        `when`(clOrdIdMappingService.getOrderData(betOrderId)).thenReturn(orderData)
        
        // When
        val enrichedReport = executionReportEnrichmentService.enrichExecutionReport(executionReport)
        
        // Then
        assertNotNull(enrichedReport)
        assertEquals(betOrderId, enrichedReport.betOrderId)
        assertEquals(modifyClOrdId, enrichedReport.clOrdId)
        assertEquals("5", enrichedReport.execType) // REPLACED
        assertEquals("5", enrichedReport.ordStatus) // REPLACED
        assertNotNull(enrichedReport.betOrder)
        
        // Verify that latestModifyAccepted was updated
        verify(clOrdIdMappingService).updateLatestAcceptedModifyClOrdId(betOrderId, modifyClOrdId)
    }

    @Test
    fun `test execution report for new order does not update latestModifyAccepted`() {
        // Given
        val betOrderId = "test-order-456"
        val newOrderClOrdId = "FBG_test_order_456"
        
        // Create execution report for accepted new order
        val executionReport = ExecutionReport()
        executionReport.set(ClOrdID(newOrderClOrdId))
        executionReport.set(ExecID("exec-456"))
        executionReport.set(ExecType(ExecType.NEW)) // 0 = NEW
        executionReport.set(OrdStatus(OrdStatus.NEW)) // 0 = NEW
        executionReport.set(CumQty(0.0))
        executionReport.set(LeavesQty(100.0))
        
        // Mock Redis responses
        `when`(clOrdIdMappingService.getOrderIdByClOrdId(newOrderClOrdId)).thenReturn(betOrderId)
        `when`(clOrdIdMappingService.getOrderData(betOrderId)).thenReturn(null)
        
        // When
        val enrichedReport = executionReportEnrichmentService.enrichExecutionReport(executionReport)
        
        // Then
        assertNotNull(enrichedReport)
        assertEquals(betOrderId, enrichedReport.betOrderId)
        assertEquals("0", enrichedReport.execType) // NEW
        
        // Verify that latestModifyAccepted was NOT updated
        verify(clOrdIdMappingService, never()).updateLatestAcceptedModifyClOrdId(any(), any())
    }

    @Test
    fun `test execution report for rejected modify does not update latestModifyAccepted`() {
        // Given
        val betOrderId = "test-order-789"
        val modifyClOrdId = "FBG_test_order_789_M_9876543210"
        
        // Create execution report for rejected modify
        val executionReport = ExecutionReport()
        executionReport.set(ClOrdID(modifyClOrdId))
        executionReport.set(ExecID("exec-789"))
        executionReport.set(ExecType(ExecType.REJECTED)) // 8 = REJECTED
        executionReport.set(OrdStatus(OrdStatus.REJECTED)) // 8 = REJECTED
        executionReport.set(CumQty(0.0))
        executionReport.set(LeavesQty(0.0))
        
        // Mock Redis responses
        `when`(clOrdIdMappingService.getOrderIdByClOrdId(modifyClOrdId)).thenReturn(betOrderId)
        
        // When
        val enrichedReport = executionReportEnrichmentService.enrichExecutionReport(executionReport)
        
        // Then
        assertNotNull(enrichedReport)
        assertEquals("8", enrichedReport.execType) // REJECTED
        
        // Verify that latestModifyAccepted was NOT updated
        verify(clOrdIdMappingService, never()).updateLatestAcceptedModifyClOrdId(any(), any())
    }

    @Test
    fun `test betOrderId extraction from various ClOrdID formats`() {
        // Given test data for different ClOrdID formats
        val testCases = listOf(
            "FBG_test_order_123" to "test-order-123",                    // New order
            "FBG_test_order_456_M_1234567890" to "test-order-456",     // Modify order
            "FBG_test_order_789_C_9876543210" to "test-order-789"      // Cancel order
        )
        
        for ((clOrdId, expectedBetOrderId) in testCases) {
            // Create execution report
            val executionReport = ExecutionReport()
            executionReport.set(ClOrdID(clOrdId))
            executionReport.set(ExecID("exec-test"))
            executionReport.set(ExecType(ExecType.NEW))
            executionReport.set(OrdStatus(OrdStatus.NEW))
            executionReport.set(CumQty(0.0))
            executionReport.set(LeavesQty(100.0))
            
            // Mock Redis response using the generator to extract betOrderId
            val extractedBetOrderId = fixClOrdIdGenerator.extractOrderId(clOrdId)
            `when`(clOrdIdMappingService.getOrderIdByClOrdId(clOrdId)).thenReturn(extractedBetOrderId)
            
            // When
            val enrichedReport = executionReportEnrichmentService.enrichExecutionReport(executionReport)
            
            // Then
            assertNotNull(enrichedReport)
            assertEquals(expectedBetOrderId, enrichedReport.betOrderId, 
                "Failed to extract correct betOrderId from ClOrdID: $clOrdId")
        }
    }

    @Test
    fun `test execution report with missing ClOrdID mapping`() {
        // Given
        val unknownClOrdId = "FBG_unknown_order_999"
        
        // Create execution report
        val executionReport = ExecutionReport()
        executionReport.set(ClOrdID(unknownClOrdId))
        executionReport.set(ExecID("exec-999"))
        executionReport.set(ExecType(ExecType.NEW))
        executionReport.set(OrdStatus(OrdStatus.NEW))
        executionReport.set(CumQty(0.0))
        executionReport.set(LeavesQty(100.0))
        
        // Mock Redis response - no mapping found
        `when`(clOrdIdMappingService.getOrderIdByClOrdId(unknownClOrdId)).thenReturn(null)
        
        // When
        val enrichedReport = executionReportEnrichmentService.enrichExecutionReport(executionReport)
        
        // Then
        assertNotNull(enrichedReport)
        assertEquals("UNKNOWN", enrichedReport.betOrderId)
        
        // Verify error reporting
        verify(fixErrorService).reportGeneralError(
            isNull(),
            eq("No betOrderId mapping found for ClOrdID: $unknownClOrdId"),
            any()
        )
        verify(fixMessageMetrics).incrementFixError("missing_clordid_mapping")
    }
    
    @Test
    fun `test execution report for accepted cancel order updates latestCancelAccepted`() {
        // Given
        val betOrderId = "test-order-999"
        val cancelClOrdId = "FBG_test_order_999_C_1234567890"
        val orderData = OrderRequestDTO(
            orderId = betOrderId,
            symbol = "INXD-23DEC31-B5748",
            side = OrderSide.SELL,
            quantity = BigDecimal("50"),
            price = BigDecimal("0.75"),
            orderType = OrderType.LIMIT,
            timeInForce = TimeInForce.GTC,
            userId = "test-user"
        )
        
        // Create execution report for accepted cancel (CANCELED)
        val executionReport = ExecutionReport()
        executionReport.set(ClOrdID(cancelClOrdId))
        executionReport.set(ExecID("exec-cancel-123"))
        executionReport.set(ExecType(ExecType.CANCELED)) // 4 = CANCELED
        executionReport.set(OrdStatus(OrdStatus.CANCELED)) // 4 = CANCELED
        executionReport.set(CumQty(0.0))
        executionReport.set(LeavesQty(0.0))
        executionReport.set(OrderID("kalshi-order-999"))
        executionReport.set(Symbol("INXD-23DEC31-B5748"))
        executionReport.set(Side(Side.SELL))
        
        // Mock Redis responses
        `when`(clOrdIdMappingService.getOrderData(betOrderId)).thenReturn(orderData)
        
        // When
        val enrichedReport = executionReportEnrichmentService.enrichExecutionReport(executionReport)
        
        // Then
        assertNotNull(enrichedReport)
        assertEquals(betOrderId, enrichedReport.betOrderId)
        assertEquals(cancelClOrdId, enrichedReport.clOrdId)
        assertEquals("4", enrichedReport.execType) // CANCELED
        assertEquals("4", enrichedReport.ordStatus) // CANCELED
        assertNotNull(enrichedReport.betOrder)
        
        // Verify that latestCancelAccepted was updated
        verify(clOrdIdMappingService).updateLatestAcceptedCancelClOrdId(betOrderId, cancelClOrdId)
    }
    
    @Test
    fun `test execution report for rejected cancel does not update latestCancelAccepted`() {
        // Given
        val betOrderId = "test-order-888"
        val cancelClOrdId = "FBG_test_order_888_C_9876543210"
        
        // Create execution report for rejected cancel
        val executionReport = ExecutionReport()
        executionReport.set(ClOrdID(cancelClOrdId))
        executionReport.set(ExecID("exec-888"))
        executionReport.set(ExecType(ExecType.REJECTED)) // 8 = REJECTED
        executionReport.set(OrdStatus(OrdStatus.REJECTED)) // 8 = REJECTED
        executionReport.set(CumQty(0.0))
        executionReport.set(LeavesQty(0.0))
        
        // Mock Redis responses
        `when`(clOrdIdMappingService.getOrderData(betOrderId)).thenReturn(null)
        
        // When
        val enrichedReport = executionReportEnrichmentService.enrichExecutionReport(executionReport)
        
        // Then
        assertNotNull(enrichedReport)
        assertEquals("8", enrichedReport.execType) // REJECTED
        
        // Verify that latestCancelAccepted was NOT updated
        verify(clOrdIdMappingService, never()).updateLatestAcceptedCancelClOrdId(any(), any())
    }
}