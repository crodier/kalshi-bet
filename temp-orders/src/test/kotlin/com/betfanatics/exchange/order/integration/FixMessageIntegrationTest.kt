package com.betfanatics.exchange.order.integration

import com.betfanatics.exchange.order.actor.common.*
import com.betfanatics.exchange.order.controller.OrderController
import com.betfanatics.exchange.order.service.ClOrdIdMappingService
import com.betfanatics.exchange.order.test.FixMessageInterceptor
import com.betfanatics.exchange.order.test.FixMessageInterceptorRegistry
import com.betfanatics.exchange.order.test.FixMessageConstants
import com.betfanatics.exchange.order.util.FixClOrdIdGenerator
import com.fbg.api.fix.domain.IncomingOrder
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.*
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.junit.jupiter.SpringExtension
import quickfix.Message
import quickfix.SessionID
import quickfix.field.ClOrdID
import quickfix.field.MsgType
import quickfix.field.OrderQty
import quickfix.field.Price
import quickfix.field.Side
import quickfix.field.Symbol
import quickfix.field.TimeInForce
import quickfix.field.OrdType
import java.math.BigDecimal
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@ExtendWith(SpringExtension::class)
@ActiveProfiles("test")
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    classes = [FixIntegrationTestConfig::class]
)
class FixMessageIntegrationTest {

    @Autowired
    lateinit var restTemplate: TestRestTemplate

    @Autowired
    lateinit var clOrdIdMappingService: ClOrdIdMappingService

    @Autowired
    lateinit var fixClOrdIdGenerator: FixClOrdIdGenerator

    @LocalServerPort
    var port: Int = 0

    private lateinit var capturedMessages: MutableList<CapturedFixMessage>
    private lateinit var messageInterceptor: TestFixMessageInterceptor

    data class CapturedFixMessage(
        val message: Message,
        val sessionId: SessionID,
        val messageType: String,
        val orderId: String?,
        val direction: String, // "OUTGOING", "INCOMING", "ADMIN_OUTGOING", "ADMIN_INCOMING"
        val timestamp: Long = System.currentTimeMillis()
    )

    inner class TestFixMessageInterceptor : FixMessageInterceptor {
        private var latch: CountDownLatch? = null

        fun expectMessages(count: Int) {
            latch = CountDownLatch(count)
        }

        fun waitForMessages(timeoutSeconds: Long = 5): Boolean {
            return latch?.await(timeoutSeconds, TimeUnit.SECONDS) ?: true
        }

        override fun onOutgoingMessage(message: Message, sessionId: SessionID, messageType: String, orderId: String?) {
            capturedMessages.add(CapturedFixMessage(message, sessionId, messageType, orderId, "OUTGOING"))
            latch?.countDown()
        }

        override fun onIncomingMessage(message: Message, sessionId: SessionID, messageType: String, orderId: String?) {
            capturedMessages.add(CapturedFixMessage(message, sessionId, messageType, orderId, "INCOMING"))
            latch?.countDown()
        }

        override fun onAdminMessage(message: Message, sessionId: SessionID, messageType: String, direction: String) {
            capturedMessages.add(CapturedFixMessage(message, sessionId, messageType, null, "ADMIN_$direction"))
            latch?.countDown()
        }
    }

    @BeforeEach
    fun setup() {
        capturedMessages = mutableListOf()
        messageInterceptor = TestFixMessageInterceptor()
        FixMessageInterceptorRegistry.register(messageInterceptor)
    }

    @AfterEach
    fun cleanup() {
        FixMessageInterceptorRegistry.clear()
    }

