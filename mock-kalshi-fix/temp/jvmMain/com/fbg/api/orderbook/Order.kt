package com.fbg.api.orderbook

import com.fbg.api.fix.domain.IncomingOrder
import com.fbg.api.fix.enums.TradingSide
import com.fbg.api.market.Side
import java.util.concurrent.atomic.AtomicLong

/**
 * Internal representation of an order in the order book.
 * All orders are normalized to YES side for simplified matching.
 */
data class BookOrder(
    val orderId: String,
    val clientOrderId: String,
    val originalSide: Side,          // YES or NO (original market side)
    val originalTradingSide: TradingSide,  // BUY or SELL
    val normalizedSide: TradingSide, // After NO/YES conversion
    val price: Int,                  // In cents (1-99)
    val originalPrice: Int,          // Original price before conversion
    val quantity: Int,               // Number of contracts
    val remainingQuantity: Int,      // Unfilled quantity
    val timestamp: Long,             // For time priority
    val sequenceNumber: Long         // Global sequence for absolute ordering
) {
    val isFilled: Boolean get() = remainingQuantity <= 0
    
    companion object {
        private val sequenceGenerator = AtomicLong(0)
        
        /**
         * Creates a BookOrder from IncomingOrder.NewOrder with NO/YES conversion
         */
        fun fromNewOrder(
            newOrder: IncomingOrder.NewOrder,
            marketSide: Side
        ): BookOrder {
            val price = newOrder.price?.toInt() ?: throw IllegalArgumentException("Price required for order")
            require(price in 1..99) { "Price must be between 1 and 99 cents" }
            
            val quantity = newOrder.quantity.toInt()
            require(quantity > 0) { "Quantity must be positive" }
            
            // Apply NO/YES conversion
            val (normalizedSide, normalizedPrice) = when (marketSide) {
                Side.yes -> {
                    // YES orders remain unchanged
                    newOrder.side to price
                }
                Side.no -> {
                    // NO orders are converted:
                    // Buy NO @ P = Sell YES @ (100-P)
                    // Sell NO @ P = Buy YES @ (100-P)
                    val convertedSide = when (newOrder.side) {
                        TradingSide.BUY -> TradingSide.SELL
                        TradingSide.SELL -> TradingSide.BUY
                        else -> throw IllegalArgumentException("Unsupported trading side: ${newOrder.side}")
                    }
                    convertedSide to (100 - price)
                }
            }
            
            return BookOrder(
                orderId = newOrder.fbgOrderId,
                clientOrderId = newOrder.clientOrderId,
                originalSide = marketSide,
                originalTradingSide = newOrder.side,
                normalizedSide = normalizedSide,
                price = normalizedPrice,
                originalPrice = price,
                quantity = quantity,
                remainingQuantity = quantity,
                timestamp = newOrder.createTimestamp,
                sequenceNumber = sequenceGenerator.incrementAndGet()
            )
        }
    }
    
    /**
     * Creates a copy with updated remaining quantity
     */
    fun withRemainingQuantity(newRemaining: Int): BookOrder {
        require(newRemaining >= 0) { "Remaining quantity cannot be negative" }
        require(newRemaining <= quantity) { "Remaining quantity cannot exceed original quantity" }
        return copy(remainingQuantity = newRemaining)
    }
    
    /**
     * Creates a copy with updated quantity (for modify orders)
     */
    fun withNewQuantity(newQuantity: Int): BookOrder {
        require(newQuantity > 0) { "Quantity must be positive" }
        // If increasing quantity, adjust remaining proportionally
        val newRemaining = if (newQuantity > quantity) {
            remainingQuantity + (newQuantity - quantity)
        } else {
            // If decreasing, cap remaining at new quantity
            minOf(remainingQuantity, newQuantity)
        }
        return copy(
            quantity = newQuantity,
            remainingQuantity = newRemaining
        )
    }
}

/**
 * Result of NO/YES conversion
 */
data class ConversionResult(
    val side: TradingSide,
    val price: Int,
    val isConverted: Boolean
)