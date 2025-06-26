package com.fbg.api.kalshi

import kotlinx.serialization.Serializable

/**
 * Internal Order Book data structure with timestamp tracking at every level
 * 
 * This is the comprehensive internal representation that tracks:
 * 1. Overall order book last update time
 * 2. Individual timestamp for every price level showing when it was last modified
 * 3. Both YES and NO sides with complete price/quantity/timestamp information
 * 
 * This structure is used internally for admin monitoring and real-time tracking
 * of exactly when each price level changed, providing granular insight into
 * market data flow and processing latency.
 */
@Serializable
data class InternalOrderBook(
    val marketTicker: String,
    val lastUpdateTimestamp: Long,        // Overall order book last update time (epoch millis)
    val lastTimeInternalBookUpdated: Long, // When we last updated our internal book (epoch millis)
    val sequenceNumber: Long?,            // Sequence number for ordering
    val receivedTimestamp: Long,          // When the raw message was received from Kalshi
    val processedTimestamp: Long,         // When we finished processing this update
    val yesSide: OrderBookSide,           // YES (Buy YES) orders
    val noSide: OrderBookSide             // NO (Buy NO) orders
) {
    
    /**
     * Calculate the processing latency in milliseconds
     */
    fun getProcessingLatency(): Long = processedTimestamp - receivedTimestamp
    
    /**
     * Get the most recent timestamp from any price level
     */
    fun getMostRecentLevelUpdate(): Long {
        val allTimestamps = yesSide.levels.values.map { it.lastUpdateTimestamp } +
                           noSide.levels.values.map { it.lastUpdateTimestamp }
        return allTimestamps.maxOrNull() ?: lastUpdateTimestamp
    }
    
    /**
     * Check if this order book has any data
     */
    fun isEmpty(): Boolean = yesSide.levels.isEmpty() && noSide.levels.isEmpty()
    
    /**
     * Get the best (highest) price for each side
     */
    fun getBestYesPrice(): Int? = yesSide.levels.keys.maxOrNull()
    fun getBestNoPrice(): Int? = noSide.levels.keys.maxOrNull()
    
    /**
     * Get the best price level for each side
     */
    fun getBestYesLevel(): PriceLevel? = getBestYesPrice()?.let { yesSide.levels[it] }
    fun getBestNoLevel(): PriceLevel? = getBestNoPrice()?.let { noSide.levels[it] }
}

/**
 * Represents one side of the order book (YES or NO) with timestamp tracking
 */
@Serializable
data class OrderBookSide(
    val side: String,               // "yes" or "no"
    val levels: Map<Int, PriceLevel> // price -> PriceLevel with timestamp
) {
    
    /**
     * Get all price levels sorted by price (descending for bids)
     */
    fun getSortedLevels(): List<PriceLevel> {
        return levels.values.sortedByDescending { it.price }
    }
    
    /**
     * Get the total quantity across all levels
     */
    fun getTotalQuantity(): Long {
        return levels.values.sumOf { it.quantity }
    }
    
    /**
     * Get the number of price levels
     */
    fun getLevelCount(): Int = levels.size
}

/**
 * Individual price level with complete timestamp and quantity information
 */
@Serializable
data class PriceLevel(
    val price: Int,                       // Price in cents
    val quantity: Long,                   // Total quantity at this price level
    val lastUpdateTimestamp: Long,        // When this specific price level was last updated in original data
    val internalLevelUpdatedTimestamp: Long, // When we last updated this level in our internal book
    val lastUpdateType: UpdateType,       // How this level was last updated
    val lastUpdateSequence: Long?,        // Sequence number of the last update
    val levelHistory: LevelHistory? = null // Optional: recent update history for this level
) {
    
    /**
     * Calculate how long ago this level was updated (in milliseconds)
     */
    fun getAgeMillis(): Long = System.currentTimeMillis() - lastUpdateTimestamp
    
    /**
     * Check if this level is considered "stale" (older than threshold)
     */
    fun isStale(thresholdMillis: Long = 30000): Boolean = getAgeMillis() > thresholdMillis
}

/**
 * Type of update that last modified a price level
 */