    @Test
    fun `should generate correct NewOrderSingle for BUY LIMIT order`() {
        // Given
        val betOrderId = "test-order-123"
        val newOrder = IncomingOrder.NewOrder(
            fbgOrderId = betOrderId,
            symbol = "KXETHD-25JUN2311-T1509.99",
            side = TradingSide.Buy,
            quantity = 2.0,
            price = 49.0,
            orderType = com.fbg.api.fix.enums.OrderType.Limit,
            timeInForce = com.fbg.api.fix.enums.TimeInForce.GoodTillCancel,
            clientOrderId = "FBG_test_order_123",
            userId = "test-user"
        )

        messageInterceptor.expectMessages(1)

        // When
        val url = "http://localhost:$port/v1/order"
        val request = mapOf(
            "orderId" to betOrderId,
            "symbol" to "KXETHD-25JUN2311-T1509.99",
            "side" to "BUY",
            "quantity" to BigDecimal("2"),
            "price" to BigDecimal("49"),
            "orderType" to "LIMIT",
            "timeInForce" to "GTC",
            "userId" to "test-user"
        )

        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON
        headers.set("X-Dev-User", "test-user")
        
        val entity = HttpEntity(request, headers)
        val response = restTemplate.postForEntity(url, entity, String::class.java)

        // Then
        assertEquals(HttpStatus.OK, response.statusCode)
        assertTrue(messageInterceptor.waitForMessages(), "Should capture outgoing FIX message")

        val outgoingMessages = capturedMessages.filter { it.direction == "OUTGOING" }
        assertEquals(1, outgoingMessages.size, "Should have exactly one outgoing message")

        val fixMessage = outgoingMessages.first()
        assertEquals("D", fixMessage.messageType, "Should be NewOrderSingle")
        assertEquals(betOrderId, fixMessage.orderId, "Should have correct order ID")

        // Verify FIX message fields
        val message = fixMessage.message
        assertEquals("FBG_test_order_123", message.getString(ClOrdID.FIELD))
        assertEquals("KXETHD-25JUN2311-T1509.99", message.getString(Symbol.FIELD))
        assertEquals(Side.BUY, message.getChar(Side.FIELD))
        assertEquals(2.0, message.getDouble(OrderQty.FIELD))
        assertEquals(49.0, message.getDouble(Price.FIELD))
        assertEquals(OrdType.LIMIT, message.getChar(OrdType.FIELD))
        assertEquals(TimeInForce.GOOD_TILL_CANCEL, message.getChar(TimeInForce.FIELD))

        // Verify message format matches expected pattern
        val pipeDelimited = message.toString().replace('\u0001', '|')
        println("Captured FIX message: $pipeDelimited")

        // Check that message contains expected fields (allowing for variable values)
        assertTrue(pipeDelimited.contains("35=D|"), "Should contain MsgType=D")
        assertTrue(pipeDelimited.contains("11=FBG_test_order_123|"), "Should contain ClOrdID")
        assertTrue(pipeDelimited.contains("55=KXETHD-25JUN2311-T1509.99|"), "Should contain Symbol")
        assertTrue(pipeDelimited.contains("54=1|"), "Should contain Side=BUY")
        assertTrue(pipeDelimited.contains("38=2|"), "Should contain OrderQty=2")
        assertTrue(pipeDelimited.contains("44=49|"), "Should contain Price=49")
        assertTrue(pipeDelimited.contains("40=2|"), "Should contain OrdType=LIMIT")
        assertTrue(pipeDelimited.contains("59=1|"), "Should contain TimeInForce=GTC")
    }

    @Test
    fun `should generate correct NewOrderSingle for SELL LIMIT order with IOC`() {
        // Given
        val betOrderId = "sell-order-456"
        messageInterceptor.expectMessages(1)

        // When
        val url = "http://localhost:$port/v1/order"
        val request = mapOf(
            "orderId" to betOrderId,
            "symbol" to "KXETHD-25JUN2311-T1509.99",
            "side" to "SELL",
            "quantity" to BigDecimal("5"),
            "price" to BigDecimal("52.5"),
            "orderType" to "LIMIT",
            "timeInForce" to "IOC",
            "userId" to "test-user-2"
        )

        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON
        headers.set("X-Dev-User", "test-user-2")
        
        val entity = HttpEntity(request, headers)
        val response = restTemplate.postForEntity(url, entity, String::class.java)

        // Then
        assertEquals(HttpStatus.OK, response.statusCode)
        assertTrue(messageInterceptor.waitForMessages(), "Should capture outgoing FIX message")

        val outgoingMessages = capturedMessages.filter { it.direction == "OUTGOING" }
        assertEquals(1, outgoingMessages.size, "Should have exactly one outgoing message")

        val fixMessage = outgoingMessages.first()
        assertEquals("D", fixMessage.messageType, "Should be NewOrderSingle")

        // Verify FIX message fields
        val message = fixMessage.message
        assertEquals(Side.SELL, message.getChar(Side.FIELD))
        assertEquals(5.0, message.getDouble(OrderQty.FIELD))
        assertEquals(52.5, message.getDouble(Price.FIELD))
        assertEquals(TimeInForce.IMMEDIATE_OR_CANCEL, message.getChar(TimeInForce.FIELD))

        val pipeDelimited = message.toString().replace('\u0001', '|')
        assertTrue(pipeDelimited.contains("54=2|"), "Should contain Side=SELL")
        assertTrue(pipeDelimited.contains("38=5|"), "Should contain OrderQty=5")
        assertTrue(pipeDelimited.contains("44=52.5|"), "Should contain Price=52.5")
        assertTrue(pipeDelimited.contains("59=3|"), "Should contain TimeInForce=IOC")
    }

