package com.betfanatics.exchange.order.util

import org.springframework.stereotype.Component
import java.util.concurrent.atomic.AtomicLong

@Component
class FixClOrdIdGenerator {
    
    companion object {
        private const val PREFIX = "FBG"
    }
    
    // Atomic counters for modify and cancel operations
    private val modifyCounter = AtomicLong(System.currentTimeMillis())
    private val cancelCounter = AtomicLong(System.currentTimeMillis())
    
    /**
     * Called when FIX session connects/reconnects
     * This resets the counters for a new session
     */
    fun onSessionStart() {
        // Reset counters to current time on session start
        val now = System.currentTimeMillis()
        modifyCounter.set(now)
        cancelCounter.set(now)
    }
    
    /**
     * Generate a ClOrdID for a new order
     * Format: FBG_<betOrderId> (NO SEQUENCE)
     * 
     * IMPORTANT DESIGN DECISION:
     * - New orders use FBG_ prefix + betOrderId (NO SEQUENCE NUMBER)
     * - This ensures that if the same betOrderId is sent twice, the exchange will reject as duplicate
     * - This provides natural idempotency at the FIX protocol level
     * - We also check Redis for duplicates as an additional safety measure
     * 
     * @param betOrderId The internal order ID from our system
     * @return FBG_<betOrderId> with hyphens replaced by underscores
     */
    fun generateClOrdId(betOrderId: String): String {
        // Replace hyphens with underscores for FIX compatibility
        val sanitizedOrderId = betOrderId.replace("-", "_")
        
        // Format: FBG_<betOrderId> - NO sequence number
        return "${PREFIX}_${sanitizedOrderId}"
    }
    
    /**
     * Generate a unique ClOrdID for a modify order request
     * Format: FBG_<betOrderId>_M_<timestamp>
     * 
     * @param betOrderId The internal order ID from our system
     * @return A unique ClOrdID for modify request
     */
    fun generateModifyClOrdId(betOrderId: String): String {
        val sanitizedOrderId = betOrderId.replace("-", "_")
        val timestamp = modifyCounter.incrementAndGet()
        
        return "${PREFIX}_${sanitizedOrderId}_M_${timestamp}"
    }
    
    /**
     * Generate a unique ClOrdID for a cancel order request
     * Format: FBG_<betOrderId>_C_<timestamp>
     * 
     * @param betOrderId The internal order ID from our system
     * @return A unique ClOrdID for cancel request
     */
    fun generateCancelClOrdId(betOrderId: String): String {
        val sanitizedOrderId = betOrderId.replace("-", "_")
        val timestamp = cancelCounter.incrementAndGet()
        
        return "${PREFIX}_${sanitizedOrderId}_C_${timestamp}"
    }
    
    /**
     * Extract the original betOrderId from a ClOrdID
     * This handles new, modify, and cancel ClOrdIDs
     * 
     * @param clOrdId The ClOrdID to parse
     * @return The betOrderId (with underscores replaced back to hyphens)
     */
    fun extractOrderId(clOrdId: String): String? {
        val parts = clOrdId.split("_")
        
        return when {
            // Modify format: FBG_<betOrderId>_M_<timestamp>
            parts.size >= 4 && parts[0] == PREFIX && parts[parts.size - 2] == "M" -> {
                // Join all parts between FBG and the M marker
                parts.subList(1, parts.size - 2).joinToString("_").replace("_", "-")
            }
            // Cancel format: FBG_<betOrderId>_C_<timestamp>
            parts.size >= 4 && parts[0] == PREFIX && parts[parts.size - 2] == "C" -> {
                // Join all parts between FBG and the C marker
                parts.subList(1, parts.size - 2).joinToString("_").replace("_", "-")
            }
            // New order format: FBG_<betOrderId>
            parts.size >= 2 && parts[0] == PREFIX -> {
                // Join all parts after FBG (in case betOrderId has underscores)
                parts.drop(1).joinToString("_").replace("_", "-")
            }
            else -> null
        }
    }
    
    /**
     * Check if a ClOrdID is for a modify request
     */
    fun isModifyClOrdId(clOrdId: String): Boolean {
        val parts = clOrdId.split("_")
        return parts.size >= 4 && parts[0] == PREFIX && parts[parts.size - 2] == "M"
    }
    
    /**
     * Check if a ClOrdID is for a cancel request
     */
    fun isCancelClOrdId(clOrdId: String): Boolean {
        val parts = clOrdId.split("_")
        return parts.size >= 4 && parts[0] == PREFIX && parts[parts.size - 2] == "C"
    }
    
    /**
     * Check if a ClOrdID is for a new order (has FBG prefix but no M/C suffix)
     */
    fun isNewOrderClOrdId(clOrdId: String): Boolean {
        val parts = clOrdId.split("_")
        return parts.size >= 2 && parts[0] == PREFIX && !isModifyClOrdId(clOrdId) && !isCancelClOrdId(clOrdId)
    }
    
    /**
     * Get the current session prefix for logging
     */
    fun getCurrentSessionPrefix(): String {
        return PREFIX
    }
}