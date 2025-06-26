package com.fbg.api.orderbook

import com.fbg.api.market.*
import com.fbg.api.fix.enums.TradingSide
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.concurrent.atomic.AtomicLong

/**
 * WebSocket message publisher that formats order book updates
 * according to the Kalshi WebSocket API specification.
 */
class WebSocketPublisher {
    private val sequenceGenerator = AtomicLong(0)
    private val json = Json { 
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    
    /**
     * Publishes order book snapshot in Kalshi format
     */
    fun publishSnapshot(
        marketTicker: String,
        orderBook: ConcurrentOrderBookV2
    ): WebSocketMessage {
        val stats = orderBook.getDepthStats()
        val snapshot = OrderBookSnapshotData(
            market = marketTicker,
            bids = BidAskLevels(
                yes = collectYesBids(orderBook),
                no = collectNoBids(orderBook)
            ),
            asks = BidAskLevels(
                yes = collectYesAsks(orderBook),
                no = collectNoAsks(orderBook)
            )
        )
        
        return WebSocketMessage(
            type = "orderbook_snapshot",
            sequence = sequenceGenerator.incrementAndGet(),
            timestamp = System.currentTimeMillis(),
            data = snapshot
        )
    }
    
    /**
     * Publishes order book delta when an order is added
     */
    fun publishOrderAdded(order: BookOrder): WebSocketMessage {
        // Convert back to original side/price for external representation
        val (externalSide, externalType) = when (order.originalSide) {
            Side.yes -> when (order.normalizedSide) {
                TradingSide.BUY -> "yes" to "bid"
                TradingSide.SELL -> "yes" to "ask"
                else -> throw IllegalStateException("Invalid trading side")
            }
            Side.no -> when (order.originalTradingSide) {
                TradingSide.BUY -> "no" to "bid"
                TradingSide.SELL -> "no" to "ask"
                else -> throw IllegalStateException("Invalid trading side")
            }
        }
        
        val update = OrderBookUpdateData(
            market = order.orderId.substringBefore("_"), // Extract market from order ID
            changes = listOf(
                PriceLevelChange(
                    side = externalSide,
                    type = externalType,
                    price = order.originalPrice,
                    size = order.quantity // This would need to be aggregated size at level
                )
            )
        )
        
        return WebSocketMessage(
            type = "orderbook_update",
            sequence = sequenceGenerator.incrementAndGet(),
            timestamp = System.currentTimeMillis(),
            data = update
        )
    }
    
    /**
     * Publishes ticker update with best bid/ask
     */
    fun publishTickerUpdate(
        marketTicker: String,
        orderBook: ConcurrentOrderBookV2
    ): WebSocketMessage {
        val bestBid = orderBook.getBestBid()
        val bestAsk = orderBook.getBestAsk()
        
        // Calculate YES and NO prices from internal representation
        val yesBid = bestBid?.price
        val yesAsk = bestAsk?.price
        
        // For NO side, we need to find orders that were originally NO
        val noBid = findBestNoBid(orderBook)
        val noAsk = findBestNoAsk(orderBook)
        
        val ticker = TickerData(
            market = marketTicker,
            yes_bid = yesBid,
            yes_ask = yesAsk,
            no_bid = noBid,
            no_ask = noAsk,
            last_price = null, // Would come from trade history
            volume_24h = 0, // Would need to track
            open_interest = 0 // Would need to track
        )
        
        return WebSocketMessage(
            type = "ticker",
            sequence = sequenceGenerator.incrementAndGet(),
            timestamp = System.currentTimeMillis(),
            data = ticker
        )
    }
    
    /**
     * Publishes trade execution
     */
    fun publishTrade(
        marketTicker: String,
        trade: Trade
    ): WebSocketMessage {
        val tradeData = TradeData(
            market = marketTicker,
            trade_id = trade.tradeId,
            price = trade.price,
            size = trade.size,
            side = trade.side.name,
            buyer_maker = trade.aggressor == OrderSide.ASK
        )
        
        return WebSocketMessage(
            type = "trade",
            sequence = sequenceGenerator.incrementAndGet(),
            timestamp = System.currentTimeMillis(),
            data = tradeData
        )
    }
    
    /**
     * Publishes user-specific fill
     */
    fun publishFill(
        order: BookOrder,
        trade: Trade,
        fillSize: Int
    ): WebSocketMessage {
        val fillData = FillData(
            order_id = order.orderId,
            trade_id = trade.tradeId,
            market = order.orderId.substringBefore("_"),
            side = order.originalSide.name,
            action = if (order.originalTradingSide == TradingSide.BUY) "buy" else "sell",
            price = trade.price,
            size = fillSize,
            fee = calculateFee(fillSize, trade.price),
            is_maker = trade.aggressor != 
                if (order.normalizedSide == TradingSide.BUY) OrderSide.BID else OrderSide.ASK
        )
        
        return WebSocketMessage(
            type = "fill",
            sequence = sequenceGenerator.incrementAndGet(),
            timestamp = System.currentTimeMillis(),
            data = fillData
        )
    }
    
    // Helper methods
    
    private fun collectYesBids(orderBook: ConcurrentOrderBookV2): List<List<Int>> {
        val bestBid = orderBook.getBestBid() ?: return emptyList()
        // This is simplified - would need access to all bid levels
        return listOf(listOf(bestBid.price, bestBid.totalQuantity()))
    }
    
    private fun collectYesAsks(orderBook: ConcurrentOrderBookV2): List<List<Int>> {
        val bestAsk = orderBook.getBestAsk() ?: return emptyList()
        // This is simplified - would need access to all ask levels
        return listOf(listOf(bestAsk.price, bestAsk.totalQuantity()))
    }
    
    private fun collectNoBids(orderBook: ConcurrentOrderBookV2): List<List<Int>> {
        // Find YES asks that originated from NO buys
        // Price conversion: NO bid price = 100 - YES ask price
        return emptyList() // Simplified
    }
    
    private fun collectNoAsks(orderBook: ConcurrentOrderBookV2): List<List<Int>> {
        // Find YES bids that originated from NO sells
        // Price conversion: NO ask price = 100 - YES bid price
        return emptyList() // Simplified
    }
    
    private fun findBestNoBid(orderBook: ConcurrentOrderBookV2): Int? {
        // Best NO bid is the lowest YES ask from a NO buy order
        return null // Simplified
    }
    
    private fun findBestNoAsk(orderBook: ConcurrentOrderBookV2): Int? {
        // Best NO ask is the highest YES bid from a NO sell order
        return null // Simplified
    }
    
    private fun calculateFee(size: Int, price: Int): Double {
        // Kalshi fee structure (example)
        return size * price * 0.01 / 100.0 // 1% of notional
    }
}

// WebSocket message types

// Note: In actual implementation, serialize data separately
data class WebSocketMessage(
    val type: String,
    val sequence: Long,
    val timestamp: Long,
    val data: Any
)

@Serializable
data class OrderBookSnapshotData(
    val market: String,
    val bids: BidAskLevels,
    val asks: BidAskLevels
)

@Serializable
data class BidAskLevels(
    val yes: List<List<Int>>,
    val no: List<List<Int>>
)

@Serializable
data class OrderBookUpdateData(
    val market: String,
    val changes: List<PriceLevelChange>
)

@Serializable
data class PriceLevelChange(
    val side: String,
    val type: String,
    val price: Int,
    val size: Int
)

@Serializable
data class TickerData(
    val market: String,
    val yes_bid: Int?,
    val yes_ask: Int?,
    val no_bid: Int?,
    val no_ask: Int?,
    val last_price: Int?,
    val volume_24h: Long,
    val open_interest: Long
)

@Serializable
data class TradeData(
    val market: String,
    val trade_id: String,
    val price: Int,
    val size: Int,
    val side: String,
    val buyer_maker: Boolean
)

@Serializable
data class FillData(
    val order_id: String,
    val trade_id: String,
    val market: String,
    val side: String,
    val action: String,
    val price: Int,
    val size: Int,
    val fee: Double,
    val is_maker: Boolean
)

@Serializable
data class OrderUpdateData(
    val order_id: String,
    val market: String,
    val status: String,
    val filled_quantity: Int,
    val remaining_quantity: Int,
    val average_fill_price: Double?
)