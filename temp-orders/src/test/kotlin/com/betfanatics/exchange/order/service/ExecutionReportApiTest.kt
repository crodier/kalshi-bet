package com.betfanatics.exchange.order.service

import com.betfanatics.exchange.order.actor.common.*
import com.betfanatics.exchange.order.metrics.FixMessageMetrics
import com.betfanatics.exchange.order.util.FixClOrdIdGenerator
import com.fbg.api.fix.domain.ExecutionReport
import com.fbg.api.fix.domain.IncomingOrder
import com.fbg.api.fix.enums.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.jupiter.MockitoExtension
import quickfix.field.*
import quickfix.fix50sp2.ExecutionReport as FixExecutionReport
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@ExtendWith(MockitoExtension::class)
class ExecutionReportApiTest {

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
    fun `test ExecutionReport contains betOrderId and orderId`() {
        // Given
        val betOrderId = "test-order-123"
        val newOrderClOrdId = "FBG_test_order_123"
        val exchangeOrderId = "kalshi-order-456"
        
        val orderData = OrderRequestDTO(
            orderId = betOrderId,
            symbol = "KXETHD-25JUN2311-T1509.99",
            side = OrderSide.BUY,
            quantity = BigDecimal("2"),
            price = BigDecimal("49"),
            orderType = OrderType.LIMIT,
            timeInForce = TimeInForce.GTC,
            userId = "test-user"
        )
        
        // Create FIX execution report
        val fixExecReport = FixExecutionReport()
        fixExecReport.set(ClOrdID(newOrderClOrdId))
        fixExecReport.set(OrderID(exchangeOrderId))
        fixExecReport.set(ExecID("exec-001"))
        fixExecReport.set(ExecType(quickfix.field.ExecType.NEW))
        fixExecReport.set(OrdStatus(quickfix.field.OrdStatus.NEW))
        fixExecReport.set(Symbol("KXETHD-25JUN2311-T1509.99"))
        fixExecReport.set(Side(quickfix.field.Side.BUY))
        fixExecReport.set(OrderQty(2.0))
        fixExecReport.set(Price(49.0))
        fixExecReport.set(CumQty(0.0))
        fixExecReport.set(LeavesQty(2.0))
        fixExecReport.set(TransactTime())
        
        // Mock responses
        `when`(clOrdIdMappingService.getOrderData(betOrderId)).thenReturn(orderData)
        
        // When
        val enrichedReport = executionReportEnrichmentService.enrichExecutionReport(fixExecReport)
        
        // Then
        assertNotNull(enrichedReport)
        assertEquals(betOrderId, enrichedReport.betOrderId, "Should contain our internal betOrderId")
        assertEquals(exchangeOrderId, enrichedReport.orderID, "Should contain exchange's OrderID")
        assertEquals(newOrderClOrdId, enrichedReport.clOrdID, "Should contain ClOrdID")
        
        // Should have newOrder populated, others null
        assertNotNull(enrichedReport.newOrder, "Should have newOrder populated")
        assertNull(enrichedReport.modifyOrder, "Should not have modifyOrder")
        assertNull(enrichedReport.cancelOrder, "Should not have cancelOrder")
    }

    @Test
    fun `test ExecutionReport for NewOrder contains complete order data`() {
        // Given
        val betOrderId = "new-order-789"
        val newOrderClOrdId = "FBG_new_order_789"
        
        val orderData = OrderRequestDTO(
            orderId = betOrderId,
            symbol = "KXETHD-25JUN2311-T1509.99",
            side = OrderSide.SELL,
            quantity = BigDecimal("5"),
            price = BigDecimal("52.5"),
            orderType = OrderType.LIMIT,
            timeInForce = TimeInForce.IOC,
            userId = "test-user-2"
        )
        
        // Create FIX execution report
        val fixExecReport = FixExecutionReport()
        fixExecReport.set(ClOrdID(newOrderClOrdId))
        fixExecReport.set(OrderID("exchange-789"))
        fixExecReport.set(ExecID("exec-new-001"))
        fixExecReport.set(ExecType(quickfix.field.ExecType.NEW))
        fixExecReport.set(OrdStatus(quickfix.field.OrdStatus.NEW))
        fixExecReport.set(Symbol("KXETHD-25JUN2311-T1509.99"))
        fixExecReport.set(Side(quickfix.field.Side.SELL))
        fixExecReport.set(OrderQty(5.0))
        fixExecReport.set(Price(52.5))
        fixExecReport.set(CumQty(0.0))
        fixExecReport.set(LeavesQty(5.0))
        fixExecReport.set(TransactTime())
        
        // Mock responses
        `when`(clOrdIdMappingService.getOrderData(betOrderId)).thenReturn(orderData)
        
        // When
        val enrichedReport = executionReportEnrichmentService.enrichExecutionReport(fixExecReport)
        
        // Then
        assertNotNull(enrichedReport)
        assertNotNull(enrichedReport.newOrder)
        
        val newOrder = enrichedReport.newOrder!!
        assertEquals(betOrderId, newOrder.fbgOrderId)
        assertEquals(TradingSide.Sell, newOrder.side)
        assertEquals(OrderType.Limit, newOrder.orderType)
        assertEquals(TimeInForce.ImmediateOrCancel, newOrder.timeInForce)
        assertEquals(5.0, newOrder.quantity)
        assertEquals(52.5, newOrder.price)
        assertEquals("KXETHD-25JUN2311-T1509.99", newOrder.symbol)
        assertEquals(newOrderClOrdId, newOrder.clientOrderId)
    }

