package com.fbg.api.orderbook

import com.fbg.api.fix.domain.IncomingOrder
import com.fbg.api.fix.enums.TradingSide
import com.fbg.api.market.Side
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentSkipListMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * Thread-safe concurrent order book that manages individual orders with NO/YES conversion.
 * 
 * Key features:
 * - All NO orders are internally converted to YES equivalents
 * - Maintains FIFO order priority at each price level
 * - Detects both self-crosses and external crosses
 * - Supports NewOrder, CancelOrder, and ModifyOrder operations
 */
class ConcurrentOrderBookV2(
    val marketTicker: String
) {
    // Price levels for normalized YES orders
    // Bids are buy orders (sorted high to low)
    private val bids = ConcurrentSkipListMap<Int, PriceLevel>(compareByDescending { it })
    // Asks are sell orders (sorted low to high)
    private val asks = ConcurrentSkipListMap<Int, PriceLevel>()
    
    // Order lookup by orderId for fast cancel/modify
    private val orderMap = ConcurrentHashMap<String, OrderLocation>()
    
    // Lock for complex operations
    private val lock = ReentrantReadWriteLock()
    
    // Listeners for order book events
    private val listeners = CopyOnWriteArrayList<OrderBookEventListener>()
    
    /**
     * Processes a new order
     */
    fun processNewOrder(newOrder: IncomingOrder.NewOrder, marketSide: Side): OrderResult {
        val order = BookOrder.fromNewOrder(newOrder, marketSide)
        
        return lock.write {
            // Check if order already exists
            if (orderMap.containsKey(order.orderId)) {
                return@write OrderResult.Rejected(
                    order.orderId,
                    "Order ${order.orderId} already exists"
                )
            }
            
            // Check for crosses before adding
            val crossCheck = checkForCross(order)
            if (crossCheck.isCrossed) {
                notifyListeners { it.onCrossDetected(crossCheck) }
                // In a real system, you might reject or match immediately
            }
            
            // Add order to appropriate side
            val priceLevel = when (order.normalizedSide) {
                TradingSide.BUY -> bids.computeIfAbsent(order.price) { PriceLevel(it) }
                TradingSide.SELL -> asks.computeIfAbsent(order.price) { PriceLevel(it) }
                else -> throw IllegalStateException("Invalid normalized side: ${order.normalizedSide}")
            }
            
            priceLevel.addOrder(order)
            orderMap[order.orderId] = OrderLocation(priceLevel, order.normalizedSide)
            
            notifyListeners { it.onOrderAdded(order) }
            
            OrderResult.Accepted(order.orderId, order.clientOrderId)
        }
    }
    
    /**
     * Cancels an existing order
     */
    fun processCancelOrder(cancelOrder: IncomingOrder.CancelOrder): OrderResult {
        return lock.write {
            val location = orderMap[cancelOrder.fbgOrderId]
                ?: return@write OrderResult.Rejected(
                    cancelOrder.fbgOrderId,
                    "Order ${cancelOrder.fbgOrderId} not found"
                )
            
            val order = location.priceLevel.removeOrder(cancelOrder.fbgOrderId)
            if (order != null) {
                orderMap.remove(cancelOrder.fbgOrderId)
                
                // Clean up empty price levels
                if (location.priceLevel.isEmpty()) {
                    when (location.side) {
                        TradingSide.BUY -> bids.remove(location.priceLevel.price)
                        TradingSide.SELL -> asks.remove(location.priceLevel.price)
                        else -> {}
                    }
                }
                
                notifyListeners { it.onOrderCanceled(order) }
                OrderResult.Canceled(order.orderId, order.clientOrderId)
            } else {
                OrderResult.Rejected(
                    cancelOrder.fbgOrderId,
                    "Order ${cancelOrder.fbgOrderId} not found in price level"
                )
            }
        }
    }
    
    /**
     * Modifies an existing order
     */
    fun processModifyOrder(modifyOrder: IncomingOrder.ModifyOrder, marketSide: Side): OrderResult {
        return lock.write {
            // First, find and remove the existing order
            val location = orderMap[modifyOrder.fbgOrderId]
                ?: return@write OrderResult.Rejected(
                    modifyOrder.fbgOrderId,
                    "Order ${modifyOrder.fbgOrderId} not found"
                )
            
            val existingOrder = location.priceLevel.findOrder(modifyOrder.fbgOrderId)
                ?: return@write OrderResult.Rejected(
                    modifyOrder.fbgOrderId,
                    "Order ${modifyOrder.fbgOrderId} not found in price level"
                )
            
            // Calculate new price if provided
            val newPrice = modifyOrder.price?.toInt() ?: existingOrder.originalPrice
            val newQuantity = modifyOrder.quantity?.toInt() ?: existingOrder.quantity
            
            // If only quantity changed and price is same, modify in place
            if (newPrice == existingOrder.originalPrice && marketSide == existingOrder.originalSide) {
                val updatedOrder = existingOrder.withNewQuantity(newQuantity)
                location.priceLevel.replaceOrder(updatedOrder)
                notifyListeners { it.onOrderModified(existingOrder, updatedOrder) }
                return@write OrderResult.Modified(updatedOrder.orderId, updatedOrder.clientOrderId)
            }
            
            // Otherwise, cancel and re-add (loses time priority)
            location.priceLevel.removeOrder(modifyOrder.fbgOrderId)
            orderMap.remove(modifyOrder.fbgOrderId)
            
            // Clean up empty price levels
            if (location.priceLevel.isEmpty()) {
                when (location.side) {
                    TradingSide.BUY -> bids.remove(location.priceLevel.price)
                    TradingSide.SELL -> asks.remove(location.priceLevel.price)
                    else -> {}
                }
            }
            
            // Create new order with same IDs but new parameters
            val newOrder = IncomingOrder.NewOrder(
                fbgOrderId = modifyOrder.fbgOrderId,
                createTimestamp = modifyOrder.createTimestamp,
                shortUUID = modifyOrder.shortUUID,
                side = modifyOrder.side,
                orderType = modifyOrder.orderType,
                timeInForce = modifyOrder.timeInForce,
                quantity = newQuantity.toDouble(),
                price = newPrice.toDouble(),
                symbol = modifyOrder.symbol,
                clientOrderId = modifyOrder.clientOrderId
            )
            
            // Process as new order
            processNewOrder(newOrder, marketSide)
        }
    }
    
    /**
     * Gets the best bid (highest buy price)
     */
    fun getBestBid(): PriceLevel? = lock.read {
        bids.firstEntry()?.value
    }
    
    /**
     * Gets the best ask (lowest sell price)
     */
    fun getBestAsk(): PriceLevel? = lock.read {
        asks.firstEntry()?.value
    }
    
    /**
     * Checks for crosses (both self-cross and external cross with NO side)
     */
    private fun checkForCross(newOrder: BookOrder): CrossInfo {
        val bestBid = bids.firstKey()
        val bestAsk = asks.firstKey()
        
        return when (newOrder.normalizedSide) {
            TradingSide.BUY -> {
                if (bestAsk != null && newOrder.price >= bestAsk) {
                    CrossInfo(
                        isCrossed = true,
                        crossType = CrossType.SELF_CROSS,
                        details = "Buy at ${newOrder.price}¢ crosses ask at ${bestAsk}¢"
                    )
                } else {
                    checkExternalCross()
                }
            }
            TradingSide.SELL -> {
                if (bestBid != null && newOrder.price <= bestBid) {
                    CrossInfo(
                        isCrossed = true,
                        crossType = CrossType.SELF_CROSS,
                        details = "Sell at ${newOrder.price}¢ crosses bid at ${bestBid}¢"
                    )
                } else {
                    checkExternalCross()
                }
            }
            else -> CrossInfo(false, null, null)
        }
    }
    
    /**
     * Checks for external cross (YES bid + NO bid > 100)
     */
    private fun checkExternalCross(): CrossInfo {
        val bestYesBid = bids.firstKey()
        if (bestYesBid != null) {
            // Since NO bids are converted to YES asks, we need to check
            // if YES bid + (100 - YES ask from NO) > 100
            // This simplifies to: YES bid + YES ask (from NO) > 100
            val bestNoBasedAsk = asks.entries.firstOrNull { entry ->
                entry.value.getOrders().any { it.originalSide == Side.no }
            }?.key
            
            if (bestNoBasedAsk != null) {
                val impliedNoBid = 100 - bestNoBasedAsk
                if (bestYesBid + impliedNoBid > 100) {
                    return CrossInfo(
                        isCrossed = true,
                        crossType = CrossType.EXTERNAL_CROSS,
                        details = "YES bid ${bestYesBid}¢ + NO bid ${impliedNoBid}¢ = ${bestYesBid + impliedNoBid}¢ > 100¢"
                    )
                }
            }
        }
        return CrossInfo(false, null, null)
    }
    
    /**
     * Gets order book depth statistics
     */
    fun getDepthStats(): OrderBookStats = lock.read {
        val bidCount = bids.values.sumOf { it.orderCount() }
        val askCount = asks.values.sumOf { it.orderCount() }
        val bidVolume = bids.values.sumOf { it.totalQuantity() }
        val askVolume = asks.values.sumOf { it.totalQuantity() }
        
        OrderBookStats(
            bidLevels = bids.size,
            askLevels = asks.size,
            totalBidOrders = bidCount,
            totalAskOrders = askCount,
            totalBidVolume = bidVolume,
            totalAskVolume = askVolume,
            bestBid = bids.firstKey(),
            bestAsk = asks.firstKey()
        )
    }
    
    fun addEventListener(listener: OrderBookEventListener) {
        listeners.add(listener)
    }
    
    fun removeEventListener(listener: OrderBookEventListener) {
        listeners.remove(listener)
    }
    
    private fun notifyListeners(action: (OrderBookEventListener) -> Unit) {
        listeners.forEach { listener ->
            try {
                action(listener)
            } catch (e: Exception) {
                // Log error but don't let one listener break others
            }
        }
    }
    
    /**
     * Price level containing orders at a specific price
     */
    inner class PriceLevel(val price: Int) {
        private val orders = mutableListOf<BookOrder>()
        private val orderLock = Any()
        
        fun addOrder(order: BookOrder) = synchronized(orderLock) {
            orders.add(order)
        }
        
        fun removeOrder(orderId: String): BookOrder? = synchronized(orderLock) {
            val index = orders.indexOfFirst { it.orderId == orderId }
            return if (index >= 0) orders.removeAt(index) else null
        }
        
        fun findOrder(orderId: String): BookOrder? = synchronized(orderLock) {
            orders.find { it.orderId == orderId }
        }
        
        fun replaceOrder(order: BookOrder) = synchronized(orderLock) {
            val index = orders.indexOfFirst { it.orderId == order.orderId }
            if (index >= 0) {
                orders[index] = order
            }
        }
        
        fun isEmpty(): Boolean = synchronized(orderLock) {
            orders.isEmpty()
        }
        
        fun orderCount(): Int = synchronized(orderLock) {
            orders.size
        }
        
        fun totalQuantity(): Int = synchronized(orderLock) {
            orders.sumOf { it.remainingQuantity }
        }
        
        fun getOrders(): List<BookOrder> = synchronized(orderLock) {
            orders.toList()
        }
    }
}

