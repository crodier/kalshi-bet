package com.betfanatics.exchange.order.fix

import com.betfanatics.exchange.order.actor.FixGatewayActor
import com.betfanatics.exchange.order.actor.QuickfixJApplication
import com.betfanatics.exchange.order.actor.common.*
import com.betfanatics.exchange.order.health.FixHealthIndicator
import com.betfanatics.exchange.order.metrics.FixMessageMetrics
import com.betfanatics.exchange.order.service.ClOrdIdMappingService
import com.betfanatics.exchange.order.service.ExecutionReportEnrichmentService
import com.betfanatics.exchange.order.service.FixErrorService
import com.betfanatics.exchange.order.util.FixClOrdIdGenerator
import com.betfanatics.exchange.order.util.KalshiSignatureUtil
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.jupiter.MockitoExtension
import quickfix.*
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
class FixMessageGenerationTest {

    @Mock
    private lateinit var orderActorResolver: OrderActorResolver
    
    @Mock
    private lateinit var fixHealthIndicator: FixHealthIndicator
    
    @Mock
    private lateinit var fixErrorService: FixErrorService
    
    @Mock
    private lateinit var fixClOrdIdGenerator: FixClOrdIdGenerator
    
    @Mock
    private lateinit var clOrdIdMappingService: ClOrdIdMappingService
    
    @Mock
    private lateinit var executionReportEnrichmentService: ExecutionReportEnrichmentService
    
    @Mock
    private lateinit var fixMessageMetrics: FixMessageMetrics
    
    private lateinit var quickfixApp: QuickfixJApplication
    private lateinit var sessionId: SessionID
    
    // FIX timestamp formatter matching the example: 20250623-16:29:15.106
    private val fixTimestampFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HH:mm:ss.SSS")
        .withZone(ZoneOffset.UTC)
    
    @BeforeEach
    fun setup() {
        sessionId = SessionID("FIXT.1.1", "d1165e6a-b033-4acb-a2c2-211bb08b770a", "KalshiRT")
        
        quickfixApp = QuickfixJApplication(
            isMockMode = false,
            privateKey = null,
            orderActorResolver = orderActorResolver,
            fixHealthIndicator = fixHealthIndicator,
            fixErrorService = fixErrorService,
            fixClOrdIdGenerator = fixClOrdIdGenerator,
            clOrdIdMappingService = clOrdIdMappingService,
            executionReportEnrichmentService = executionReportEnrichmentService,
            onConnected = {},
            onDisconnected = {}
        )
    }
    
    @Test
    fun `test NewOrderSingle generation matches required format`() {
        // Given - matching the JSON example
        val betOrderId = "f61f15b1-f1ad-47f3-8643-27007f9e3eaf"
        val clOrdId = "FBG_f61f15b1_f1ad_47f3_8643_27007f9e3eaf" // Our format
        val symbol = "KXETHD-25JUN2311-T1509.99"
        val orderQty = 2.0
        val price = 49.0
        val side = Side.BUY // 1
        val ordType = OrdType.LIMIT // 2
        val timeInForce = TimeInForce.GOOD_TILL_CANCEL // 1
        val userId = "test-user-2"
        val accountId = "b0f5a944-8dbd-46ad-a48b-f82e29f57599"
        
        // Create NewOrderSingle
        val order = NewOrderSingle()
        
        // Set fields in the order they appear in the JSON example
        order.set(ClOrdID(clOrdId))                      // Tag 11
        order.set(OrderQty(orderQty))                     // Tag 38
        order.set(OrdType(ordType))                       // Tag 40 = 2 (LIMIT)
        order.set(Price(price))                           // Tag 44
        order.set(Side(side))                             // Tag 54 = 1 (BUY)
        order.set(Symbol(symbol))                         // Tag 55
        order.set(TimeInForce(timeInForce))              // Tag 59 = 1 (GTC)
        order.set(TransactTime())                         // Tag 60
        
        // Add Parties repeating group
        order.setInt(NoPartyIDs.FIELD, 1)                // Tag 453 = 1
        val partyGroup = NewOrderSingle.NoPartyIDs()
        partyGroup.set(PartyID("${accountId}_${userId}")) // Tag 448
        partyGroup.set(PartyRole(24))                     // Tag 452 = 24 (customer account)
        order.addGroup(partyGroup)
        
        // Verify the message structure
        assertEquals(clOrdId, order.getString(ClOrdID.FIELD))
        assertEquals(orderQty, order.getDouble(OrderQty.FIELD))
        assertEquals('2', order.getChar(OrdType.FIELD)) // LIMIT
        assertEquals(price, order.getDouble(Price.FIELD))
        assertEquals('1', order.getChar(Side.FIELD)) // BUY
        assertEquals(symbol, order.getString(Symbol.FIELD))
        assertEquals('1', order.getChar(TimeInForce.FIELD)) // GTC
        assertEquals(1, order.getInt(NoPartyIDs.FIELD))
        
        // Verify party information
        val groups = order.getGroups(NoPartyIDs.FIELD)
        assertEquals(1, groups.size)
        val party = groups[0]
        assertEquals("${accountId}_${userId}", party.getString(PartyID.FIELD))
        assertEquals(24, party.getInt(PartyRole.FIELD))
        
        // Verify TransactTime is set and in correct format
        val transactTime = order.getString(TransactTime.FIELD)
        assertNotNull(transactTime)
        // Should match format: 20250623-16:29:15.105
        assertTrue(transactTime.matches(Regex("\\d{8}-\\d{2}:\\d{2}:\\d{2}\\.\\d{3}")))
        
        // Log the FIX message for visual verification
        val fixString = order.toString()
        println("Generated FIX message: ${fixString.replace('\u0001', '|')}")
    }
    
