package com.kalshi.mock.model;

import com.fbg.api.market.KalshiSide;
import com.kalshi.mock.dto.OrderbookResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test the Kalshi format order book output to ensure proper YES/NO separation
 */
public class OrderBookKalshiFormatTest {
    
    private ConcurrentOrderBook orderBook;
    
    @BeforeEach
    void setUp() {
        orderBook = new ConcurrentOrderBook("TEST-MARKET");
    }
    
    @Test
    void testBuyYesOrdersAppearInYesArray() {
        // Given: Buy YES orders at various prices
        orderBook.addOrder(new OrderBookEntry("1", "user1", KalshiSide.yes, "buy", 45, 100, System.nanoTime()));
        orderBook.addOrder(new OrderBookEntry("2", "user2", KalshiSide.yes, "buy", 44, 200, System.nanoTime()));
        orderBook.addOrder(new OrderBookEntry("3", "user3", KalshiSide.yes, "buy", 43, 150, System.nanoTime()));
        
        // When: Getting orderbook in Kalshi format
        OrderbookResponse.OrderbookData data = orderBook.getOrderbookSnapshotKalshiFormat(10);
        
        // Then: YES array should contain the buy orders sorted by price descending
        assertNotNull(data.getYes());
        assertEquals(3, data.getYes().size());
        
        // Verify order and values
        assertEquals(45, data.getYes().get(0).get(0)); // Highest price first
        assertEquals(100, data.getYes().get(0).get(1));
        
        assertEquals(44, data.getYes().get(1).get(0));
        assertEquals(200, data.getYes().get(1).get(1));
        
        assertEquals(43, data.getYes().get(2).get(0));
        assertEquals(150, data.getYes().get(2).get(1));
        
        // NO array should be empty
        assertTrue(data.getNo().isEmpty());
    }
    
    @Test
    void testBuyNoOrdersAppearInNoArray() {
        // Given: Buy NO orders at various prices
        // These are stored internally as Sell YES at (100-price)
        orderBook.addOrder(new OrderBookEntry("1", "user1", KalshiSide.no, "buy", 40, 100, System.nanoTime()));
        orderBook.addOrder(new OrderBookEntry("2", "user2", KalshiSide.no, "buy", 45, 200, System.nanoTime()));
        orderBook.addOrder(new OrderBookEntry("3", "user3", KalshiSide.no, "buy", 50, 150, System.nanoTime()));
        
        // When: Getting orderbook in Kalshi format
        OrderbookResponse.OrderbookData data = orderBook.getOrderbookSnapshotKalshiFormat(10);
        
        // Then: NO array should contain the buy NO orders sorted by price ascending
        assertNotNull(data.getNo());
        assertEquals(3, data.getNo().size());
        
        // Verify order and values (sorted ascending for NO side)
        assertEquals(40, data.getNo().get(0).get(0)); // Lowest price first
        assertEquals(100, data.getNo().get(0).get(1));
        
        assertEquals(45, data.getNo().get(1).get(0));
        assertEquals(200, data.getNo().get(1).get(1));
        
        assertEquals(50, data.getNo().get(2).get(0));
        assertEquals(150, data.getNo().get(2).get(1));
        
        // YES array should be empty
        assertTrue(data.getYes().isEmpty());
    }
    
    @Test
    void testMixedOrdersProperSeparation() {
        // Given: Mix of Buy YES and Buy NO orders
        orderBook.addOrder(new OrderBookEntry("1", "user1", KalshiSide.yes, "buy", 55, 100, System.nanoTime()));
        orderBook.addOrder(new OrderBookEntry("2", "user2", KalshiSide.yes, "buy", 54, 150, System.nanoTime()));
        orderBook.addOrder(new OrderBookEntry("3", "user3", KalshiSide.no, "buy", 40, 200, System.nanoTime()));
        orderBook.addOrder(new OrderBookEntry("4", "user4", KalshiSide.no, "buy", 45, 250, System.nanoTime()));
        
        // When: Getting orderbook in Kalshi format
        OrderbookResponse.OrderbookData data = orderBook.getOrderbookSnapshotKalshiFormat(10);
        
        // Then: Orders should be properly separated
        assertEquals(2, data.getYes().size());
        assertEquals(2, data.getNo().size());
        
        // YES side - Buy YES orders (descending)
        assertEquals(55, data.getYes().get(0).get(0));
        assertEquals(100, data.getYes().get(0).get(1));
        assertEquals(54, data.getYes().get(1).get(0));
        assertEquals(150, data.getYes().get(1).get(1));
        
        // NO side - Buy NO orders (ascending)
        assertEquals(40, data.getNo().get(0).get(0));
        assertEquals(200, data.getNo().get(0).get(1));
        assertEquals(45, data.getNo().get(1).get(0));
        assertEquals(250, data.getNo().get(1).get(1));
    }
    