/**
 * Location of an order in the book
 */
private data class OrderLocation(
    val priceLevel: ConcurrentOrderBookV2.PriceLevel,
    val side: TradingSide
)

/**
 * Result of order operations
 */
sealed class OrderResult {
    data class Accepted(val orderId: String, val clientOrderId: String) : OrderResult()
    data class Rejected(val orderId: String, val reason: String) : OrderResult()
    data class Canceled(val orderId: String, val clientOrderId: String) : OrderResult()
    data class Modified(val orderId: String, val clientOrderId: String) : OrderResult()
}

/**
 * Information about detected crosses
 */
data class CrossInfo(
    val isCrossed: Boolean,
    val crossType: CrossType?,
    val details: String?
)

enum class CrossType {
    SELF_CROSS,      // Bid >= Ask on same side
    EXTERNAL_CROSS   // YES bid + NO bid > 100
}

/**
 * Order book statistics
 */
data class OrderBookStats(
    val bidLevels: Int,
    val askLevels: Int,
    val totalBidOrders: Int,
    val totalAskOrders: Int,
    val totalBidVolume: Int,
    val totalAskVolume: Int,
    val bestBid: Int?,
    val bestAsk: Int?
)

/**
 * Listener for order book events
 */
interface OrderBookEventListener {
    fun onOrderAdded(order: BookOrder)
    fun onOrderCanceled(order: BookOrder)
    fun onOrderModified(oldOrder: BookOrder, newOrder: BookOrder)
    fun onCrossDetected(crossInfo: CrossInfo)
}