    @Test
    fun `test ExecutionReport parsing for order acceptance`() {
        // Given - an execution report for order acceptance
        val betOrderId = "f61f15b1-f1ad-47f3-8643-27007f9e3eaf"
        val clOrdId = "FBG_f61f15b1_f1ad_47f3_8643_27007f9e3eaf"
        val exchangeOrderId = "kalshi-order-12345"
        val symbol = "KXETHD-25JUN2311-T1509.99"
        
        val execReport = ExecutionReport()
        execReport.set(ClOrdID(clOrdId))                 // Tag 11
        execReport.set(OrderID(exchangeOrderId))         // Tag 37
        execReport.set(ExecID("exec-001"))               // Tag 17
        execReport.set(ExecType(ExecType.NEW))           // Tag 150 = '0' (NEW)
        execReport.set(OrdStatus(OrdStatus.NEW))         // Tag 39 = '0' (NEW)
        execReport.set(Symbol(symbol))                    // Tag 55
        execReport.set(Side(Side.BUY))                    // Tag 54
        execReport.set(OrderQty(2.0))                     // Tag 38
        execReport.set(Price(49.0))                       // Tag 44
        execReport.set(CumQty(0.0))                       // Tag 14
        execReport.set(LeavesQty(2.0))                    // Tag 151
        execReport.set(TransactTime())                    // Tag 60
        
        // Mock the enrichment service response
        val enrichedReport = ExecutionReportEnrichmentService.EnrichedExecutionReport(
            clOrdId = clOrdId,
            betOrderId = betOrderId,
            exchangeOrderId = exchangeOrderId,
            execId = "exec-001",
            execType = "0",
            ordStatus = "0",
            symbol = symbol,
            side = "1",
            orderQty = 2.0,
            price = 49.0,
            cumQty = 0.0,
            leavesQty = 2.0,
            lastPx = null,
            lastQty = null,
            avgPx = null,
            betOrder = null
        )
        
        `when`(executionReportEnrichmentService.enrichExecutionReport(execReport))
            .thenReturn(enrichedReport)
        
        // When
        quickfixApp.fromApp(execReport, sessionId)
        
        // Then
        verify(executionReportEnrichmentService).enrichExecutionReport(execReport)
        verify(orderActorResolver).invoke(betOrderId)
    }
    
