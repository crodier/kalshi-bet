package com.betfanatics.exchange.order.actor

import quickfix.*
import quickfix.field.*
import quickfix.fix50sp2.*
import quickfix.Message
import com.betfanatics.exchange.order.util.KalshiSignatureUtil
import com.betfanatics.exchange.order.util.FixMessageLogger
import com.betfanatics.exchange.order.util.FixClOrdIdGenerator
import com.betfanatics.exchange.order.actor.common.OrderActorResolver
import com.betfanatics.exchange.order.health.FixHealthIndicator
import com.betfanatics.exchange.order.service.FixErrorService
import com.betfanatics.exchange.order.service.ClOrdIdMappingService
import com.betfanatics.exchange.order.service.ExecutionReportEnrichmentService
import com.betfanatics.exchange.order.test.FixMessageInterceptorRegistry
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.security.PrivateKey
import java.time.Instant
import java.math.BigDecimal

class QuickfixJApplication(
    private val isMockMode: Boolean,
    private val privateKey: PrivateKey?,
    private val orderActorResolver: OrderActorResolver,
    private val fixHealthIndicator: FixHealthIndicator,
    private val fixErrorService: FixErrorService,
    private val fixClOrdIdGenerator: FixClOrdIdGenerator,
    private val clOrdIdMappingService: ClOrdIdMappingService,
    private val executionReportEnrichmentService: ExecutionReportEnrichmentService,
    private val onConnected: (SessionID) -> Unit,
    private val onDisconnected: () -> Unit
) : Application {
    
    private val log: Logger = LoggerFactory.getLogger(QuickfixJApplication::class.java)
    
    override fun onCreate(sessionId: SessionID) {
        log.info("QuickFIX/J session created: {}", sessionId)
    }
    
    override fun onLogon(sessionId: SessionID) {
        onConnected(sessionId)
        fixHealthIndicator.setConnected(sessionId)
        fixClOrdIdGenerator.onSessionStart()
        log.info("QuickFIX/J session logged on: {} with ClOrdID prefix: {}", 
            sessionId, fixClOrdIdGenerator.getCurrentSessionPrefix())
        // TODO handle sequence gaps
    }
    
    override fun onLogout(sessionId: SessionID) {
        onDisconnected()
        fixHealthIndicator.setDisconnected()
        log.info("QuickFIX/J session logged out: {}", sessionId)
    }

    // fired when quickfix is sending a message to the server
    // we intercept to add fields to the logon message
    // also fires for heartbeat messages, which we ignore
    override fun toAdmin(message: Message, sessionId: SessionID) {
        // Log all admin messages
        val msgType = try { message.header.getString(MsgType.FIELD) } catch (e: Exception) { "UNKNOWN" }
        FixMessageLogger.logAdminMessage(message, sessionId.toString(), "OUTGOING")
        
        // Notify interceptors for testing
        FixMessageInterceptorRegistry.notifyAdminMessage(message, sessionId, msgType, "OUTGOING")
        
        try {
            if (MsgType.LOGON == msgType) {
                val sendingTime = message.header.getString(SendingTime.FIELD)
                val msgType = message.header.getString(MsgType.FIELD)
                val msgSeqNum = message.header.getString(MsgSeqNum.FIELD)
                val senderCompID = message.header.getString(SenderCompID.FIELD)
                val targetCompID = message.header.getString(TargetCompID.FIELD)
                val soh = "\u0001"
                val preHashString = listOf(sendingTime, msgType, msgSeqNum, senderCompID, targetCompID).joinToString(soh)
                log.info("PreHashString: [{}]", preHashString)
                log.info("PreHashString values: SendingTime=[{}] MsgType=[{}] MsgSeqNum=[{}] SenderCompID=[{}] TargetCompID=[{}]", sendingTime, msgType, msgSeqNum, senderCompID, targetCompID)
                val rawDataValue = privateKey?.let {
                    KalshiSignatureUtil.generateSignature(sendingTime, msgType, msgSeqNum, senderCompID, targetCompID, it)
                } ?: ""


                if (!isMockMode) {
                    log.info("Generated RawData signature: {}", rawDataValue)
                    message.setInt(EncryptMethod.FIELD, 0) // 98=0 (None)
                    message.setInt(RawDataLength.FIELD, rawDataValue.length) // 95
                    message.setString(RawData.FIELD, rawDataValue) // 96=signature
                }

                message.setString(DefaultApplVerID.FIELD, "9") // 1137=9 (FIX50SP2)
                message.setString(8013, "N") // CancelOrdersOnDisconnect (optional)

                log.info("Logon message after setting fields: {}", message)
                log.info("Outgoing FIX string: {}", message.toString().replace('\u0001', '|'))
            }
        } catch (e: Exception) {
            log.error("Error setting logon fields: {}", e.message)
            fixErrorService.reportAuthenticationError(sessionId.toString(), 
                "Failed to set logon fields: ${e.message}", 
                mapOf("exception" to e.javaClass.simpleName))
        }
    }
    
    override fun toApp(message: Message, sessionId: SessionID) {
        // Extract order ID for correlation
        val orderId = try { message.getString(ClOrdID.FIELD) } catch (e: Exception) { null }
        val msgType = try { message.header.getString(MsgType.FIELD) } catch (e: Exception) { "UNKNOWN" }
        
        FixMessageLogger.logOutgoingFixMessage(message, orderId, sessionId.toString())
        
        // Notify interceptors for testing
        FixMessageInterceptorRegistry.notifyOutgoingMessage(message, sessionId, msgType, orderId)
        
        log.info("HANDOFF_TO_FIX: from=QuickfixJApplication to=FixProtocol sessionId={} orderId={}", 
            sessionId, orderId ?: "unknown")
    }
    
    override fun fromAdmin(message: Message, sessionId: SessionID) {
        try {
            val msgType = message.header.getString(MsgType.FIELD)
            
            // Log based on message type
            when (msgType) {
                MsgType.HEARTBEAT -> {
                    FixMessageLogger.logHeartbeat(message, sessionId.toString(), "INCOMING")
                }
                else -> {
                    FixMessageLogger.logAdminMessage(message, sessionId.toString(), "INCOMING")
                }
            }
            
            // Notify interceptors for testing
            FixMessageInterceptorRegistry.notifyAdminMessage(message, sessionId, msgType, "INCOMING")
            
            val seqNum = message.header.getInt(MsgSeqNum.FIELD)
            val possDupFlag = if (message.header.isSetField(PossDupFlag.FIELD)) 
                message.header.getBoolean(PossDupFlag.FIELD) else false
            
            when (msgType) {
                MsgType.HEARTBEAT -> {
                    // TODO check for gaps in sequence
                    log.trace("Heartbeat received - SeqNum: {}, PossDup: {}", seqNum, possDupFlag)
                }
                MsgType.RESEND_REQUEST -> {
                    log.info("Received resend request from exchange: {}", message)
                    val beginSeq = message.getInt(BeginSeqNo.FIELD)
                    val endSeq = message.getInt(EndSeqNo.FIELD)
                    fixErrorService.reportSequenceError(sessionId.toString(), 
                        "Resend request received", beginSeq, endSeq)
                }
                MsgType.SEQUENCE_RESET -> {
                    log.info("Received sequence reset: {}", message)
                    val newSeqNo = message.getInt(NewSeqNo.FIELD)
                    fixErrorService.reportSequenceError(sessionId.toString(), 
                        "Sequence reset received", null, newSeqNo)
                }
                else -> {
                    log.info("Admin message received - Type: {}, SeqNum: {}, PossDup: {}", 
                            msgType, seqNum, possDupFlag)
                }
            }
        } catch (e: Exception) {
            log.error("Error processing admin message: {}", e.message)
            fixErrorService.reportMessageProcessingError(sessionId.toString(),
                "Admin message processing failed: ${e.message}",
                try { message.header.getString(MsgType.FIELD) } catch (ex: Exception) { null })
        }
    }
    
    override fun fromApp(message: Message, sessionId: SessionID) {
        // Extract ClOrdID and lookup actual orderId
        val clOrdId = try { message.getString(ClOrdID.FIELD) } catch (e: Exception) { null }
        val orderId = clOrdId?.let { clOrdIdMappingService.getOrderIdByClOrdId(it) }
        val msgType = try { message.header.getString(MsgType.FIELD) } catch (e: Exception) { "UNKNOWN" }
        
        FixMessageLogger.logIncomingFixMessage(message, orderId, sessionId.toString())
        
        // Notify interceptors for testing
        FixMessageInterceptorRegistry.notifyIncomingMessage(message, sessionId, msgType, orderId)
        
        log.info("HANDOFF_FROM_FIX: from=FixProtocol to=QuickfixJApplication sessionId={} orderId={}", 
            sessionId, orderId ?: "unknown")
        
        try {
            val msgType = message.header.getString(MsgType.FIELD)
            
            if (msgType == MsgType.EXECUTION_REPORT) {
                // Enrich the execution report with original order data
                val enrichedReport = executionReportEnrichmentService.enrichExecutionReport(message)
                if (enrichedReport == null) {
                    log.error("Failed to enrich execution report")
                    return
                }
                
                val betOrderId = enrichedReport.betOrderId
                if (betOrderId == "UNKNOWN") {
                    log.error("No betOrderId mapping found for ClOrdID={}", enrichedReport.clOrdId)
                    return
                }
                
                val hasOriginalOrder = enrichedReport.newOrder != null || enrichedReport.modifyOrder != null || enrichedReport.cancelOrder != null
                log.info("[FIX ExecutionReport] ClOrdID={}, BetOrderId={}, ExchangeOrderId={}, ExecType={}, OrdStatus={}, CumQty={}, LeavesQty={}, LastPx={}, LastQty={}, Symbol={}, Side={}, HasOriginalOrder={}, OrderType={}",
                    enrichedReport.clOrdID, enrichedReport.betOrderId, enrichedReport.orderID,
                    enrichedReport.execType, enrichedReport.ordStatus, enrichedReport.cumQty, 
                    enrichedReport.leavesQty, enrichedReport.lastPx, enrichedReport.lastQty, 
                    enrichedReport.instrument.symbol, enrichedReport.side, hasOriginalOrder,
                    when {
                        enrichedReport.newOrder != null -> "NewOrder"
                        enrichedReport.modifyOrder != null -> "ModifyOrder" 
                        enrichedReport.cancelOrder != null -> "CancelOrder"
                        else -> "Unknown"
                    })

                // Handle order status and execution updates
                val orderRef = orderActorResolver(betOrderId)
                
                // Pass the enriched execution report to the order actor
                val execType = enrichedReport.execType
                val ordStatus = enrichedReport.ordStatus
                val lastQty = enrichedReport.lastQty
                
                when (ordStatus) {
                    OrderStatus.New -> { // Order accepted by exchange
                        log.info("[FIX ExecutionReport] Order accepted (OrdStatus=0) for orderId={}", orderId)
                        orderRef.tell(OrderActor.FixOrderAccepted(Instant.now()))
                    }
                    "1", "2" -> { // Partially filled or Filled
                        if ((execType == ExecType.TRADE.toString() || execType == "F") && lastQty != null && lastQty > 0.0) {
                            log.info("[FIX ExecutionReport] Trade execution (OrdStatus={}) for orderId={}, filledQty={}", ordStatus, orderId, lastQty)
                            orderRef.tell(OrderActor.OrderFillUpdate(BigDecimal.valueOf(lastQty), Instant.now()))
                        }
                    }
                    "8" -> { // Rejected
                        log.info("[FIX ExecutionReport] Order rejected (OrdStatus=8) for orderId={}", orderId)
                        orderRef.tell(OrderActor.FixOrderRejected("Order rejected by exchange"))
                    }
                    "4" -> { // Canceled
                        log.info("[FIX ExecutionReport] Order canceled (OrdStatus=4) for orderId={}", orderId)
                        orderRef.tell(OrderActor.FixOrderCancelled("Order canceled by exchange"))
                    }
                    else -> {
                        log.debug("[FIX ExecutionReport] Unhandled OrdStatus={} for orderId={}", ordStatus, orderId)
                    }
                }
            } else if (msgType == "zz") { // UMS message
                log.info("[FIX UMS] {}", message)
                val id = message.getString(20105) // marketsettlementreportid
                val symbol = message.getString(55) // symbol
                /*
                TODO
                - get the orderId from the ClOrdID
                - build a new ClosePositionProcessManager
                - have it run a workflow to:
                    - mark the orderactor as settled
                    - send a message to the kalshi wallet actor to debit the user's account
                    - send a message to the fbg wallet actor to credit the user's account
                    - anything else?
                 */
            } else {
                // Unknown message type
                FixMessageLogger.logUnknownFixMessage(message, sessionId.toString(), 
                    "Unknown message type: $msgType")
                log.warn("UNKNOWN_FIX_MESSAGE: sessionId={} msgType={} orderId={}", 
                    sessionId, msgType, orderId ?: "unknown")
                fixErrorService.reportMessageProcessingError(sessionId.toString(),
                    "Unknown message type received: $msgType", msgType)
            }
            
        } catch (e: Exception) {
            log.error("Error parsing ExecutionReport: {}", e.message)
            fixErrorService.reportMessageProcessingError(sessionId.toString(),
                "Failed to parse message: ${e.message}",
                try { message.header.getString(MsgType.FIELD) } catch (ex: Exception) { null },
                mapOf("orderId" to (orderId ?: "unknown")))
        }
    }            
}