package com.betfanatics.exchange.order.service

import com.betfanatics.exchange.order.config.KafkaTopicsConfig
import com.betfanatics.exchange.order.messaging.KafkaSender
import com.betfanatics.exchange.order.metrics.FixMessageMetrics
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

@Service
class FixErrorService(
    private val kafkaSender: KafkaSender,
    private val kafkaTopicsConfig: KafkaTopicsConfig,
    private val fixMessageMetrics: FixMessageMetrics
) {
    
    private val log = LoggerFactory.getLogger(FixErrorService::class.java)
    
    // Error tracking
    private val errorCounts = ConcurrentHashMap<String, AtomicLong>()
    private val recentErrors = mutableListOf<FixError>()
    private val maxRecentErrors = 100
    
    // Track the most recent connection error for republishing
    @Volatile
    private var lastConnectionError: FixError? = null
    
    data class FixError(
        val timestamp: Instant,
        val errorType: String,
        val message: String,
        val sessionId: String?,
        val details: Map<String, Any>
    )
    
    fun reportConnectionError(sessionId: String?, error: String, details: Map<String, Any> = emptyMap()) {
        log.error("FIX_CONNECTION_ERROR: sessionId={} error={} details={}", sessionId, error, details)
        
        val errorType = "CONNECTION_ERROR"
        incrementErrorCount(errorType)
        fixMessageMetrics.incrementFixError(errorType)
        
        val fixError = FixError(
            timestamp = Instant.now(),
            errorType = errorType,
            message = error,
            sessionId = sessionId,
            details = details
        )
        
        addRecentError(fixError)
        lastConnectionError = fixError
        publishErrorToKafka(fixError)
    }
    
    fun reportAuthenticationError(sessionId: String?, error: String, details: Map<String, Any> = emptyMap()) {
        log.error("FIX_AUTHENTICATION_ERROR: sessionId={} error={} details={}", sessionId, error, details)
        
        val errorType = "AUTHENTICATION_ERROR"
        incrementErrorCount(errorType)
        fixMessageMetrics.incrementFixError(errorType)
        
        val fixError = FixError(
            timestamp = Instant.now(),
            errorType = errorType,
            message = error,
            sessionId = sessionId,
            details = details
        )
        
        addRecentError(fixError)
        publishErrorToKafka(fixError)
    }
    
    fun reportSequenceError(sessionId: String?, error: String, expectedSeq: Int?, receivedSeq: Int?) {
        log.error("FIX_SEQUENCE_ERROR: sessionId={} error={} expectedSeq={} receivedSeq={}", 
            sessionId, error, expectedSeq, receivedSeq)
        
        val errorType = "SEQUENCE_ERROR"
        incrementErrorCount(errorType)
        fixMessageMetrics.incrementFixError(errorType)
        
        val details = mutableMapOf<String, Any>()
        expectedSeq?.let { details["expectedSeq"] = it }
        receivedSeq?.let { details["receivedSeq"] = it }
        
        val fixError = FixError(
            timestamp = Instant.now(),
            errorType = errorType,
            message = error,
            sessionId = sessionId,
            details = details
        )
        
        addRecentError(fixError)
        publishErrorToKafka(fixError)
    }
    
    fun reportMessageProcessingError(sessionId: String?, error: String, msgType: String?, details: Map<String, Any> = emptyMap()) {
        log.error("FIX_MESSAGE_PROCESSING_ERROR: sessionId={} error={} msgType={} details={}", 
            sessionId, error, msgType, details)
        
        val errorType = "MESSAGE_PROCESSING_ERROR"
        incrementErrorCount(errorType)
        fixMessageMetrics.incrementFixError(errorType)
        
        val allDetails = details.toMutableMap()
        msgType?.let { allDetails["msgType"] = it }
        
        val fixError = FixError(
            timestamp = Instant.now(),
            errorType = errorType,
            message = error,
            sessionId = sessionId,
            details = allDetails
        )
        
        addRecentError(fixError)
        publishErrorToKafka(fixError)
    }
    
    fun reportGeneralError(sessionId: String?, error: String, details: Map<String, Any> = emptyMap()) {
        log.error("FIX_GENERAL_ERROR: sessionId={} error={} details={}", sessionId, error, details)
        
        val errorType = "GENERAL_ERROR"
        incrementErrorCount(errorType)
        fixMessageMetrics.incrementFixError(errorType)
        
        val fixError = FixError(
            timestamp = Instant.now(),
            errorType = errorType,
            message = error,
            sessionId = sessionId,
            details = details
        )
        
        addRecentError(fixError)
        publishErrorToKafka(fixError)
    }
    
    fun getErrorCounts(): Map<String, Long> {
        return errorCounts.mapValues { it.value.get() }
    }
    
    fun getRecentErrors(limit: Int = 10): List<FixError> {
        synchronized(recentErrors) {
            return recentErrors.takeLast(limit).reversed()
        }
    }
    
    fun clearErrors() {
        synchronized(recentErrors) {
            recentErrors.clear()
        }
        errorCounts.clear()
        lastConnectionError = null
    }
    
    private fun incrementErrorCount(errorType: String) {
        errorCounts.computeIfAbsent(errorType) { AtomicLong() }.incrementAndGet()
    }
    
    private fun addRecentError(error: FixError) {
        synchronized(recentErrors) {
            recentErrors.add(error)
            if (recentErrors.size > maxRecentErrors) {
                recentErrors.removeAt(0)
            }
        }
    }
    
    private fun publishErrorToKafka(error: FixError) {
        val errorMessage = mapOf(
            "timestamp" to error.timestamp.toString(),
            "errorType" to error.errorType,
            "message" to error.message,
            "sessionId" to (error.sessionId ?: ""),
            "details" to error.details
        )
        
        val key = "${error.errorType}_${error.timestamp.toEpochMilli()}"
        
        kafkaSender.send(kafkaTopicsConfig.fixErrorTopic.name, key, errorMessage)
            .thenAccept { result ->
                if (result.isSuccess) {
                    log.debug("FIX error published to Kafka: errorType={} key={}", error.errorType, key)
                } else {
                    log.error("Failed to publish FIX error to Kafka: errorType={} error={}", 
                        error.errorType, result.errorMessage)
                }
            }
            .exceptionally { throwable ->
                log.error("Exception publishing FIX error to Kafka: errorType={} error={}", 
                    error.errorType, throwable.message, throwable)
                null
            }
    }
    
    fun getLastConnectionError(): FixError? = lastConnectionError
    
    fun republishConnectionError() {
        lastConnectionError?.let { error ->
            // Create a new error with updated timestamp and republish count
            val republishCount = (error.details["republishCount"] as? Int ?: 0) + 1
            val updatedDetails = error.details.toMutableMap()
            updatedDetails["republishCount"] = republishCount
            updatedDetails["originalTimestamp"] = error.timestamp.toString()
            
            val republishedError = error.copy(
                timestamp = Instant.now(),
                details = updatedDetails
            )
            
            publishErrorToKafka(republishedError)
            log.info("Republished connection error to Kafka: count={}", republishCount)
        }
    }
}