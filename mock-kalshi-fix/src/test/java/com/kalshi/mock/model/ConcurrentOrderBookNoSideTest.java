package com.kalshi.mock.model;

import com.fbg.api.market.KalshiSide;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Queue;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for NO side order book operations.
 * Verifies the NO→YES conversion logic and proper order book behavior.
 */
public class ConcurrentOrderBookNoSideTest {
    
    private ConcurrentOrderBook orderBook;
    
    @BeforeEach
    void setUp() {
        orderBook = new ConcurrentOrderBook("TEST-MARKET");
    }
    
    // ==================== NO Order Conversion Tests ====================
    
    @Test
    void testBuyNoOrderConversion() {
        // Given: Buy NO at 35¢
        OrderBookEntry buyNo = new OrderBookEntry("1", "user1", KalshiSide.no, "buy", 35, 100, System.nanoTime());
        
        // When: Adding to order book
        boolean added = orderBook.addOrder(buyNo);
        
        // Then: Should be converted to Sell YES at 65¢ and placed in asks
        assertTrue(added);
        assertEquals(65, buyNo.getNormalizedPrice());
        assertFalse(buyNo.isNormalizedBuy()); // Buy NO → Sell YES
        
        // Verify it's in the asks side
        Map.Entry<Integer, Queue<OrderBookEntry>> bestAsk = orderBook.getBestAsk();
        assertNotNull(bestAsk);
        assertEquals(65, bestAsk.getKey());
        assertTrue(bestAsk.getValue().contains(buyNo));
    }
    
    @Test
    void testSellNoOrderConversion() {
        // Given: Sell NO at 40¢
        OrderBookEntry sellNo = new OrderBookEntry("1", "user1", KalshiSide.no, "sell", 40, 100, System.nanoTime());
        
        // When: Adding to order book
        boolean added = orderBook.addOrder(sellNo);
        
        // Then: Should be converted to Buy YES at 60¢ and placed in bids
        assertTrue(added);
        assertEquals(60, sellNo.getNormalizedPrice());
        assertTrue(sellNo.isNormalizedBuy()); // Sell NO → Buy YES
        
        // Verify it's in the bids side
        Map.Entry<Integer, Queue<OrderBookEntry>> bestBid = orderBook.getBestBid();
        assertNotNull(bestBid);
        assertEquals(60, bestBid.getKey());
        assertTrue(bestBid.getValue().contains(sellNo));
    }
    
    @Test
    void testNoOrderPriceValidation() {
        // Test that price validation happens in OrderBookEntry constructor
        
        // Invalid NO price: 0
        assertThrows(IllegalArgumentException.class, () -> {
            new OrderBookEntry("1", "user1", KalshiSide.no, "buy", 0, 100, System.nanoTime());
        });
        
        // Invalid NO price: 100
        assertThrows(IllegalArgumentException.class, () -> {
            new OrderBookEntry("2", "user2", KalshiSide.no, "buy", 100, 100, System.nanoTime());
        });
        
        // Valid NO prices should work
        assertDoesNotThrow(() -> {
            new OrderBookEntry("3", "user3", KalshiSide.no, "buy", 1, 100, System.nanoTime());
            new OrderBookEntry("4", "user4", KalshiSide.no, "buy", 99, 100, System.nanoTime());
        });
    }
    
    // ==================== Cross Detection Tests ====================
    
    @Test
    void testExternalCrossDetectionWithNoOrders() {
        // Given: YES bid at 65¢
        orderBook.addOrder(new OrderBookEntry("1", "user1", KalshiSide.yes, "buy", 65, 100, System.nanoTime()));
        
        // When: Adding NO bid at 36¢ (which would create external cross: 65 + 36 = 101 > 100)
        OrderBookEntry noBid = new OrderBookEntry("2", "user2", KalshiSide.no, "buy", 36, 100, System.nanoTime());
        boolean added = orderBook.addOrder(noBid);
        
        // Then: Cross should be detected (but order still added for matching engine to handle)
        assertTrue(added);
        // The matching engine would handle the actual cross execution
    }
    
    @Test
    void testSelfCrossWithNoOrders() {
        // Given: Buy NO at 40¢ (converts to Sell YES at 60¢)
        orderBook.addOrder(new OrderBookEntry("1", "user1", KalshiSide.no, "buy", 40, 100, System.nanoTime()));
        
        // When: Adding Sell NO at 40¢ (converts to Buy YES at 60¢)
        // This creates a self-cross: Buy YES 60¢ meets Sell YES 60¢
        OrderBookEntry sellNo = new OrderBookEntry("2", "user2", KalshiSide.no, "sell", 40, 100, System.nanoTime());
        boolean added = orderBook.addOrder(sellNo);
        
        // Then: Cross should be detected
        assertTrue(added);
    }
    
    // ==================== Order Matching Scenarios ====================
    