    @Test
    fun `test ExecutionReport for ModifyOrder contains original and new ClOrdID`() {
        // Given
        val betOrderId = "modify-order-456"
        val modifyClOrdId = "FBG_modify_order_456_M_1234567890"
        val origClOrdId = "FBG_modify_order_456"
        
        val orderData = OrderRequestDTO(
            orderId = betOrderId,
            symbol = "KXETHD-25JUN2311-T1509.99",
            side = OrderSide.BUY,
            quantity = BigDecimal("3"),
            price = BigDecimal("50"),
            orderType = OrderType.LIMIT,
            timeInForce = TimeInForce.GTC,
            userId = "test-user"
        )
        
        // Create FIX execution report for modify acceptance
        val fixExecReport = FixExecutionReport()
        fixExecReport.set(ClOrdID(modifyClOrdId))
        fixExecReport.set(OrigClOrdID(origClOrdId))
        fixExecReport.set(OrderID("exchange-456"))
        fixExecReport.set(ExecID("exec-modify-001"))
        fixExecReport.set(ExecType(quickfix.field.ExecType.REPLACED))
        fixExecReport.set(OrdStatus(quickfix.field.OrdStatus.REPLACED))
        fixExecReport.set(Symbol("KXETHD-25JUN2311-T1509.99"))
        fixExecReport.set(Side(quickfix.field.Side.BUY))
        fixExecReport.set(OrderQty(3.0))
        fixExecReport.set(Price(50.0))
        fixExecReport.set(CumQty(0.0))
        fixExecReport.set(LeavesQty(3.0))
        fixExecReport.set(TransactTime())
        
        // Mock responses
        `when`(clOrdIdMappingService.getOrderData(betOrderId)).thenReturn(orderData)
        
        // When
        val enrichedReport = executionReportEnrichmentService.enrichExecutionReport(fixExecReport)
        
        // Then
        assertNotNull(enrichedReport)
        assertNotNull(enrichedReport.modifyOrder)
        assertNull(enrichedReport.newOrder)
        assertNull(enrichedReport.cancelOrder)
        
        val modifyOrder = enrichedReport.modifyOrder!!
        assertEquals(betOrderId, modifyOrder.fbgOrderId)
        assertEquals(origClOrdId, modifyOrder.originalClientOrderId)
        assertEquals(modifyClOrdId, modifyOrder.clientOrderId)
        assertEquals(TradingSide.Buy, modifyOrder.side)
        assertEquals(OrderType.Limit, modifyOrder.orderType)
        assertEquals(TimeInForce.GoodTillCancel, modifyOrder.timeInForce)
        assertEquals(3.0, modifyOrder.quantity)
        assertEquals(50.0, modifyOrder.price)
        assertEquals("KXETHD-25JUN2311-T1509.99", modifyOrder.symbol)
    }

