package com.kalshi.mock.orderbook;

import com.fbg.api.market.KalshiSide;
import com.kalshi.mock.model.ConcurrentOrderBook;
import com.kalshi.mock.model.OrderBookEntry;
import com.kalshi.mock.service.MatchingEngine;
import com.kalshi.mock.service.MatchingEngine.Execution;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Edge case tests for the order book implementation.
 * Tests boundary conditions, extreme scenarios, and special cases.
 */
public class EdgeCaseTest {
    
    private ConcurrentOrderBook orderBook;
    private MatchingEngine matchingEngine;
    
    @BeforeEach
    public void setUp() {
        orderBook = new ConcurrentOrderBook("TEST-MARKET");
        matchingEngine = new MatchingEngine();
    }
    
    @Test
    @DisplayName("Orders at price boundaries (1¢ and 99¢)")
    public void testPriceBoundaries() {
        // Test 1¢ price
        OrderBookEntry order1Cent = new OrderBookEntry("O1", "U1", KalshiSide.yes, "buy", 1, 100, 1000);
        assertTrue(orderBook.addOrder(order1Cent));
        assertEquals(1, order1Cent.getPrice());
        assertEquals(1, order1Cent.getNormalizedPrice());
        
        // Test 99¢ price
        OrderBookEntry order99Cent = new OrderBookEntry("O2", "U2", KalshiSide.yes, "sell", 99, 100, 2000);
        assertTrue(orderBook.addOrder(order99Cent));
        assertEquals(99, order99Cent.getPrice());
        assertEquals(99, order99Cent.getNormalizedPrice());
        
        // Test NO at 1¢ (converts to Sell YES at 99¢)
        OrderBookEntry no1Cent = new OrderBookEntry("O3", "U3", KalshiSide.no, "buy", 1, 100, 3000);
        assertTrue(orderBook.addOrder(no1Cent));
        assertEquals(1, no1Cent.getPrice());
        assertEquals(99, no1Cent.getNormalizedPrice());
        assertFalse(no1Cent.isNormalizedBuy());
        
        // Test NO at 99¢ (converts to Sell YES at 1¢)
        OrderBookEntry no99Cent = new OrderBookEntry("O4", "U4", KalshiSide.no, "buy", 99, 100, 4000);
        assertTrue(orderBook.addOrder(no99Cent));
        assertEquals(99, no99Cent.getPrice());
        assertEquals(1, no99Cent.getNormalizedPrice());
        assertFalse(no99Cent.isNormalizedBuy());
    }
    
    @Test
    @DisplayName("Maximum arbitrage: YES bid 99¢ + NO bid 99¢ = 198¢")
    public void testMaximumArbitrage() {
        // This is the theoretical maximum arbitrage opportunity
        orderBook.addOrder(new OrderBookEntry("Y1", "U1", KalshiSide.yes, "buy", 99, 100, 1000));
        orderBook.addOrder(new OrderBookEntry("N1", "U2", KalshiSide.no, "buy", 99, 100, 2000));
        
        // Market maker could collect 198¢ and pay out max 100¢ = 98¢ profit per contract!
        // In reality, this would never happen due to market forces
    }
    
    @Test
    @DisplayName("Large quantity matching preserves FIFO")
    public void testLargeQuantityFIFO() {
        // Add multiple orders at same price
        for (int i = 1; i <= 10; i++) {
            orderBook.addOrder(new OrderBookEntry(
                "BUY-" + i, 
                "USER-" + i, 
                KalshiSide.yes, 
                "buy", 
                50, 
                100 * i, // Increasing quantities
                1000L * i
            ));
        }
        
        // Match with a large sell order
        OrderBookEntry largeSell = new OrderBookEntry("SELL-1", "U11", KalshiSide.yes, "sell", 50, 2000, 11000);
        List<Execution> executions = matchingEngine.matchOrder(largeSell, orderBook);
        
        // Should match in FIFO order
        int expectedMatches = 0;
        int remainingQty = 2000;
        for (int i = 1; i <= 10 && remainingQty > 0; i++) {
            expectedMatches++;
            remainingQty -= (100 * i);
        }
        
        assertEquals(expectedMatches, executions.size());
        
        // Verify FIFO order
        for (int i = 0; i < executions.size(); i++) {
            assertTrue(executions.get(i).getPassive().getOrderId().endsWith("-" + (i + 1)));
        }
    }
    
    @Test
    @DisplayName("Empty order book operations")
    public void testEmptyOrderBook() {
        // Test operations on empty book
        assertNull(orderBook.getBestBid());
        assertNull(orderBook.getBestAsk());
        assertNull(orderBook.getOrder("NON-EXISTENT"));
        assertFalse(orderBook.cancelOrder("NON-EXISTENT"));
        
        // Test matching against empty book
        OrderBookEntry testOrder = new OrderBookEntry("O1", "U1", KalshiSide.yes, "buy", 50, 100, 1000);
        List<Execution> executions = matchingEngine.matchOrder(testOrder, orderBook);
        assertTrue(executions.isEmpty());
    }
    