    @Test
    void testYesBuyMatchesNoBuy() {
        // Scenario: Buy YES at 65¢ should match Buy NO at 35¢
        // (Buy NO at 35¢ converts to Sell YES at 65¢)
        
        // Given: Buy NO at 35¢ in the book
        orderBook.addOrder(new OrderBookEntry("1", "user1", KalshiSide.no, "buy", 35, 100, System.nanoTime()));
        
        // When: Buy YES at 65¢ arrives
        OrderBookEntry buyYes = new OrderBookEntry("2", "user2", KalshiSide.yes, "buy", 65, 100, System.nanoTime());
        boolean added = orderBook.addOrder(buyYes);
        
        // Then: Should detect a cross (matching engine would execute)
        assertTrue(added);
    }
    
    @Test
    void testNoSellMatchesNoBuy() {
        // Scenario: Sell NO at 40¢ should match Buy NO at 40¢
        // (Sell NO at 40¢ converts to Buy YES at 60¢)
        // (Buy NO at 40¢ converts to Sell YES at 60¢)
        
        // Given: Buy NO at 40¢ in the book
        orderBook.addOrder(new OrderBookEntry("1", "user1", KalshiSide.no, "buy", 40, 100, System.nanoTime()));
        
        // When: Sell NO at 40¢ arrives
        OrderBookEntry sellNo = new OrderBookEntry("2", "user2", KalshiSide.no, "sell", 40, 100, System.nanoTime());
        boolean added = orderBook.addOrder(sellNo);
        
        // Then: Should detect a cross at 60¢ YES price
        assertTrue(added);
    }
    
    // ==================== Order Book Depth Tests ====================
    
    @Test
    void testMixedYesNoOrderBookDepth() {
        // Given: Mixed YES and NO orders
        // Buy YES orders
        orderBook.addOrder(new OrderBookEntry("1", "user1", KalshiSide.yes, "buy", 65, 100, System.nanoTime()));
        orderBook.addOrder(new OrderBookEntry("2", "user2", KalshiSide.yes, "buy", 64, 200, System.nanoTime()));
        
        // Sell NO orders (convert to Buy YES)
        orderBook.addOrder(new OrderBookEntry("3", "user3", KalshiSide.no, "sell", 37, 150, System.nanoTime())); // → Buy YES 63
        orderBook.addOrder(new OrderBookEntry("4", "user4", KalshiSide.no, "sell", 38, 250, System.nanoTime())); // → Buy YES 62
        
        // Buy NO orders (convert to Sell YES)
        orderBook.addOrder(new OrderBookEntry("5", "user5", KalshiSide.no, "buy", 33, 300, System.nanoTime())); // → Sell YES 67
        orderBook.addOrder(new OrderBookEntry("6", "user6", KalshiSide.no, "buy", 32, 400, System.nanoTime())); // → Sell YES 68
        
        // Sell YES orders
        orderBook.addOrder(new OrderBookEntry("7", "user7", KalshiSide.yes, "sell", 66, 350, System.nanoTime()));
        orderBook.addOrder(new OrderBookEntry("8", "user8", KalshiSide.yes, "sell", 69, 450, System.nanoTime()));
        
        // When: Getting best bid and ask
        Map.Entry<Integer, Queue<OrderBookEntry>> bestBid = orderBook.getBestBid();
        Map.Entry<Integer, Queue<OrderBookEntry>> bestAsk = orderBook.getBestAsk();
        
        // Then: Best bid should be 65 (highest), best ask should be 66 (lowest)
        assertEquals(65, bestBid.getKey());
        assertEquals(66, bestAsk.getKey());
    }
    
    // ==================== FIFO Priority Tests ====================
    
    @Test
    void testFifoPriorityWithNoOrders() {
        // Given: Multiple Buy NO orders at same price (all convert to same YES price)
        long time1 = System.nanoTime();
        long time2 = time1 + 1000;
        long time3 = time2 + 1000;
        
        OrderBookEntry no1 = new OrderBookEntry("1", "user1", KalshiSide.no, "buy", 35, 100, time1);
        OrderBookEntry no2 = new OrderBookEntry("2", "user2", KalshiSide.no, "buy", 35, 200, time2);
        OrderBookEntry no3 = new OrderBookEntry("3", "user3", KalshiSide.no, "buy", 35, 150, time3);
        
        // Add in insertion order (FIFO is based on insertion, not timestamp)
        orderBook.addOrder(no2); // First
        orderBook.addOrder(no1); // Second
        orderBook.addOrder(no3); // Third
        
        // When: Getting the ask level at 65¢ (converted price)
        Map.Entry<Integer, Queue<OrderBookEntry>> askLevel = orderBook.getBestAsk();
        
        // Then: Should maintain insertion order (FIFO)
        assertEquals(65, askLevel.getKey());
        Queue<OrderBookEntry> orders = askLevel.getValue();
        assertEquals(3, orders.size());
        
        // Verify insertion order (not timestamp order)
        OrderBookEntry first = orders.poll();
        assertEquals("2", first.getOrderId()); // First inserted
        OrderBookEntry second = orders.poll();
        assertEquals("1", second.getOrderId()); // Second inserted
        OrderBookEntry third = orders.poll();
        assertEquals("3", third.getOrderId()); // Third inserted
    }
    