    @Test
    fun `should generate correct NewOrderSingle for MARKET order`() {
        // Given
        val betOrderId = "market-order-789"
        messageInterceptor.expectMessages(1)

        // When
        val url = "http://localhost:$port/v1/order"
        val request = mapOf(
            "orderId" to betOrderId,
            "symbol" to "KXETHD-25JUN2311-T1509.99",
            "side" to "BUY",
            "quantity" to BigDecimal("1"),
            "orderType" to "MARKET",
            "timeInForce" to "FOK",
            "userId" to "market-user"
        )

        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON
        headers.set("X-Dev-User", "market-user")
        
        val entity = HttpEntity(request, headers)
        val response = restTemplate.postForEntity(url, entity, String::class.java)

        // Then
        assertEquals(HttpStatus.OK, response.statusCode)
        assertTrue(messageInterceptor.waitForMessages(), "Should capture outgoing FIX message")

        val outgoingMessages = capturedMessages.filter { it.direction == "OUTGOING" }
        assertEquals(1, outgoingMessages.size, "Should have exactly one outgoing message")

        val fixMessage = outgoingMessages.first()
        val message = fixMessage.message

        // Market orders should not have price field
        assertEquals(OrdType.MARKET, message.getChar(OrdType.FIELD))
        assertEquals(TimeInForce.FILL_OR_KILL, message.getChar(TimeInForce.FIELD))

        val pipeDelimited = message.toString().replace('\u0001', '|')
        assertTrue(pipeDelimited.contains("40=1|"), "Should contain OrdType=MARKET")
        assertTrue(pipeDelimited.contains("59=4|"), "Should contain TimeInForce=FOK")
        // Market orders should not contain price field
        assertTrue(!pipeDelimited.contains("44="), "Should not contain Price field for market order")
    }

    @Test
    fun `should generate correct OrderCancelRequest`() {
        // Given: First place an order
        val betOrderId = "cancel-test-order"
        val clOrdId = "FBG_cancel_test_order"
        
        // Store the ClOrdID mapping manually for testing
        clOrdIdMappingService.storeClOrdIdMapping(clOrdId, betOrderId)

        messageInterceptor.expectMessages(1)

        // When: Cancel the order
        val url = "http://localhost:$port/v1/order/cancel"
        val request = mapOf(
            "orderId" to betOrderId,
            "userId" to "test-user"
        )

        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON
        headers.set("X-Dev-User", "test-user")
        
        val entity = HttpEntity(request, headers)
        val response = restTemplate.postForEntity(url, entity, String::class.java)

        // Then
        assertEquals(HttpStatus.OK, response.statusCode)
        assertTrue(messageInterceptor.waitForMessages(), "Should capture outgoing cancel message")

        val outgoingMessages = capturedMessages.filter { it.direction == "OUTGOING" }
        assertEquals(1, outgoingMessages.size, "Should have exactly one outgoing message")

        val fixMessage = outgoingMessages.first()
        assertEquals("F", fixMessage.messageType, "Should be OrderCancelRequest")

        val message = fixMessage.message
        val pipeDelimited = message.toString().replace('\u0001', '|')
        println("Captured Cancel FIX message: $pipeDelimited")

        // Verify cancel message structure
        assertTrue(pipeDelimited.contains("35=F|"), "Should contain MsgType=F")
        assertTrue(pipeDelimited.contains("41="), "Should contain OrigClOrdID")
        
        // Cancel ClOrdID should be different from original
        val cancelClOrdId = message.getString(ClOrdID.FIELD)
        assertTrue(cancelClOrdId.contains("_C_"), "Cancel ClOrdID should contain _C_ timestamp")
        assertTrue(cancelClOrdId.startsWith("FBG_cancel_test_order_C_"), "Should start with FBG_betOrderId_C_")
    }

