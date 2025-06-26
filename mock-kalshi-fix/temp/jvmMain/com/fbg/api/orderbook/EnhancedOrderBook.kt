package com.fbg.api.orderbook

import com.fbg.api.market.PriceLevel
import com.fbg.api.market.Side

/**
 * Enhanced order book snapshot that clearly separates bids and asks for each side
 */
data class EnhancedOrderbookSnapshot(
    val marketTicker: String,
    val yesBids: List<PriceLevel>,
    val yesAsks: List<PriceLevel>,
    val noBids: List<PriceLevel>,
    val noAsks: List<PriceLevel>,
    val sequenceNumber: Long? = null,
    val timestamp: Long? = null
)

/**
 * Enhanced order book delta that includes bid/ask information
 */
data class EnhancedOrderbookDelta(
    val marketTicker: String,
    val price: Int,
    val delta: Int,
    val side: Side,
    val isBid: Boolean, // true for bid, false for ask
    val sequenceNumber: Long? = null,
    val timestamp: Long? = null
)

/**
 * Order type for the enhanced model
 */
enum class OrderSide {
    BID,
    ASK
}

/**
 * Represents a single order in the book (Level 3 data)
 */
data class Level3Order(
    val orderId: String,
    val side: Side, // YES or NO
    val orderSide: OrderSide, // BID or ASK
    val price: Int,
    val size: Int,
    val timestamp: Long
)

/**
 * Level 2 market data with aggregated price levels
 */
data class Level2Data(
    val marketTicker: String,
    val bids: List<PriceLevel>,
    val asks: List<PriceLevel>,
    val side: Side,
    val timestamp: Long
)

/**
 * Level 3 market data with individual orders
 */
data class Level3Data(
    val marketTicker: String,
    val orders: List<Level3Order>,
    val side: Side,
    val timestamp: Long
)

/**
 * Market depth representation
 */
data class MarketDepth(
    val marketTicker: String,
    val yesDepth: SideDepth,
    val noDepth: SideDepth,
    val timestamp: Long
)

/**
 * Depth information for one side (YES or NO)
 */
data class SideDepth(
    val side: Side,
    val bidLevels: List<DepthLevel>,
    val askLevels: List<DepthLevel>,
    val totalBidVolume: Int,
    val totalAskVolume: Int,
    val weightedAvgBidPrice: Double?,
    val weightedAvgAskPrice: Double?
)

/**
 * Enhanced depth level with cumulative information
 */
data class DepthLevel(
    val price: Int,
    val size: Int,
    val cumulativeSize: Int,
    val numberOfOrders: Int? = null
)

/**
 * Trade information for order book updates
 */
data class Trade(
    val tradeId: String,
    val marketTicker: String,
    val side: Side,
    val price: Int,
    val size: Int,
    val aggressor: OrderSide, // Which side was the aggressor
    val timestamp: Long
)

/**
 * Order book event types for streaming updates
 */
enum class OrderBookEventType {
    SNAPSHOT,
    UPDATE,
    TRADE,
    CLEAR,
    BEST_BID_ASK_CHANGE
}

/**
 * Generic order book event
 */
data class OrderBookEvent(
    val type: OrderBookEventType,
    val marketTicker: String,
    val data: String, // JSON serialized data specific to event type
    val sequenceNumber: Long,
    val timestamp: Long
)