    @Test
    void testOnlyBuyOrdersInKalshiFormat() {
        // Given: Mix of buy and sell orders
        // Buy YES orders
        orderBook.addOrder(new OrderBookEntry("1", "user1", KalshiSide.yes, "buy", 45, 100, System.nanoTime()));
        // Buy NO orders  
        orderBook.addOrder(new OrderBookEntry("2", "user2", KalshiSide.no, "buy", 40, 200, System.nanoTime()));
        // Sell YES orders (these should NOT appear in the output)
        orderBook.addOrder(new OrderBookEntry("3", "user3", KalshiSide.yes, "sell", 60, 150, System.nanoTime()));
        // Sell NO orders (converted to Buy YES at 65, so should appear in YES array)
        orderBook.addOrder(new OrderBookEntry("4", "user4", KalshiSide.no, "sell", 35, 250, System.nanoTime()));
        
        // When: Getting orderbook in Kalshi format
        OrderbookResponse.OrderbookData data = orderBook.getOrderbookSnapshotKalshiFormat(10);
        
        // Then: 
        // YES array should have Buy YES (45) and converted Sell NO (65)
        assertEquals(2, data.getYes().size());
        assertEquals(65, data.getYes().get(0).get(0)); // Sell NO at 35 = Buy YES at 65
        assertEquals(250, data.getYes().get(0).get(1));
        assertEquals(45, data.getYes().get(1).get(0)); // Buy YES at 45
        assertEquals(100, data.getYes().get(1).get(1));
        
        // NO array should only have Buy NO orders
        assertEquals(1, data.getNo().size());
        assertEquals(40, data.getNo().get(0).get(0));
        assertEquals(200, data.getNo().get(0).get(1));
    }
    
    @Test
    void testDepthLimitRespected() {
        // Given: Many orders but limited depth
        for (int i = 0; i < 10; i++) {
            orderBook.addOrder(new OrderBookEntry("yes" + i, "user", KalshiSide.yes, "buy", 50 + i, 100, System.nanoTime()));
            orderBook.addOrder(new OrderBookEntry("no" + i, "user", KalshiSide.no, "buy", 40 + i, 100, System.nanoTime()));
        }
        
        // When: Getting orderbook with depth of 3
        OrderbookResponse.OrderbookData data = orderBook.getOrderbookSnapshotKalshiFormat(3);
        
        // Then: Should only return top 3 levels for each side
        assertEquals(3, data.getYes().size());
        assertEquals(3, data.getNo().size());
        
        // YES side should have highest 3 prices (59, 58, 57)
        assertEquals(59, data.getYes().get(0).get(0));
        assertEquals(58, data.getYes().get(1).get(0));
        assertEquals(57, data.getYes().get(2).get(0));
        
        // NO side should have lowest 3 prices (40, 41, 42)
        assertEquals(40, data.getNo().get(0).get(0));
        assertEquals(41, data.getNo().get(1).get(0));
        assertEquals(42, data.getNo().get(2).get(0));
    }
    
    @Test
    void testPriceAggregation() {
        // Given: Multiple orders at same price level
        orderBook.addOrder(new OrderBookEntry("1", "user1", KalshiSide.yes, "buy", 50, 100, System.nanoTime()));
        orderBook.addOrder(new OrderBookEntry("2", "user2", KalshiSide.yes, "buy", 50, 200, System.nanoTime()));
        orderBook.addOrder(new OrderBookEntry("3", "user3", KalshiSide.yes, "buy", 50, 150, System.nanoTime()));
        
        // When: Getting orderbook in Kalshi format
        OrderbookResponse.OrderbookData data = orderBook.getOrderbookSnapshotKalshiFormat(10);
        
        // Then: Quantities should be aggregated at price level
        assertEquals(1, data.getYes().size());
        assertEquals(50, data.getYes().get(0).get(0));
        assertEquals(450, data.getYes().get(0).get(1)); // 100 + 200 + 150
    }
}