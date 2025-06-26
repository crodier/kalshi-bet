package com.betfanatics.exchange.order.service

import com.betfanatics.exchange.order.util.FixClOrdIdGenerator
import com.betfanatics.exchange.order.actor.common.OrderRequestDTO
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Service
import java.time.Duration

@Service
class ClOrdIdMappingService(
    private val redisTemplate: RedisTemplate<String, String>,
    private val fixClOrdIdGenerator: FixClOrdIdGenerator,
    private val objectMapper: ObjectMapper
) {
    
    private val log = LoggerFactory.getLogger(ClOrdIdMappingService::class.java)
    
    companion object {
        private const val CLORDID_PREFIX = "fix-orders/clOrdId/"
        private const val ORDERID_PREFIX = "fix-orders/orderId/"
        private const val ORDER_DATA_PREFIX = "fix-orders/orderData/"
        private const val CANCEL_PREFIX = "fix-orders/cancel/"
        private const val MODIFY_PREFIX = "fix-orders/modify/"
        private const val LATEST_MODIFY_ACCEPTED_PREFIX = "fix-orders/latestModifyAccepted/"
        private const val LATEST_CANCEL_ACCEPTED_PREFIX = "fix-orders/latestCancelAccepted/"
        // Keep mappings for 30 days (orders should be settled long before this)
        private val MAPPING_TTL = Duration.ofDays(30)
    }
    
    /**
     * Generate a new ClOrdID for an order and store the mapping with order data
     * This provides idempotency - if called again with same betOrderId, returns the same ClOrdID
     * 
     * @param betOrderId The internal bet order ID
     * @param orderRequest The complete order request data to store
     * @return The ClOrdID (either newly generated or existing)
     */
    fun generateAndStoreClOrdId(betOrderId: String, orderRequest: OrderRequestDTO? = null): String {
        // Check if we already have a ClOrdID for this betOrderId
        val existingClOrdId = getClOrdIdByOrderId(betOrderId)
        if (existingClOrdId != null) {
            log.info("Found existing ClOrdID {} for betOrderId {}", existingClOrdId, betOrderId)
            return existingClOrdId
        }
        
        // Generate new ClOrdID
        val clOrdId = fixClOrdIdGenerator.generateClOrdId(betOrderId)
        
        // Store mappings and order data atomically using Redis pipeline
        redisTemplate.executePipelined { connection ->
            val ops = redisTemplate.opsForValue()
            
            // Store clOrdId -> betOrderId mapping
            ops.set("$CLORDID_PREFIX$clOrdId", betOrderId, MAPPING_TTL)
            
            // Store betOrderId -> clOrdId mapping
            ops.set("$ORDERID_PREFIX$betOrderId", clOrdId, MAPPING_TTL)
            
            // Store the complete order data if provided
            if (orderRequest != null) {
                val orderJson = objectMapper.writeValueAsString(orderRequest)
                ops.set("$ORDER_DATA_PREFIX$betOrderId", orderJson, MAPPING_TTL)
            }
            
            null // executePipelined requires null return
        }
        
        log.info("Generated and stored new ClOrdID {} for betOrderId {} with order data", clOrdId, betOrderId)
        return clOrdId
    }
    
    /**
     * Get the betOrderId associated with a ClOrdID
     * Used when processing execution reports from FIX
     * 
     * For new orders, the ClOrdID IS the betOrderId (with underscores)
     * For modify/cancel orders, we need to extract the betOrderId from the ClOrdID
     */
    fun getOrderIdByClOrdId(clOrdId: String): String? {
        // First try direct lookup (for backwards compatibility or if we stored it)
        val directLookup = redisTemplate.opsForValue().get("$CLORDID_PREFIX$clOrdId")
        if (directLookup != null) {
            return directLookup
        }
        
        // Extract the betOrderId from the ClOrdID
        // For new orders, this just converts underscores back to hyphens
        // For modify/cancel, this extracts the betOrderId portion
        return fixClOrdIdGenerator.extractOrderId(clOrdId)
    }
    
    /**
     * Get the ClOrdID associated with a betOrderId
     * Used for idempotency checking
     */
    fun getClOrdIdByOrderId(betOrderId: String): String? {
        return redisTemplate.opsForValue().get("$ORDERID_PREFIX$betOrderId")
    }
    
    /**
     * Check if a betOrderId already has a ClOrdID mapping
     * Used for idempotency checking
     */
    fun hasClOrdId(betOrderId: String): Boolean {
        return redisTemplate.hasKey("$ORDERID_PREFIX$betOrderId")
    }
    
    /**
     * Get the original order data for a betOrderId
     * Used when creating execution reports
     */
    fun getOrderData(betOrderId: String): OrderRequestDTO? {
        val orderJson = redisTemplate.opsForValue().get("$ORDER_DATA_PREFIX$betOrderId")
        return if (orderJson != null) {
            try {
                objectMapper.readValue(orderJson, OrderRequestDTO::class.java)
            } catch (e: Exception) {
                log.error("Failed to deserialize order data for betOrderId {}: {}", betOrderId, e.message)
                null
            }
        } else {
            null
        }
    }
    
    /**
     * Store order data for a betOrderId
     * Can be called separately if order data wasn't available during ClOrdID generation
     */
    fun storeOrderData(betOrderId: String, orderRequest: OrderRequestDTO) {
        try {
            val orderJson = objectMapper.writeValueAsString(orderRequest)
            redisTemplate.opsForValue().set("$ORDER_DATA_PREFIX$betOrderId", orderJson, MAPPING_TTL)
            log.debug("Stored order data for betOrderId {}", betOrderId)
        } catch (e: Exception) {
            log.error("Failed to store order data for betOrderId {}: {}", betOrderId, e.message)
        }
    }
    
    /**
     * Delete mappings for an order (all data)
     * Used for cleanup after order completion/cancellation if needed
     */
    fun deleteMappings(betOrderId: String) {
        val clOrdId = getClOrdIdByOrderId(betOrderId)
        if (clOrdId != null) {
            redisTemplate.delete(listOf(
                "$CLORDID_PREFIX$clOrdId",
                "$ORDERID_PREFIX$betOrderId",
                "$ORDER_DATA_PREFIX$betOrderId"
            ))
            log.info("Deleted all mappings for betOrderId {} (ClOrdID: {})", betOrderId, clOrdId)
        }
    }
    
    /**
     * Extend the TTL for an order's mappings
     * Used when an order is still active after a long time
     */
    fun extendMappingTTL(betOrderId: String) {
        val clOrdId = getClOrdIdByOrderId(betOrderId)
        if (clOrdId != null) {
            redisTemplate.expire("$CLORDID_PREFIX$clOrdId", MAPPING_TTL)
            redisTemplate.expire("$ORDERID_PREFIX$betOrderId", MAPPING_TTL)
            redisTemplate.expire("$ORDER_DATA_PREFIX$betOrderId", MAPPING_TTL)
            log.debug("Extended TTL for betOrderId {} (ClOrdID: {})", betOrderId, clOrdId)
        }
    }
    
    /**
     * Generate and store a cancel ClOrdID
     * Also determines the OrigClOrdID (tag 41) to use based on latest accepted modify or original order
     * 
     * @param betOrderId The internal bet order ID
     * @return Pair of (cancelClOrdId, origClOrdId for tag 41)
     */
    fun generateAndStoreCancelClOrdId(betOrderId: String): Pair<String, String> {
        // Generate new cancel ClOrdID
        val cancelClOrdId = fixClOrdIdGenerator.generateCancelClOrdId(betOrderId)
        
        // Determine OrigClOrdID (tag 41) - what the exchange knows about
        val origClOrdId = getLatestAcceptedModifyClOrdId(betOrderId) 
            ?: getClOrdIdByOrderId(betOrderId) 
            ?: throw IllegalStateException("No ClOrdID found for betOrderId: $betOrderId")
        
        // Store the cancel ClOrdID
        redisTemplate.executePipelined { connection ->
            val ops = redisTemplate.opsForValue()
            
            // Store cancel ClOrdID mapping
            ops.set("$CANCEL_PREFIX$betOrderId", cancelClOrdId, MAPPING_TTL)
            
            // Store reverse mapping for cancel ClOrdID
            ops.set("$CLORDID_PREFIX$cancelClOrdId", betOrderId, MAPPING_TTL)
            
            null
        }
        
        log.info("Generated cancel ClOrdID {} for betOrderId {}, using OrigClOrdID {} (tag 41)", 
            cancelClOrdId, betOrderId, origClOrdId)
        
        return Pair(cancelClOrdId, origClOrdId)
    }
    
    /**
     * Generate and store a modify ClOrdID
     * 
     * @param betOrderId The internal bet order ID  
     * @return Pair of (modifyClOrdId, origClOrdId for tag 41)
     */
    fun generateAndStoreModifyClOrdId(betOrderId: String): Pair<String, String> {
        // Generate new modify ClOrdID
        val modifyClOrdId = fixClOrdIdGenerator.generateModifyClOrdId(betOrderId)
        
        // Determine OrigClOrdID (tag 41) - what the exchange knows about
        val origClOrdId = getLatestAcceptedModifyClOrdId(betOrderId)
            ?: getClOrdIdByOrderId(betOrderId)
            ?: throw IllegalStateException("No ClOrdID found for betOrderId: $betOrderId")
        
        // Store the modify ClOrdID
        redisTemplate.executePipelined { connection ->
            val ops = redisTemplate.opsForValue()
            
            // Store modify ClOrdID mapping
            ops.set("$MODIFY_PREFIX$betOrderId", modifyClOrdId, MAPPING_TTL)
            
            // Store reverse mapping for modify ClOrdID
            ops.set("$CLORDID_PREFIX$modifyClOrdId", betOrderId, MAPPING_TTL)
            
            null
        }
        
        log.info("Generated modify ClOrdID {} for betOrderId {}, using OrigClOrdID {} (tag 41)",
            modifyClOrdId, betOrderId, origClOrdId)
        
        return Pair(modifyClOrdId, origClOrdId)
    }
    
    /**
     * Update the latest accepted modify ClOrdID when we receive an execution report
     * accepting a modify order
     */
    fun updateLatestAcceptedModifyClOrdId(betOrderId: String, modifyClOrdId: String) {
        redisTemplate.opsForValue().set("$LATEST_MODIFY_ACCEPTED_PREFIX$betOrderId", modifyClOrdId, MAPPING_TTL)
        log.info("Updated latest accepted modify ClOrdID for betOrderId {} to {}", betOrderId, modifyClOrdId)
    }
    
    /**
     * Get the latest accepted modify ClOrdID for an order
     */
    fun getLatestAcceptedModifyClOrdId(betOrderId: String): String? {
        return redisTemplate.opsForValue().get("$LATEST_MODIFY_ACCEPTED_PREFIX$betOrderId")
    }
    
    /**
     * Update the latest accepted cancel ClOrdID when we receive an execution report
     * accepting a cancel order
     */
    fun updateLatestAcceptedCancelClOrdId(betOrderId: String, cancelClOrdId: String) {
        redisTemplate.opsForValue().set("$LATEST_CANCEL_ACCEPTED_PREFIX$betOrderId", cancelClOrdId, MAPPING_TTL)
        log.info("Updated latest accepted cancel ClOrdID for betOrderId {} to {}", betOrderId, cancelClOrdId)
    }
    
    /**
     * Get the latest accepted cancel ClOrdID for an order
     */
    fun getLatestAcceptedCancelClOrdId(betOrderId: String): String? {
        return redisTemplate.opsForValue().get("$LATEST_CANCEL_ACCEPTED_PREFIX$betOrderId")
    }
}