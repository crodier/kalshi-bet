package com.betfanatics.exchange.order.util

import org.slf4j.LoggerFactory
import quickfix.Message
import quickfix.field.*
import java.time.Instant
import com.betfanatics.exchange.order.metrics.FixMessageMetrics
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class FixMessageLogger @Autowired constructor(
    private val fixMessageMetrics: FixMessageMetrics
) {
    
    private val log = LoggerFactory.getLogger(FixMessageLogger::class.java)
    
    companion object {
        // Static instance for when dependency injection isn't available
        private var instance: FixMessageLogger? = null
        
        fun setInstance(fixMessageLogger: FixMessageLogger) {
            instance = fixMessageLogger
        }
        
        private fun getInstance(): FixMessageLogger? = instance
        
        // Static methods for backward compatibility
        @JvmStatic
        fun logOutgoingFixMessage(message: Message, orderId: String? = null, sessionId: String? = null) {
            getInstance()?.logOutgoingFixMessage(message, orderId, sessionId)
                ?: logWithoutMetrics("FIX_OUTGOING", message, orderId, sessionId)
        }
        
        @JvmStatic
        fun logIncomingFixMessage(message: Message, orderId: String? = null, sessionId: String? = null) {
            getInstance()?.logIncomingFixMessage(message, orderId, sessionId)
                ?: logWithoutMetrics("FIX_INCOMING", message, orderId, sessionId)
        }
        
        @JvmStatic
        fun logHeartbeat(message: Message, sessionId: String, direction: String) {
            getInstance()?.logHeartbeat(message, sessionId, direction)
                ?: logHeartbeatWithoutMetrics(message, sessionId, direction)
        }
        
        @JvmStatic
        fun logAdminMessage(message: Message, sessionId: String, direction: String, description: String? = null) {
            getInstance()?.logAdminMessage(message, sessionId, direction, description)
                ?: logAdminWithoutMetrics(message, sessionId, direction, description)
        }
        
        @JvmStatic
        fun logUnknownFixMessage(message: Message, sessionId: String, reason: String) {
            getInstance()?.logUnknownFixMessage(message, sessionId, reason)
                ?: logUnknownWithoutMetrics(message, sessionId, reason)
        }
        
        private fun logWithoutMetrics(type: String, message: Message, orderId: String?, sessionId: String?) {
            val log = LoggerFactory.getLogger(FixMessageLogger::class.java)
            try {
                val fixString = message.toString()
                val pipeFormatted = formatFixMessageWithPipes(fixString)
                val msgType = extractMessageType(message)
                val msgSeqNum = extractSeqNum(message)
                
                log.info("{}: orderId={} sessionId={} msgType={} seqNum={} message={}",
                    type, orderId ?: "unknown", sessionId ?: "unknown", msgType, msgSeqNum, pipeFormatted)
            } catch (e: Exception) {
                log.error("Error logging FIX message: {}", e.message, e)
            }
        }
        
        private fun logHeartbeatWithoutMetrics(message: Message, sessionId: String, direction: String) {
            val log = LoggerFactory.getLogger(FixMessageLogger::class.java)
            try {
                val fixString = message.toString()
                val pipeFormatted = formatFixMessageWithPipes(fixString)
                val msgSeqNum = extractSeqNum(message)
                
                log.debug("FIX_HEARTBEAT: direction={} sessionId={} seqNum={} message={}",
                    direction, sessionId, msgSeqNum, pipeFormatted)
            } catch (e: Exception) {
                log.error("Error logging heartbeat: {}", e.message, e)
            }
        }
        
        private fun logAdminWithoutMetrics(message: Message, sessionId: String, direction: String, description: String?) {
            val log = LoggerFactory.getLogger(FixMessageLogger::class.java)
            try {
                val fixString = message.toString()
                val pipeFormatted = formatFixMessageWithPipes(fixString)
                val msgType = extractMessageType(message)
                val msgSeqNum = extractSeqNum(message)
                
                log.info("FIX_ADMIN: direction={} sessionId={} msgType={} seqNum={} description={} message={}",
                    direction, sessionId, msgType, msgSeqNum, description ?: getAdminMessageDescription(msgType), pipeFormatted)
            } catch (e: Exception) {
                log.error("Error logging admin message: {}", e.message, e)
            }
        }
        
        private fun logUnknownWithoutMetrics(message: Message, sessionId: String, reason: String) {
            val log = LoggerFactory.getLogger(FixMessageLogger::class.java)
            try {
                val fixString = message.toString()
                val pipeFormatted = formatFixMessageWithPipes(fixString)
                val msgType = extractMessageType(message)
                val msgSeqNum = extractSeqNum(message)
                
                log.error("FIX_UNKNOWN_MESSAGE: sessionId={} msgType={} seqNum={} reason={} message={}",
                    sessionId, msgType, msgSeqNum, reason, pipeFormatted)
            } catch (e: Exception) {
                log.error("Error logging unknown message: {}", e.message, e)
            }
        }
    }
    
    /**
     * Logs outgoing FIX messages with pipe formatting
     */
    fun logOutgoingFixMessage(message: Message, orderId: String? = null, sessionId: String? = null) {
        try {
            val fixString = message.toString()
            val pipeFormatted = formatFixMessageWithPipes(fixString)
            val msgType = extractMessageType(message)
            val msgSeqNum = extractSeqNum(message)
            
            // Update metrics
            fixMessageMetrics.incrementFixOutgoing(msgType)
            
            log.info("FIX_OUTGOING: orderId={} sessionId={} msgType={} seqNum={} message={}",
                orderId ?: "unknown",
                sessionId ?: "unknown", 
                msgType,
                msgSeqNum,
                pipeFormatted)
                
            // Log specific fields for order messages
            if (isOrderMessage(msgType)) {
                logOrderSpecificFields(message, orderId, "OUTGOING")
            }
            
        } catch (e: Exception) {
            log.error("ERROR_LOGGING_OUTGOING_FIX: orderId={} error={}", orderId, e.message, e)
        }
    }
    
    /**
     * Logs incoming FIX messages with pipe formatting
     */
    fun logIncomingFixMessage(message: Message, orderId: String? = null, sessionId: String? = null) {
        try {
            val fixString = message.toString()
            val pipeFormatted = formatFixMessageWithPipes(fixString)
            val msgType = extractMessageType(message)
            val msgSeqNum = extractSeqNum(message)
            
            // Update metrics
            fixMessageMetrics.incrementFixIncoming(msgType)
            
            log.info("FIX_INCOMING: orderId={} sessionId={} msgType={} seqNum={} message={}",
                orderId ?: "unknown",
                sessionId ?: "unknown",
                msgType,
                msgSeqNum,
                pipeFormatted)
                
            // Log specific fields for execution reports
            if (isExecutionReport(msgType)) {
                logExecutionReportFields(message, orderId)
            } else if (isOrderMessage(msgType)) {
                logOrderSpecificFields(message, orderId, "INCOMING")
            }
            
        } catch (e: Exception) {
            log.error("ERROR_LOGGING_INCOMING_FIX: orderId={} error={}", orderId, e.message, e)
        }
    }
    
    /**
     * Logs heartbeat messages
     */
    fun logHeartbeat(message: Message, sessionId: String, direction: String) {
        try {
            val fixString = message.toString()
            val pipeFormatted = formatFixMessageWithPipes(fixString)
            val msgSeqNum = extractSeqNum(message)
            
            // Update metrics based on direction
            when (direction.uppercase()) {
                "INCOMING" -> fixMessageMetrics.incrementFixHeartbeatIncoming()
                "OUTGOING" -> fixMessageMetrics.incrementFixHeartbeatOutgoing()
            }
            
            log.debug("FIX_HEARTBEAT: direction={} sessionId={} seqNum={} message={}",
                direction,
                sessionId,
                msgSeqNum,
                pipeFormatted)
                
        } catch (e: Exception) {
            fixMessageMetrics.incrementFixError("heartbeat_logging")
            log.error("ERROR_LOGGING_HEARTBEAT: sessionId={} direction={} error={}", sessionId, direction, e.message, e)
        }
    }
    
    /**
     * Logs admin messages (logon, logout, test request, etc.)
     */
    fun logAdminMessage(message: Message, sessionId: String, direction: String, description: String? = null) {
        try {
            val fixString = message.toString()
            val pipeFormatted = formatFixMessageWithPipes(fixString)
            val msgType = extractMessageType(message)
            val msgSeqNum = extractSeqNum(message)
            
            // Update metrics based on direction
            when (direction.uppercase()) {
                "INCOMING" -> fixMessageMetrics.incrementFixAdminIncoming(msgType)
                "OUTGOING" -> fixMessageMetrics.incrementFixAdminOutgoing(msgType)
            }
            
            log.info("FIX_ADMIN: direction={} sessionId={} msgType={} seqNum={} description={} message={}",
                direction,
                sessionId,
                msgType,
                msgSeqNum,
                description ?: getAdminMessageDescription(msgType),
                pipeFormatted)
                
        } catch (e: Exception) {
            fixMessageMetrics.incrementFixError("admin_logging")
            log.error("ERROR_LOGGING_ADMIN_FIX: sessionId={} direction={} error={}", sessionId, direction, e.message, e)
        }
    }
    
    /**
     * Logs unknown/unhandled FIX messages as errors
     */
    fun logUnknownFixMessage(message: Message, sessionId: String, reason: String) {
        try {
            val fixString = message.toString()
            val pipeFormatted = formatFixMessageWithPipes(fixString)
            val msgType = extractMessageType(message)
            val msgSeqNum = extractSeqNum(message)
            
            // Increment unknown message counter as requested
            fixMessageMetrics.incrementFixUnknownMessage(msgType, reason)
            
            log.error("FIX_UNKNOWN_MESSAGE: sessionId={} msgType={} seqNum={} reason={} message={}",
                sessionId,
                msgType,
                msgSeqNum,
                reason,
                pipeFormatted)
                
        } catch (e: Exception) {
            fixMessageMetrics.incrementFixError("unknown_message_logging")
            log.error("ERROR_LOGGING_UNKNOWN_FIX: sessionId={} reason={} error={}", sessionId, reason, e.message, e)
        }
    }
    
    /**
     * Converts FIX message to pipe-delimited format for better readability
     */
    private fun formatFixMessageWithPipes(fixString: String): String {
        return fixString.replace('\u0001', '|') // Replace SOH with pipe
    }
    
    private fun extractMessageType(message: Message): String {
        return try {
            message.header.getString(MsgType.FIELD)
        } catch (e: Exception) {
            "UNKNOWN"
        }
    }
    
    private fun extractSeqNum(message: Message): String {
        return try {
            message.header.getString(MsgSeqNum.FIELD)
        } catch (e: Exception) {
            "UNKNOWN"
        }
    }
    
    private fun isOrderMessage(msgType: String): Boolean {
        return msgType in setOf("D", "F", "G") // NewOrderSingle, OrderCancelRequest, OrderCancelReplaceRequest
    }
    
    private fun isExecutionReport(msgType: String): Boolean {
        return msgType == "8" // ExecutionReport
    }
    
    private fun isHeartbeat(msgType: String): Boolean {
        return msgType == "0" // Heartbeat
    }
    
    private fun getAdminMessageDescription(msgType: String): String {
        return when (msgType) {
            "A" -> "LOGON"
            "5" -> "LOGOUT"
            "0" -> "HEARTBEAT"
            "1" -> "TEST_REQUEST"
            "2" -> "RESEND_REQUEST"
            "4" -> "SEQUENCE_RESET"
            "3" -> "REJECT"
            else -> "ADMIN_MESSAGE"
        }
    }
    
    private fun logOrderSpecificFields(message: Message, orderId: String?, direction: String) {
        try {
            val clOrdId = try { message.getString(ClOrdID.FIELD) } catch (e: Exception) { "N/A" }
            val symbol = try { message.getString(Symbol.FIELD) } catch (e: Exception) { "N/A" }
            val side = try { message.getChar(Side.FIELD).toString() } catch (e: Exception) { "N/A" }
            val orderQty = try { message.getDouble(OrderQty.FIELD).toString() } catch (e: Exception) { "N/A" }
            val price = try { message.getDouble(Price.FIELD).toString() } catch (e: Exception) { "N/A" }
            val ordType = try { message.getChar(OrdType.FIELD).toString() } catch (e: Exception) { "N/A" }
            
            log.info("FIX_ORDER_DETAILS: direction={} orderId={} clOrdId={} symbol={} side={} qty={} price={} ordType={}",
                direction, orderId ?: clOrdId, clOrdId, symbol, side, orderQty, price, ordType)
                
        } catch (e: Exception) {
            log.debug("Could not extract order details: {}", e.message)
        }
    }
    
    private fun logExecutionReportFields(message: Message, orderId: String?) {
        try {
            val clOrdId = try { message.getString(ClOrdID.FIELD) } catch (e: Exception) { "N/A" }
            val execId = try { message.getString(ExecID.FIELD) } catch (e: Exception) { "N/A" }
            val execType = try { message.getChar(ExecType.FIELD).toString() } catch (e: Exception) { "N/A" }
            val ordStatus = try { message.getChar(OrdStatus.FIELD).toString() } catch (e: Exception) { "N/A" }
            val lastQty = try { message.getDouble(LastQty.FIELD).toString() } catch (e: Exception) { "N/A" }
            val cumQty = try { message.getDouble(CumQty.FIELD).toString() } catch (e: Exception) { "N/A" }
            val avgPx = try { message.getDouble(AvgPx.FIELD).toString() } catch (e: Exception) { "N/A" }
            
            log.info("FIX_EXECUTION_DETAILS: orderId={} clOrdId={} execId={} execType={} ordStatus={} lastQty={} cumQty={} avgPx={}",
                orderId ?: clOrdId, clOrdId, execId, execType, ordStatus, lastQty, cumQty, avgPx)
                
        } catch (e: Exception) {
            log.debug("Could not extract execution report details: {}", e.message)
        }
    }
}