@Serializable
enum class UpdateType {
    SNAPSHOT,    // Level set via full snapshot
    DELTA_ADD,   // Level added/increased via delta
    DELTA_REMOVE,// Level removed/decreased via delta
    DELTA_MODIFY // Level quantity changed via delta
}

/**
 * Optional history tracking for a price level
 * Useful for debugging and detailed analysis
 */
@Serializable
data class LevelHistory(
    val recentUpdates: List<LevelUpdate>  // Last N updates to this level
) {
    companion object {
        const val MAX_HISTORY_SIZE = 10
    }
}

/**
 * Individual update record for level history
 */
@Serializable
data class LevelUpdate(
    val timestamp: Long,
    val updateType: UpdateType,
    val oldQuantity: Long,
    val newQuantity: Long,
    val sequenceNumber: Long?
)

/**
 * Builder for constructing InternalOrderBook instances
 */
class InternalOrderBookBuilder(
    private val marketTicker: String,
    private val receivedTimestamp: Long = System.currentTimeMillis()
) {
    private var sequenceNumber: Long? = null
    private val yesLevels = mutableMapOf<Int, PriceLevel>()
    private val noLevels = mutableMapOf<Int, PriceLevel>()
    
    fun setSequenceNumber(seq: Long): InternalOrderBookBuilder {
        this.sequenceNumber = seq
        return this
    }
    
    fun addYesLevel(price: Int, quantity: Long, updateType: UpdateType = UpdateType.SNAPSHOT): InternalOrderBookBuilder {
        val timestamp = System.currentTimeMillis()
        val internalTimestamp = System.currentTimeMillis()
        yesLevels[price] = PriceLevel(
            price = price,
            quantity = quantity,
            lastUpdateTimestamp = timestamp,
            internalLevelUpdatedTimestamp = internalTimestamp,
            lastUpdateType = updateType,
            lastUpdateSequence = sequenceNumber
        )
        return this
    }
    
    fun addYesLevel(price: Int, quantity: Long, originalTimestamp: Long, updateType: UpdateType = UpdateType.SNAPSHOT): InternalOrderBookBuilder {
        val internalTimestamp = System.currentTimeMillis()
        yesLevels[price] = PriceLevel(
            price = price,
            quantity = quantity,
            lastUpdateTimestamp = originalTimestamp,
            internalLevelUpdatedTimestamp = internalTimestamp,
            lastUpdateType = updateType,
            lastUpdateSequence = sequenceNumber
        )
        return this
    }
    
    fun addNoLevel(price: Int, quantity: Long, updateType: UpdateType = UpdateType.SNAPSHOT): InternalOrderBookBuilder {
        val timestamp = System.currentTimeMillis()
        val internalTimestamp = System.currentTimeMillis()
        noLevels[price] = PriceLevel(
            price = price,
            quantity = quantity,
            lastUpdateTimestamp = timestamp,
            internalLevelUpdatedTimestamp = internalTimestamp,
            lastUpdateType = updateType,
            lastUpdateSequence = sequenceNumber
        )
        return this
    }
    
    fun addNoLevel(price: Int, quantity: Long, originalTimestamp: Long, updateType: UpdateType = UpdateType.SNAPSHOT): InternalOrderBookBuilder {
        val internalTimestamp = System.currentTimeMillis()
        noLevels[price] = PriceLevel(
            price = price,
            quantity = quantity,
            lastUpdateTimestamp = originalTimestamp,
            internalLevelUpdatedTimestamp = internalTimestamp,
            lastUpdateType = updateType,
            lastUpdateSequence = sequenceNumber
        )
        return this
    }
    
    fun build(): InternalOrderBook {
        val processedTimestamp = System.currentTimeMillis()
        val internalBookTimestamp = System.currentTimeMillis()
        return InternalOrderBook(
            marketTicker = marketTicker,
            lastUpdateTimestamp = processedTimestamp,
            lastTimeInternalBookUpdated = internalBookTimestamp,
            sequenceNumber = sequenceNumber,
            receivedTimestamp = receivedTimestamp,
            processedTimestamp = processedTimestamp,
            yesSide = OrderBookSide("yes", yesLevels.toMap()),
            noSide = OrderBookSide("no", noLevels.toMap())
        )
    }
}