    @Test
    fun `should handle party groups correctly in NewOrderSingle`() {
        // Given
        val betOrderId = "party-test-order"
        messageInterceptor.expectMessages(1)

        // When
        val url = "http://localhost:$port/v1/order"
        val request = mapOf(
            "orderId" to betOrderId,
            "symbol" to "TEST-SYMBOL",
            "side" to "BUY",
            "quantity" to BigDecimal("1"),
            "price" to BigDecimal("50"),
            "orderType" to "LIMIT",
            "timeInForce" to "GTC",
            "userId" to "party-user"
        )

        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON
        headers.set("X-Dev-User", "party-user")
        
        val entity = HttpEntity(request, headers)
        val response = restTemplate.postForEntity(url, entity, String::class.java)

        // Then
        assertEquals(HttpStatus.OK, response.statusCode)
        assertTrue(messageInterceptor.waitForMessages())

        val fixMessage = capturedMessages.filter { it.direction == "OUTGOING" }.first()
        val pipeDelimited = fixMessage.message.toString().replace('\u0001', '|')

        // Verify party group fields
        assertTrue(pipeDelimited.contains("453=1|"), "Should contain NoPartyIDs=1")
        assertTrue(pipeDelimited.contains("448=b0f5a944-8dbd-46ad-a48b-f82e29f57599_party-user|"), "Should contain PartyID")
        assertTrue(pipeDelimited.contains("452=24|"), "Should contain PartyRole=24")
    }

    @Test 
    fun `should verify all required FIX header fields are present`() {
        // Given
        val betOrderId = "header-test-order"
        messageInterceptor.expectMessages(1)

        // When
        val url = "http://localhost:$port/v1/order"
        val request = mapOf(
            "orderId" to betOrderId,
            "symbol" to "TEST-SYMBOL",
            "side" to "BUY",
            "quantity" to BigDecimal("1"),
            "price" to BigDecimal("50"),
            "orderType" to "LIMIT",
            "timeInForce" to "GTC",
            "userId" to "header-user"
        )

        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON
        headers.set("X-Dev-User", "header-user")
        
        val entity = HttpEntity(request, headers)
        val response = restTemplate.postForEntity(url, entity, String::class.java)

        // Then
        assertEquals(HttpStatus.OK, response.statusCode)
        assertTrue(messageInterceptor.waitForMessages())

        val fixMessage = capturedMessages.filter { it.direction == "OUTGOING" }.first()
        val message = fixMessage.message
        val pipeDelimited = message.toString().replace('\u0001', '|')

        println("Complete FIX message: $pipeDelimited")

        // Verify standard FIX header fields are present
        assertTrue(pipeDelimited.contains("8=FIXT.1.1|"), "Should contain BeginString")
        assertTrue(pipeDelimited.contains("9="), "Should contain BodyLength")
        assertTrue(pipeDelimited.contains("35=D|"), "Should contain MsgType")
        assertTrue(pipeDelimited.contains("34="), "Should contain MsgSeqNum")
        assertTrue(pipeDelimited.contains("49="), "Should contain SenderCompID")
        assertTrue(pipeDelimited.contains("56="), "Should contain TargetCompID")
        assertTrue(pipeDelimited.contains("52="), "Should contain SendingTime")
        assertTrue(pipeDelimited.contains("60="), "Should contain TransactTime")
        assertTrue(pipeDelimited.endsWith("10="), "Should end with CheckSum")
    }

    @Test
    fun `should verify ClOrdID format matches specification`() {
        // Given
        val betOrderId = "clordid-format-test"
        val expectedClOrdId = "FBG_clordid_format_test"
        
        messageInterceptor.expectMessages(1)

        // When  
        val url = "http://localhost:$port/v1/order"
        val request = mapOf(
            "orderId" to betOrderId,
            "symbol" to "TEST-SYMBOL",
            "side" to "BUY",
            "quantity" to BigDecimal("1"),
            "price" to BigDecimal("50"),
            "orderType" to "LIMIT",
            "timeInForce" to "GTC",
            "userId" to "format-user"
        )

        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON
        headers.set("X-Dev-User", "format-user")
        
        val entity = HttpEntity(request, headers)
        val response = restTemplate.postForEntity(url, entity, String::class.java)

        // Then
        assertEquals(HttpStatus.OK, response.statusCode)
        assertTrue(messageInterceptor.waitForMessages())

        val fixMessage = capturedMessages.filter { it.direction == "OUTGOING" }.first()
        val actualClOrdId = fixMessage.message.getString(ClOrdID.FIELD)

        assertEquals(expectedClOrdId, actualClOrdId, "ClOrdID should match FBG_<betOrderId> format")
        assertTrue(actualClOrdId.startsWith("FBG_"), "ClOrdID should start with FBG_")
        assertTrue(!actualClOrdId.contains("_M_"), "New order ClOrdID should not contain modify suffix")
        assertTrue(!actualClOrdId.contains("_C_"), "New order ClOrdID should not contain cancel suffix")
    }
}