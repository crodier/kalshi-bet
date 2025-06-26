package com.fbg.api.kalshi

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Order book delta representing a quantity change at a specific price level
 */
@Serializable
data class OrderBookDelta(
    @SerialName("market_ticker")
    val marketTicker: String,
    
    val price: Int, // Price in cents
    val delta: Int, // Quantity change (positive = add, negative = remove)
    val side: String // "yes" or "no"
)

/**
 * Full order book snapshot
 */
@Serializable
data class OrderBookSnapshot(
    @SerialName("market_ticker")
    val marketTicker: String,
    
    val yes: List<List<Int>>, // Buy YES orders (bids) - sorted high to low, each inner list is [price, quantity]
    val no: List<List<Int>>   // Buy NO orders - sorted low to high, each inner list is [price, quantity]
)

/**
 * Wrapper for delta updates that can contain either a delta or a snapshot
 */
@Serializable
data class DeltaUpdate(
    val channel: String, // "orderbook_delta" or "orderbook_snapshot"
    
    @SerialName("market_ticker")
    val marketTicker: String,
    
    val seq: Long, // Sequence number for ordering updates
    val data: DeltaUpdateData
)

/**
 * Data payload for delta updates
 */
@Serializable
data class DeltaUpdateData(
    // For orderbook_delta
    val side: String? = null, // "yes" or "no"
    val price: Int? = null,   // Price in cents
    val delta: Int? = null,   // Quantity change
    
    // For orderbook_snapshot
    val yes: List<List<Int>>? = null, // Buy YES orders
    val no: List<List<Int>>? = null   // Buy NO orders
)

/**
 * Update counter for tracking when to send full snapshots
 */
@Serializable
data class UpdateCounter(
    @SerialName("market_ticker")
    val marketTicker: String,
    
    @SerialName("update_count")
    val updateCount: Long,
    
    @SerialName("last_snapshot_count")
    val lastSnapshotCount: Long,
    
    @SerialName("snapshot_interval")
    val snapshotInterval: Int = 100 // Send snapshot every N updates
) {
    /**
     * Check if a snapshot should be sent based on update count
     */
    fun shouldSendSnapshot(): Boolean {
        return updateCount - lastSnapshotCount >= snapshotInterval
    }
    
    /**
     * Create a new counter with incremented update count
     */
    fun incrementUpdate(): UpdateCounter {
        return copy(updateCount = updateCount + 1)
    }
    
    /**
     * Create a new counter after sending a snapshot
     */
    fun afterSnapshot(): UpdateCounter {
        return copy(lastSnapshotCount = updateCount)
    }
}

/**
 * WebSocket subscription request for order book updates
 */
@Serializable
data class OrderBookSubscription(
    val id: Int,
    val cmd: String = "subscribe",
    val params: SubscriptionParams
)

/**
 * Parameters for subscription request
 */
@Serializable
data class SubscriptionParams(
    val channels: List<String>, // e.g., ["orderbook_snapshot", "orderbook_delta"]
    
    @SerialName("market_tickers")
    val marketTickers: List<String> // e.g., ["TRUMP-2024", "BIDEN-2024"]
)

/**
 * WebSocket subscription response
 */
@Serializable
data class SubscriptionResponse(
    val id: Int,
    val status: String, // "success" or "error"
    val sids: List<String>? = null, // Subscription IDs
    val error: String? = null,
    val code: Int? = null
)