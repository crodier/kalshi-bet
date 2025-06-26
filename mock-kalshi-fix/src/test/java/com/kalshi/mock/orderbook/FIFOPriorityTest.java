package com.kalshi.mock.orderbook;

import com.fbg.api.market.KalshiSide;
import com.fbg.api.rest.*;
import com.kalshi.mock.model.ConcurrentOrderBook;
import com.kalshi.mock.model.OrderBookEntry;
import com.kalshi.mock.service.MatchingEngine;
import com.kalshi.mock.service.MatchingEngine.Execution;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.List;
import java.util.Map;
import java.util.Queue;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for FIFO (First In, First Out) order priority at price levels.
 * 
 * Orders at the same price level must maintain strict time priority.
 */
public class FIFOPriorityTest {
    
    private ConcurrentOrderBook orderBook;
    private MatchingEngine matchingEngine;
    
    @BeforeEach
    public void setUp() {
        orderBook = new ConcurrentOrderBook("TEST-MARKET");
        matchingEngine = new MatchingEngine();
    }
    
    @Test
    @DisplayName("FIFO priority: Orders at same price level execute in time order")
    public void testFIFOExecutionOrder() {
        // Add three buy orders at 65¢ with different timestamps
        OrderBookEntry order1 = new OrderBookEntry("ORDER-1", "USER-1", KalshiSide.yes, "buy", 65, 100, 1000);
        OrderBookEntry order2 = new OrderBookEntry("ORDER-2", "USER-2", KalshiSide.yes, "buy", 65, 200, 2000);
        OrderBookEntry order3 = new OrderBookEntry("ORDER-3", "USER-3", KalshiSide.yes, "buy", 65, 150, 3000);
        
        orderBook.addOrder(order1);
        orderBook.addOrder(order2);
        orderBook.addOrder(order3);
        
        // Verify orders are queued at the same price level
        Map.Entry<Integer, Queue<OrderBookEntry>> bestBid = orderBook.getBestBid();
        assertNotNull(bestBid);
        assertEquals(65, bestBid.getKey());
        assertEquals(3, bestBid.getValue().size());
        
        // Match a sell order of 250 contracts
        OrderBookEntry sellOrder = new OrderBookEntry("SELL-1", "USER-4", KalshiSide.yes, "sell", 65, 250, 4000);
        List<Execution> executions = matchingEngine.matchOrder(sellOrder, orderBook);
        
        // Should have 2 executions (order1 fully filled, order2 partially filled)
        assertEquals(2, executions.size());
        
        // First execution should be against ORDER-1 (100 contracts)
        Execution exec1 = executions.get(0);
        assertEquals("ORDER-1", exec1.getPassive().getOrderId());
        assertEquals(100, exec1.getQuantity());
        
        // Second execution should be against ORDER-2 (150 contracts)
        Execution exec2 = executions.get(1);
        assertEquals("ORDER-2", exec2.getPassive().getOrderId());
        assertEquals(150, exec2.getQuantity());
        
        // ORDER-3 should remain untouched
        assertEquals(150, order3.getQuantity());
        
        // ORDER-2 should have 50 remaining
        assertEquals(50, order2.getQuantity());
    }
    
    @Test
    @DisplayName("FIFO priority maintained across YES and NO orders at same normalized price")
    public void testFIFOMixedYESNOOrders() {
        // Add orders that normalize to same YES price level
        // Buy YES at 65¢
        OrderBookEntry yesBuy1 = new OrderBookEntry("YES-1", "USER-1", KalshiSide.yes, "buy", 65, 100, 1000);
        orderBook.addOrder(yesBuy1);
        
        // Sell NO at 35¢ (converts to Buy YES at 65¢)
        OrderBookEntry noSell1 = new OrderBookEntry("NO-1", "USER-2", KalshiSide.no, "sell", 35, 200, 2000);
        orderBook.addOrder(noSell1);
        
        // Buy YES at 65¢ again
        OrderBookEntry yesBuy2 = new OrderBookEntry("YES-2", "USER-3", KalshiSide.yes, "buy", 65, 150, 3000);
        orderBook.addOrder(yesBuy2);
        
        // Match a sell order
        OrderBookEntry sellOrder = new OrderBookEntry("SELL-1", "USER-4", KalshiSide.yes, "sell", 65, 350, 4000);
        List<Execution> executions = matchingEngine.matchOrder(sellOrder, orderBook);
        
        // Should execute in order: YES-1, NO-1, YES-2
        assertEquals(3, executions.size());
        assertEquals("YES-1", executions.get(0).getPassive().getOrderId());
        assertEquals("NO-1", executions.get(1).getPassive().getOrderId());
        assertEquals("YES-2", executions.get(2).getPassive().getOrderId());
    }
    