    @Test
    fun `test ExecutionReport for CancelOrder contains cancel details`() {
        // Given
        val betOrderId = "cancel-order-789"
        val cancelClOrdId = "FBG_cancel_order_789_C_9876543210"
        val origClOrdId = "FBG_cancel_order_789"
        
        val orderData = OrderRequestDTO(
            orderId = betOrderId,
            symbol = "KXETHD-25JUN2311-T1509.99",
            side = OrderSide.SELL,
            quantity = BigDecimal("1"),
            price = BigDecimal("55"),
            orderType = OrderType.LIMIT,
            timeInForce = TimeInForce.GTC,
            userId = "test-user"
        )
        
        // Create FIX execution report for cancel acceptance
        val fixExecReport = FixExecutionReport()
        fixExecReport.set(ClOrdID(cancelClOrdId))
        fixExecReport.set(OrigClOrdID(origClOrdId))
        fixExecReport.set(OrderID("exchange-789"))
        fixExecReport.set(ExecID("exec-cancel-001"))
        fixExecReport.set(ExecType(quickfix.field.ExecType.CANCELED))
        fixExecReport.set(OrdStatus(quickfix.field.OrdStatus.CANCELED))
        fixExecReport.set(Symbol("KXETHD-25JUN2311-T1509.99"))
        fixExecReport.set(Side(quickfix.field.Side.SELL))
        fixExecReport.set(CumQty(0.0))
        fixExecReport.set(LeavesQty(0.0))
        fixExecReport.set(TransactTime())
        
        // Mock responses
        `when`(clOrdIdMappingService.getOrderData(betOrderId)).thenReturn(orderData)
        
        // When
        val enrichedReport = executionReportEnrichmentService.enrichExecutionReport(fixExecReport)
        
        // Then
        assertNotNull(enrichedReport)
        assertNotNull(enrichedReport.cancelOrder)
        assertNull(enrichedReport.newOrder)
        assertNull(enrichedReport.modifyOrder)
        
        val cancelOrder = enrichedReport.cancelOrder!!
        assertEquals(betOrderId, cancelOrder.fbgOrderId)
        assertEquals(origClOrdId, cancelOrder.originalClientOrderId)
        assertEquals(cancelClOrdId, cancelOrder.clientOrderId)
        assertEquals(TradingSide.Sell, cancelOrder.side)
        assertEquals("KXETHD-25JUN2311-T1509.99", cancelOrder.symbol)
    }

    @Test
    fun `test ExecutionReport enum mappings are correct`() {
        // Given
        val betOrderId = "enum-test-123"
        val clOrdId = "FBG_enum_test_123"
        
        val orderData = OrderRequestDTO(
            orderId = betOrderId,
            symbol = "TEST-SYMBOL",
            side = OrderSide.BUY,
            quantity = BigDecimal("1"),
            price = BigDecimal("50"),
            orderType = OrderType.MARKET,
            timeInForce = TimeInForce.FOK,
            userId = "test-user"
        )
        
        // Create FIX execution report with various enum values
        val fixExecReport = FixExecutionReport()
        fixExecReport.set(ClOrdID(clOrdId))
        fixExecReport.set(OrderID("enum-test-order"))
        fixExecReport.set(ExecID("exec-enum-001"))
        fixExecReport.set(ExecType(quickfix.field.ExecType.PARTIAL_FILL))  // '1'
        fixExecReport.set(OrdStatus(quickfix.field.OrdStatus.PARTIALLY_FILLED))  // '1'
        fixExecReport.set(Symbol("TEST-SYMBOL"))
        fixExecReport.set(Side(quickfix.field.Side.BUY))  // '1'
        fixExecReport.set(OrderQty(1.0))
        fixExecReport.set(CumQty(0.5))
        fixExecReport.set(LeavesQty(0.5))
        fixExecReport.set(LastQty(0.5))
        fixExecReport.set(LastPx(50.0))
        fixExecReport.set(TransactTime())
        
        // Mock responses
        `when`(clOrdIdMappingService.getOrderData(betOrderId)).thenReturn(orderData)
        
        // When
        val enrichedReport = executionReportEnrichmentService.enrichExecutionReport(fixExecReport)
        
        // Then
        assertNotNull(enrichedReport)
        assertEquals(ExecutionType.PartialFill, enrichedReport.execType)
        assertEquals(OrderStatus.PartiallyFilled, enrichedReport.ordStatus)
        assertEquals(TradingSide.Buy, enrichedReport.side)
        
        // Verify the newOrder has correct enum mappings
        assertNotNull(enrichedReport.newOrder)
        val newOrder = enrichedReport.newOrder!!
        assertEquals(TradingSide.Buy, newOrder.side)
        assertEquals(OrderType.Market, newOrder.orderType)
        assertEquals(TimeInForce.FillOrKill, newOrder.timeInForce)
    }
}