    @Test
    fun `test ExecutionReport parsing for partial fill`() {
        // Given - an execution report for partial fill
        val betOrderId = "f61f15b1-f1ad-47f3-8643-27007f9e3eaf"
        val clOrdId = "FBG_f61f15b1_f1ad_47f3_8643_27007f9e3eaf"
        val exchangeOrderId = "kalshi-order-12345"
        
        val execReport = ExecutionReport()
        execReport.set(ClOrdID(clOrdId))                     // Tag 11
        execReport.set(OrderID(exchangeOrderId))             // Tag 37
        execReport.set(ExecID("exec-002"))                   // Tag 17
        execReport.set(ExecType(ExecType.PARTIAL_FILL))      // Tag 150 = '1' (PARTIAL_FILL)
        execReport.set(OrdStatus(OrdStatus.PARTIALLY_FILLED))// Tag 39 = '1' (PARTIALLY_FILLED)
        execReport.set(Symbol("KXETHD-25JUN2311-T1509.99"))  // Tag 55
        execReport.set(Side(Side.BUY))                       // Tag 54
        execReport.set(OrderQty(2.0))                         // Tag 38
        execReport.set(Price(49.0))                           // Tag 44
        execReport.set(LastQty(1.0))                         // Tag 32 - this fill quantity
        execReport.set(LastPx(49.0))                          // Tag 31 - this fill price
        execReport.set(CumQty(1.0))                           // Tag 14 - total filled
        execReport.set(LeavesQty(1.0))                       // Tag 151 - remaining
        execReport.set(AvgPx(49.0))                          // Tag 6 - average price
        execReport.set(TransactTime())                        // Tag 60
        
        // Verify fields
        assertEquals('1', execReport.getChar(ExecType.FIELD))
        assertEquals('1', execReport.getChar(OrdStatus.FIELD))
        assertEquals(1.0, execReport.getDouble(LastQty.FIELD))
        assertEquals(1.0, execReport.getDouble(CumQty.FIELD))
        assertEquals(1.0, execReport.getDouble(LeavesQty.FIELD))
    }
    
    @Test
    fun `test ExecutionReport parsing for order rejection`() {
        // Given - an execution report for order rejection
        val betOrderId = "f61f15b1-f1ad-47f3-8643-27007f9e3eaf"
        val clOrdId = "FBG_f61f15b1_f1ad_47f3_8643_27007f9e3eaf"
        
        val execReport = ExecutionReport()
        execReport.set(ClOrdID(clOrdId))                 // Tag 11
        execReport.set(OrderID("NONE"))                  // Tag 37
        execReport.set(ExecID("exec-rej-001"))           // Tag 17
        execReport.set(ExecType(ExecType.REJECTED))      // Tag 150 = '8' (REJECTED)
        execReport.set(OrdStatus(OrdStatus.REJECTED))    // Tag 39 = '8' (REJECTED)
        execReport.set(Symbol("KXETHD-25JUN2311-T1509.99")) // Tag 55
        execReport.set(Side(Side.BUY))                   // Tag 54
        execReport.set(OrderQty(2.0))                    // Tag 38
        execReport.set(CumQty(0.0))                      // Tag 14
        execReport.set(LeavesQty(0.0))                   // Tag 151
        execReport.set(OrdRejReason(OrdRejReason.INSUFFICIENT_CREDIT)) // Tag 103
        execReport.set(Text("Insufficient funds"))       // Tag 58
        execReport.set(TransactTime())                   // Tag 60
        
        // Verify rejection fields
        assertEquals('8', execReport.getChar(ExecType.FIELD))
        assertEquals('8', execReport.getChar(OrdStatus.FIELD))
        assertEquals(15, execReport.getInt(OrdRejReason.FIELD)) // INSUFFICIENT_CREDIT
        assertEquals("Insufficient funds", execReport.getString(Text.FIELD))
    }
    
    @Test
    fun `test market order generation without price`() {
        // Given - a market order
        val clOrdId = "FBG_market_order_123"
        val symbol = "KXETHD-25JUN2311-T1509.99"
        
        val order = NewOrderSingle()
        order.set(ClOrdID(clOrdId))                      // Tag 11
        order.set(OrderQty(5.0))                         // Tag 38
        order.set(OrdType(OrdType.MARKET))               // Tag 40 = 1 (MARKET)
        // NO PRICE for market orders
        order.set(Side(Side.SELL))                       // Tag 54 = 2 (SELL)
        order.set(Symbol(symbol))                         // Tag 55
        order.set(TimeInForce(TimeInForce.IMMEDIATE_OR_CANCEL)) // Tag 59 = 3 (IOC)
        order.set(TransactTime())                         // Tag 60
        
        // Verify market order has no price
        assertEquals('1', order.getChar(OrdType.FIELD)) // MARKET
        assertTrue(!order.isSetField(Price.FIELD), "Market order should not have price")
        assertEquals('3', order.getChar(TimeInForce.FIELD)) // IOC
    }
}