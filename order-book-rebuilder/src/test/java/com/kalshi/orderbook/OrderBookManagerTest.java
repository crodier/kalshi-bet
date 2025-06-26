package com.kalshi.orderbook;

import com.kalshi.orderbook.model.OrderBook;
import com.kalshi.orderbook.model.OrderBookLevel;
import com.kalshi.orderbook.service.OrderBookManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OrderBookManagerTest {
    
    private OrderBookManager orderBookManager;
    
    @BeforeEach
    void setUp() {
        orderBookManager = new OrderBookManager();
    }
    
    @Test
    void testCreateAndRetrieveOrderBook() {
        String marketTicker = "TEST_MARKET";
        
        OrderBook orderBook = orderBookManager.getOrCreateOrderBook(marketTicker);
        assertNotNull(orderBook);
        assertEquals(marketTicker, orderBook.getMarketTicker());
        
        // Should return same instance
        OrderBook sameOrderBook = orderBookManager.getOrderBook(marketTicker);
        assertSame(orderBook, sameOrderBook);
    }
    
    @Test
    void testOrderBookOperations() {
        OrderBook orderBook = orderBookManager.getOrCreateOrderBook("TEST");
        
        // Add yes side levels
        orderBook.updateYesSide(45, 1000, System.currentTimeMillis());
        orderBook.updateYesSide(44, 2000, System.currentTimeMillis());
        orderBook.updateYesSide(43, 3000, System.currentTimeMillis());
        
        // Add no side levels
        orderBook.updateNoSide(47, 1500, System.currentTimeMillis());
        orderBook.updateNoSide(48, 2500, System.currentTimeMillis());
        orderBook.updateNoSide(49, 3500, System.currentTimeMillis());
        
        // Test best yes/no
        OrderBookLevel bestYes = orderBook.getBestYes();
        OrderBookLevel bestNo = orderBook.getBestNo();
        
        assertNotNull(bestYes);
        assertNotNull(bestNo);
        assertEquals(45, bestYes.getPrice());
        assertEquals(47, bestNo.getPrice());
        
        // Test top levels
        var topYes = orderBook.getTopYes(2);
        var topNo = orderBook.getTopNo(2);
        
        assertEquals(2, topYes.size());
        assertEquals(2, topNo.size());
        assertEquals(45, topYes.get(0).getPrice());
        assertEquals(44, topYes.get(1).getPrice());
        
        // Remove a level
        orderBook.updateYesSide(45, 0, System.currentTimeMillis());
        bestYes = orderBook.getBestYes();
        assertEquals(44, bestYes.getPrice());
    }
}