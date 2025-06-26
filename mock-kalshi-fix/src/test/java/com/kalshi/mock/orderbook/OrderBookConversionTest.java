package com.kalshi.mock.orderbook;

import com.fbg.api.market.KalshiSide;
import com.kalshi.mock.model.OrderBookEntry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for YES/NO order conversion logic as specified in the market dynamics documentation.
 * 
 * Key conversions:
 * - Buy NO @ P → Sell YES @ (100 - P)
 * - Sell NO @ P → Buy YES @ (100 - P)
 */
public class OrderBookConversionTest {
    
    @Test
    @DisplayName("Buy NO at 35¢ should convert to Sell YES at 65¢")
    public void testBuyNOConversion() {
        // Buy NO at 35¢
        OrderBookEntry order = new OrderBookEntry(
            "ORDER-1",
            "USER-1",
            KalshiSide.no,
            "buy",
            35,
            100,
            System.currentTimeMillis()
        );
        
        // Should convert to Sell YES at 65¢
        assertEquals(65, order.getNormalizedPrice());
        assertFalse(order.isNormalizedBuy());
    }
    
    @Test
    @DisplayName("Sell NO at 35¢ should convert to Buy YES at 65¢")
    public void testSellNOConversion() {
        // Sell NO at 35¢
        OrderBookEntry order = new OrderBookEntry(
            "ORDER-2",
            "USER-1",
            KalshiSide.no,
            "sell",
            35,
            100,
            System.currentTimeMillis()
        );
        
        // Should convert to Buy YES at 65¢
        assertEquals(65, order.getNormalizedPrice());
        assertTrue(order.isNormalizedBuy());
    }
    
    @Test
    @DisplayName("Buy YES at 65¢ should remain unchanged")
    public void testBuyYESNoConversion() {
        // Buy YES at 65¢
        OrderBookEntry order = new OrderBookEntry(
            "ORDER-3",
            "USER-1",
            KalshiSide.yes,
            "buy",
            65,
            100,
            System.currentTimeMillis()
        );
        
        // Should remain Buy YES at 65¢
        assertEquals(65, order.getNormalizedPrice());
        assertTrue(order.isNormalizedBuy());
    }
    
    @Test
    @DisplayName("Sell YES at 65¢ should remain unchanged")
    public void testSellYESNoConversion() {
        // Sell YES at 65¢
        OrderBookEntry order = new OrderBookEntry(
            "ORDER-4",
            "USER-1",
            KalshiSide.yes,
            "sell",
            65,
            100,
            System.currentTimeMillis()
        );
        
        // Should remain Sell YES at 65¢
        assertEquals(65, order.getNormalizedPrice());
        assertFalse(order.isNormalizedBuy());
    }
    
    @Test
    @DisplayName("NO price + YES equivalent should equal 100")
    public void testPriceSumEquals100() {
        // Test various NO prices
        int[] noPrices = {10, 25, 35, 50, 65, 75, 90};
        
        for (int noPrice : noPrices) {
            OrderBookEntry buyNO = new OrderBookEntry(
                "ORDER-" + noPrice,
                "USER-1",
                KalshiSide.no,
                "buy",
                noPrice,
                100,
                System.currentTimeMillis()
            );
            
            // NO price + converted YES price should equal 100
            assertEquals(100, noPrice + buyNO.getNormalizedPrice(),
                "NO price " + noPrice + " + YES price " + buyNO.getNormalizedPrice() + " should equal 100");
        }
    }
    
    @Test
    @DisplayName("Edge case: Buy NO at 1¢ converts to Sell YES at 99¢")
    public void testBuyNOAt1Cent() {
        OrderBookEntry order = new OrderBookEntry(
            "ORDER-5",
            "USER-1",
            KalshiSide.no,
            "buy",
            1,
            100,
            System.currentTimeMillis()
        );
        
        assertEquals(99, order.getNormalizedPrice());
        assertFalse(order.isNormalizedBuy());
    }
    
    @Test
    @DisplayName("Edge case: Buy NO at 99¢ converts to Sell YES at 1¢")
    public void testBuyNOAt99Cents() {
        OrderBookEntry order = new OrderBookEntry(
            "ORDER-6",
            "USER-1",
            KalshiSide.no,
            "buy",
            99,
            100,
            System.currentTimeMillis()
        );
        
        assertEquals(1, order.getNormalizedPrice());
        assertFalse(order.isNormalizedBuy());
    }
    
    @Test
    @DisplayName("Price validation: should reject price below 1¢")
    public void testRejectPriceBelow1() {
        assertThrows(IllegalArgumentException.class, () -> {
            new OrderBookEntry(
                "ORDER-7",
                "USER-1",
                KalshiSide.yes,
                "buy",
                0,
                100,
                System.currentTimeMillis()
            );
        });
    }
    
    @Test
    @DisplayName("Price validation: should reject price above 99¢")
    public void testRejectPriceAbove99() {
        assertThrows(IllegalArgumentException.class, () -> {
            new OrderBookEntry(
                "ORDER-8",
                "USER-1",
                KalshiSide.yes,
                "buy",
                100,
                100,
                System.currentTimeMillis()
            );
        });
    }
    
    @Test
    @DisplayName("Equivalence: Buy NO at P equals Sell YES at (100-P)")
    public void testOrderEquivalence() {
        // Create equivalent orders
        OrderBookEntry buyNO35 = new OrderBookEntry(
            "ORDER-9",
            "USER-1",
            KalshiSide.no,
            "buy",
            35,
            100,
            System.currentTimeMillis()
        );
        
        OrderBookEntry sellYES65 = new OrderBookEntry(
            "ORDER-10",
            "USER-1",
            KalshiSide.yes,
            "sell",
            65,
            100,
            System.currentTimeMillis()
        );
        
        // Both should have same normalized representation
        assertEquals(buyNO35.getNormalizedPrice(), sellYES65.getNormalizedPrice());
        assertEquals(buyNO35.isNormalizedBuy(), sellYES65.isNormalizedBuy());
    }
}