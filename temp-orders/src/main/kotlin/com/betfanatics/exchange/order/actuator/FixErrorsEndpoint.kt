package com.betfanatics.exchange.order.actuator

import com.betfanatics.exchange.order.service.FixErrorService
import org.springframework.boot.actuate.endpoint.annotation.*
import org.springframework.stereotype.Component

@Component
@Endpoint(id = "fixerrors")
class FixErrorsEndpoint(
    private val fixErrorService: FixErrorService
) {
    
    @ReadOperation
    fun getFixErrors(@Selector limit: Int?): Map<String, Any> {
        val actualLimit = limit ?: 10
        return mapOf(
            "errorCounts" to fixErrorService.getErrorCounts(),
            "recentErrors" to fixErrorService.getRecentErrors(actualLimit),
            "limit" to actualLimit
        )
    }
    
    @ReadOperation
    fun getErrorsByType(@Selector errorType: String): Map<String, Any> {
        val allErrors = fixErrorService.getRecentErrors(100)
        val filteredErrors = allErrors.filter { it.errorType == errorType }
        
        return mapOf(
            "errorType" to errorType,
            "count" to filteredErrors.size,
            "errors" to filteredErrors
        )
    }
    
    @WriteOperation
    fun clearErrors(): Map<String, String> {
        fixErrorService.clearErrors()
        return mapOf("status" to "All FIX errors cleared")
    }
    
    @ReadOperation
    fun getErrorSummary(): Map<String, Any> {
        val errorCounts = fixErrorService.getErrorCounts()
        val totalErrors = errorCounts.values.sum()
        val recentErrors = fixErrorService.getRecentErrors(5)
        
        return mapOf(
            "totalErrors" to totalErrors,
            "errorTypes" to errorCounts,
            "mostRecentErrors" to recentErrors.map { error ->
                mapOf(
                    "timestamp" to error.timestamp,
                    "type" to error.errorType,
                    "message" to error.message.take(100) + if (error.message.length > 100) "..." else ""
                )
            }
        )
    }
}