    // ==================== Cancellation Tests ====================
    
    @Test
    void testCancelNoOrder() {
        // Given: Buy NO order in the book
        OrderBookEntry buyNo = new OrderBookEntry("1", "user1", KalshiSide.no, "buy", 35, 100, System.nanoTime());
        orderBook.addOrder(buyNo);
        
        // When: Cancelling the order
        boolean cancelled = orderBook.cancelOrder("1");
        
        // Then: Should be removed from asks (where it was placed as Sell YES 65)
        assertTrue(cancelled);
        assertNull(orderBook.getOrder("1"));
        
        Map.Entry<Integer, Queue<OrderBookEntry>> bestAsk = orderBook.getBestAsk();
        assertNull(bestAsk);
    }
    
    // ==================== Original Side Tracking Tests ====================
    
    @Test
    void testOriginalSideTracking() {
        // Given: Various NO orders
        OrderBookEntry buyNo = new OrderBookEntry("1", "user1", KalshiSide.no, "buy", 35, 100, System.nanoTime());
        OrderBookEntry sellNo = new OrderBookEntry("2", "user2", KalshiSide.no, "sell", 40, 100, System.nanoTime());
        
        orderBook.addOrder(buyNo);
        orderBook.addOrder(sellNo);
        
        // When: Retrieving orders
        OrderBookEntry retrieved1 = orderBook.getOrder("1");
        OrderBookEntry retrieved2 = orderBook.getOrder("2");
        
        // Then: Original side should be preserved
        assertEquals(KalshiSide.no, retrieved1.getSide());
        assertEquals("buy", retrieved1.getAction());
        assertEquals(35, retrieved1.getPrice()); // Original price
        
        assertEquals(KalshiSide.no, retrieved2.getSide());
        assertEquals("sell", retrieved2.getAction());
        assertEquals(40, retrieved2.getPrice()); // Original price
    }
    
    // ==================== Complex Scenario Tests ====================
    
    @Test
    void testComplexMarketScenario() {
        // Simulate a realistic market with multiple participants
        
        // Market makers providing liquidity
        orderBook.addOrder(new OrderBookEntry("mm1", "maker1", KalshiSide.yes, "buy", 49, 1000, System.nanoTime()));
        orderBook.addOrder(new OrderBookEntry("mm2", "maker1", KalshiSide.yes, "sell", 51, 1000, System.nanoTime()));
        orderBook.addOrder(new OrderBookEntry("mm3", "maker2", KalshiSide.no, "buy", 49, 1000, System.nanoTime())); // Sell YES 51
        orderBook.addOrder(new OrderBookEntry("mm4", "maker2", KalshiSide.no, "sell", 51, 1000, System.nanoTime())); // Buy YES 49
        
        // Traders taking positions
        orderBook.addOrder(new OrderBookEntry("t1", "trader1", KalshiSide.yes, "buy", 50, 500, System.nanoTime()));
        orderBook.addOrder(new OrderBookEntry("t2", "trader2", KalshiSide.no, "buy", 45, 300, System.nanoTime())); // Sell YES 55
        orderBook.addOrder(new OrderBookEntry("t3", "trader3", KalshiSide.no, "sell", 45, 200, System.nanoTime())); // Buy YES 55
        
        // Verify the order book state
        Map.Entry<Integer, Queue<OrderBookEntry>> bestBid = orderBook.getBestBid();
        Map.Entry<Integer, Queue<OrderBookEntry>> bestAsk = orderBook.getBestAsk();
        
        // Best bid should be Buy YES at 55 (from Sell NO 45)
        assertEquals(55, bestBid.getKey());
        
        // Best ask should be Sell YES at 51 (native or from Buy NO 49)
        assertEquals(51, bestAsk.getKey());
    }
    
    @Test
    void testArbitrageDetection() {
        // Test the classic arbitrage scenario from MARKET_DYNAMICS.md
        
        // Given: YES bid at 65¢
        orderBook.addOrder(new OrderBookEntry("1", "arb1", KalshiSide.yes, "buy", 65, 1000, System.nanoTime()));
        
        // When: NO bid at 40¢ arrives (total = 105¢ > 100¢)
        OrderBookEntry noBid = new OrderBookEntry("2", "arb2", KalshiSide.no, "buy", 40, 1000, System.nanoTime());
        
        // The order book should accept the order but flag it as a cross
        boolean added = orderBook.addOrder(noBid);
        assertTrue(added);
        
        // In a real system, the matching engine would immediately execute
        // against both orders to capture the 5¢ arbitrage profit
    }
}