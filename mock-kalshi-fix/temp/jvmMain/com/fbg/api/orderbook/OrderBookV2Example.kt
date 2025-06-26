package com.fbg.api.orderbook

import com.fbg.api.fix.domain.IncomingOrder
import com.fbg.api.fix.enums.OrderType
import com.fbg.api.fix.enums.TimeInForce
import com.fbg.api.fix.enums.TradingSide
import com.fbg.api.market.Side

/**
 * Example demonstrating the new concurrent order book with NO/YES conversion
 */
object OrderBookV2Example {
    
    @JvmStatic
    fun main(args: Array<String>) {
        val orderBook = ConcurrentOrderBookV2("TRUMP-2024")
        
        // Add event listener
        orderBook.addEventListener(object : OrderBookEventListener {
            override fun onOrderAdded(order: BookOrder) {
                println("Order added: ${order.orderId} - Original: ${order.originalSide} ${order.originalTradingSide} @ ${order.originalPrice}¢ -> Normalized: ${order.normalizedSide} @ ${order.price}¢")
            }
            
            override fun onOrderCanceled(order: BookOrder) {
                println("Order canceled: ${order.orderId}")
            }
            
            override fun onOrderModified(oldOrder: BookOrder, newOrder: BookOrder) {
                println("Order modified: ${oldOrder.orderId} - Quantity: ${oldOrder.quantity} -> ${newOrder.quantity}")
            }
            
            override fun onCrossDetected(crossInfo: CrossInfo) {
                println("CROSS DETECTED: ${crossInfo.crossType} - ${crossInfo.details}")
            }
        })
        
        println("=== Kalshi Order Book Example with NO/YES Conversion ===\n")
        
        // Example 1: Add YES buy order
        println("1. Adding YES BUY @ 65¢")
        val yesBuyOrder = IncomingOrder.NewOrder(
            fbgOrderId = "order-1",
            createTimestamp = System.currentTimeMillis(),
            shortUUID = "uuid-1",
            side = TradingSide.BUY,
            orderType = OrderType.LIMIT,
            timeInForce = TimeInForce.GOOD_TILL_CANCEL,
            quantity = 100.0,
            price = 65.0,
            symbol = "TRUMP-2024",
            clientOrderId = "client-1"
        )
        orderBook.processNewOrder(yesBuyOrder, Side.yes)
        
        // Example 2: Add NO buy order (converts to YES sell)
        println("\n2. Adding NO BUY @ 30¢ (converts to YES SELL @ 70¢)")
        val noBuyOrder = IncomingOrder.NewOrder(
            fbgOrderId = "order-2",
            createTimestamp = System.currentTimeMillis(),
            shortUUID = "uuid-2",
            side = TradingSide.BUY,
            orderType = OrderType.LIMIT,
            timeInForce = TimeInForce.GOOD_TILL_CANCEL,
            quantity = 150.0,
            price = 30.0,
            symbol = "TRUMP-2024",
            clientOrderId = "client-2"
        )
        orderBook.processNewOrder(noBuyOrder, Side.no)
        
        // Example 3: Add YES sell order
        println("\n3. Adding YES SELL @ 66¢")
        val yesSellOrder = IncomingOrder.NewOrder(
            fbgOrderId = "order-3",
            createTimestamp = System.currentTimeMillis(),
            shortUUID = "uuid-3",
            side = TradingSide.SELL,
            orderType = OrderType.LIMIT,
            timeInForce = TimeInForce.GOOD_TILL_CANCEL,
            quantity = 200.0,
            price = 66.0,
            symbol = "TRUMP-2024",
            clientOrderId = "client-3"
        )
        orderBook.processNewOrder(yesSellOrder, Side.yes)
        
        // Example 4: Add NO sell order (converts to YES buy)
        println("\n4. Adding NO SELL @ 35¢ (converts to YES BUY @ 65¢)")
        val noSellOrder = IncomingOrder.NewOrder(
            fbgOrderId = "order-4",
            createTimestamp = System.currentTimeMillis(),
            shortUUID = "uuid-4",
            side = TradingSide.SELL,
            orderType = OrderType.LIMIT,
            timeInForce = TimeInForce.GOOD_TILL_CANCEL,
            quantity = 120.0,
            price = 35.0,
            symbol = "TRUMP-2024",
            clientOrderId = "client-4"
        )
        orderBook.processNewOrder(noSellOrder, Side.no)
        
        // Show order book state
        println("\n=== Order Book State ===")
        val stats = orderBook.getDepthStats()
        println("Best Bid: ${stats.bestBid}¢ (${stats.totalBidOrders} orders, ${stats.totalBidVolume} contracts)")
        println("Best Ask: ${stats.bestAsk}¢ (${stats.totalAskOrders} orders, ${stats.totalAskVolume} contracts)")
        println("Spread: ${stats.bestAsk?.minus(stats.bestBid ?: 0)}¢")
        
        // Example 5: Test external cross detection
        println("\n5. Testing external cross - Adding NO BUY @ 40¢")
        val crossingNoBuy = IncomingOrder.NewOrder(
            fbgOrderId = "order-5",
            createTimestamp = System.currentTimeMillis(),
            shortUUID = "uuid-5",
            side = TradingSide.BUY,
            orderType = OrderType.LIMIT,
            timeInForce = TimeInForce.GOOD_TILL_CANCEL,
            quantity = 50.0,
            price = 40.0,  // This creates YES SELL @ 60¢, which with YES BID @ 65¢ = 125¢ > 100¢
            symbol = "TRUMP-2024",
            clientOrderId = "client-5"
        )
        orderBook.processNewOrder(crossingNoBuy, Side.no)
        
        // Example 6: Test self-cross detection
        println("\n6. Testing self-cross - Adding YES BUY @ 67¢ (crosses existing ask)")
        val crossingYesBuy = IncomingOrder.NewOrder(
            fbgOrderId = "order-6",
            createTimestamp = System.currentTimeMillis(),
            shortUUID = "uuid-6",
            side = TradingSide.BUY,
            orderType = OrderType.LIMIT,
            timeInForce = TimeInForce.GOOD_TILL_CANCEL,
            quantity = 75.0,
            price = 67.0,
            symbol = "TRUMP-2024",
            clientOrderId = "client-6"
        )
        orderBook.processNewOrder(crossingYesBuy, Side.yes)
        
        // Example 7: Cancel an order
        println("\n7. Canceling order-2")
        val cancelOrder = IncomingOrder.CancelOrder(
            fbgOrderId = "order-2",
            createTimestamp = System.currentTimeMillis(),
            shortUUID = "uuid-cancel",
            originalClientOrderId = "client-2",
            clientOrderId = "client-cancel",
            side = TradingSide.BUY,
            symbol = "TRUMP-2024"
        )
        orderBook.processCancelOrder(cancelOrder)
        
        // Example 8: Modify an order
        println("\n8. Modifying order-3 quantity from 200 to 300")
        val modifyOrder = IncomingOrder.ModifyOrder(
            fbgOrderId = "order-3",
            createTimestamp = System.currentTimeMillis(),
            shortUUID = "uuid-modify",
            originalClientOrderId = "client-3",
            clientOrderId = "client-modify",
            side = TradingSide.SELL,
            orderType = OrderType.LIMIT,
            timeInForce = TimeInForce.GOOD_TILL_CANCEL,
            quantity = 300.0,
            price = 66.0,
            symbol = "TRUMP-2024"
        )
        orderBook.processModifyOrder(modifyOrder, Side.yes)
        
        // Final order book state
        println("\n=== Final Order Book State ===")
        val finalStats = orderBook.getDepthStats()
        println("Bid Levels: ${finalStats.bidLevels}, Ask Levels: ${finalStats.askLevels}")
        println("Total Bid Orders: ${finalStats.totalBidOrders}, Total Ask Orders: ${finalStats.totalAskOrders}")
        println("Total Bid Volume: ${finalStats.totalBidVolume}, Total Ask Volume: ${finalStats.totalAskVolume}")
    }
}