    @Test
    @DisplayName("Identical timestamps maintain sequence order")
    public void testIdenticalTimestamps() {
        long timestamp = System.currentTimeMillis();
        
        // Create orders with identical timestamps
        OrderBookEntry order1 = new OrderBookEntry("O1", "U1", KalshiSide.yes, "buy", 60, 100, timestamp);
        OrderBookEntry order2 = new OrderBookEntry("O2", "U2", KalshiSide.yes, "buy", 60, 100, timestamp);
        OrderBookEntry order3 = new OrderBookEntry("O3", "U3", KalshiSide.yes, "buy", 60, 100, timestamp);
        
        // Sequence numbers should still maintain order
        assertTrue(order1.getSequence() < order2.getSequence());
        assertTrue(order2.getSequence() < order3.getSequence());
        
        orderBook.addOrder(order1);
        orderBook.addOrder(order2);
        orderBook.addOrder(order3);
        
        // Match and verify FIFO based on sequence
        OrderBookEntry sellOrder = new OrderBookEntry("S1", "U4", KalshiSide.yes, "sell", 60, 250, timestamp + 1);
        List<Execution> executions = matchingEngine.matchOrder(sellOrder, orderBook);
        
        assertEquals(3, executions.size());
        assertEquals("O1", executions.get(0).getPassive().getOrderId());
        assertEquals("O2", executions.get(1).getPassive().getOrderId());
        assertEquals("O3", executions.get(2).getPassive().getOrderId());
    }
    
    @Test
    @DisplayName("All four order type combinations")
    public void testAllOrderTypeCombinations() {
        // Test all combinations: Buy YES, Sell YES, Buy NO, Sell NO
        
        // Buy YES at 70¢
        OrderBookEntry buyYes = new OrderBookEntry("BY", "U1", KalshiSide.yes, "buy", 70, 100, 1000);
        assertTrue(orderBook.addOrder(buyYes));
        assertEquals(70, buyYes.getNormalizedPrice());
        assertTrue(buyYes.isNormalizedBuy());
        
        // Sell YES at 75¢
        OrderBookEntry sellYes = new OrderBookEntry("SY", "U2", KalshiSide.yes, "sell", 75, 100, 2000);
        assertTrue(orderBook.addOrder(sellYes));
        assertEquals(75, sellYes.getNormalizedPrice());
        assertFalse(sellYes.isNormalizedBuy());
        
        // Buy NO at 25¢ (converts to Sell YES at 75¢)
        OrderBookEntry buyNo = new OrderBookEntry("BN", "U3", KalshiSide.no, "buy", 25, 100, 3000);
        assertTrue(orderBook.addOrder(buyNo));
        assertEquals(75, buyNo.getNormalizedPrice());
        assertFalse(buyNo.isNormalizedBuy());
        
        // Sell NO at 30¢ (converts to Buy YES at 70¢)
        OrderBookEntry sellNo = new OrderBookEntry("SN", "U4", KalshiSide.no, "sell", 30, 100, 4000);
        assertTrue(orderBook.addOrder(sellNo));
        assertEquals(70, sellNo.getNormalizedPrice());
        assertTrue(sellNo.isNormalizedBuy());
        
        // Verify order book state
        assertNotNull(orderBook.getBestBid());
        assertEquals(70, orderBook.getBestBid().getKey()); // Two buy orders at 70
        
        assertNotNull(orderBook.getBestAsk());
        assertEquals(75, orderBook.getBestAsk().getKey()); // Two sell orders at 75
    }
    
    @Test
    @DisplayName("Cancel non-existent order returns false")
    public void testCancelNonExistentOrder() {
        // Add an order
        orderBook.addOrder(new OrderBookEntry("O1", "U1", KalshiSide.yes, "buy", 50, 100, 1000));
        
        // Try to cancel non-existent order
        assertFalse(orderBook.cancelOrder("O2"));
        
        // Original order should still exist
        assertNotNull(orderBook.getOrder("O1"));
    }
    
    @Test
    @DisplayName("Multiple orders at same price from same user")
    public void testSameUserMultipleOrders() {
        // Same user places multiple orders at same price
        String userId = "USER-1";
        
        orderBook.addOrder(new OrderBookEntry("O1", userId, KalshiSide.yes, "buy", 60, 100, 1000));
        orderBook.addOrder(new OrderBookEntry("O2", userId, KalshiSide.yes, "buy", 60, 200, 2000));
        orderBook.addOrder(new OrderBookEntry("O3", userId, KalshiSide.yes, "buy", 60, 300, 3000));
        
        // Match against them
        OrderBookEntry sellOrder = new OrderBookEntry("S1", "U2", KalshiSide.yes, "sell", 60, 350, 4000);
        List<Execution> executions = matchingEngine.matchOrder(sellOrder, orderBook);
        
        // Should still maintain FIFO regardless of same user
        assertEquals(3, executions.size());
        assertEquals("O1", executions.get(0).getPassive().getOrderId());
        assertEquals("O2", executions.get(1).getPassive().getOrderId());
        assertEquals("O3", executions.get(2).getPassive().getOrderId());
        
        // Verify fill quantities
        assertEquals(100, executions.get(0).getQuantity()); // O1 fully filled
        assertEquals(200, executions.get(1).getQuantity()); // O2 fully filled
        assertEquals(50, executions.get(2).getQuantity());  // O3 partially filled
        
        // O3 should have remaining quantity
        assertEquals(250, orderBook.getOrder("O3").getQuantity());
    }
}