    @Test
    @DisplayName("Multiple price levels maintain independent FIFO queues")
    public void testMultiplePriceLevelsFIFO() {
        // Add orders at different price levels
        // Level 66¢
        orderBook.addOrder(new OrderBookEntry("BID-66-1", "U1", KalshiSide.yes, "buy", 66, 100, 1000));
        orderBook.addOrder(new OrderBookEntry("BID-66-2", "U2", KalshiSide.yes, "buy", 66, 100, 2000));
        
        // Level 65¢
        orderBook.addOrder(new OrderBookEntry("BID-65-1", "U3", KalshiSide.yes, "buy", 65, 100, 3000));
        orderBook.addOrder(new OrderBookEntry("BID-65-2", "U4", KalshiSide.yes, "buy", 65, 100, 4000));
        
        // Level 64¢
        orderBook.addOrder(new OrderBookEntry("BID-64-1", "U5", KalshiSide.yes, "buy", 64, 100, 5000));
        
        // Match a large sell order at 64¢
        OrderBookEntry sellOrder = new OrderBookEntry("SELL-1", "U6", KalshiSide.yes, "sell", 64, 400, 6000);
        List<Execution> executions = matchingEngine.matchOrder(sellOrder, orderBook);
        
        // Should match in price-time priority:
        // 1. BID-66-1 at 66¢ (best price, first time)
        // 2. BID-66-2 at 66¢ (best price, second time)
        // 3. BID-65-1 at 65¢ (second best price, first time)
        // 4. BID-65-2 at 65¢ (second best price, second time)
        assertEquals(4, executions.size());
        assertEquals("BID-66-1", executions.get(0).getPassive().getOrderId());
        assertEquals("BID-66-2", executions.get(1).getPassive().getOrderId());
        assertEquals("BID-65-1", executions.get(2).getPassive().getOrderId());
        assertEquals("BID-65-2", executions.get(3).getPassive().getOrderId());
    }
    
    @Test
    @DisplayName("Order sequence numbers maintain strict ordering")
    public void testOrderSequenceNumbers() throws InterruptedException {
        // Create orders with minimal time gaps to test sequence ordering
        OrderBookEntry order1 = new OrderBookEntry("O1", "U1", KalshiSide.yes, "buy", 50, 100, System.currentTimeMillis());
        Thread.sleep(1); // Ensure different timestamps
        OrderBookEntry order2 = new OrderBookEntry("O2", "U2", KalshiSide.yes, "buy", 50, 100, System.currentTimeMillis());
        Thread.sleep(1);
        OrderBookEntry order3 = new OrderBookEntry("O3", "U3", KalshiSide.yes, "buy", 50, 100, System.currentTimeMillis());
        
        // Verify sequence numbers are strictly increasing
        assertTrue(order1.getSequence() < order2.getSequence());
        assertTrue(order2.getSequence() < order3.getSequence());
    }
    
    @Test
    @DisplayName("Partial fills maintain FIFO position")
    public void testPartialFillMaintainsFIFO() {
        // Add two orders at same price
        OrderBookEntry order1 = new OrderBookEntry("O1", "U1", KalshiSide.yes, "buy", 70, 100, 1000);
        OrderBookEntry order2 = new OrderBookEntry("O2", "U2", KalshiSide.yes, "buy", 70, 100, 2000);
        
        orderBook.addOrder(order1);
        orderBook.addOrder(order2);
        
        // Partially fill first order
        OrderBookEntry sell1 = new OrderBookEntry("S1", "U3", KalshiSide.yes, "sell", 70, 60, 3000);
        matchingEngine.matchOrder(sell1, orderBook);
        
        // Verify order1 has 40 remaining
        assertEquals(40, order1.getQuantity());
        assertEquals(100, order2.getQuantity());
        
        // Next match should continue with order1
        OrderBookEntry sell2 = new OrderBookEntry("S2", "U4", KalshiSide.yes, "sell", 70, 50, 4000);
        List<Execution> executions = matchingEngine.matchOrder(sell2, orderBook);
        
        // Should fill remaining 40 from order1, then 10 from order2
        assertEquals(2, executions.size());
        assertEquals("O1", executions.get(0).getPassive().getOrderId());
        assertEquals(40, executions.get(0).getQuantity());
        assertEquals("O2", executions.get(1).getPassive().getOrderId());
        assertEquals(10, executions.get(1).getQuantity());
    }
    
    @Test
    @DisplayName("FIFO maintained when orders have same timestamp but different sequence")
    public void testFIFOSameTimestamp() {
        // Create orders with same timestamp
        long timestamp = System.currentTimeMillis();
        OrderBookEntry order1 = new OrderBookEntry("O1", "U1", KalshiSide.yes, "buy", 55, 100, timestamp);
        OrderBookEntry order2 = new OrderBookEntry("O2", "U2", KalshiSide.yes, "buy", 55, 100, timestamp);
        OrderBookEntry order3 = new OrderBookEntry("O3", "U3", KalshiSide.yes, "buy", 55, 100, timestamp);
        
        // Add in specific order
        orderBook.addOrder(order1);
        orderBook.addOrder(order2);
        orderBook.addOrder(order3);
        
        // Match against them
        OrderBookEntry sellOrder = new OrderBookEntry("S1", "U4", KalshiSide.yes, "sell", 55, 250, timestamp + 1000);
        List<Execution> executions = matchingEngine.matchOrder(sellOrder, orderBook);
        
        // Should still maintain order based on sequence numbers
        assertEquals(3, executions.size());
        assertEquals("O1", executions.get(0).getPassive().getOrderId());
        assertEquals("O2", executions.get(1).getPassive().getOrderId());
        assertEquals("O3", executions.get(2).getPassive().getOrderId());
    }
}