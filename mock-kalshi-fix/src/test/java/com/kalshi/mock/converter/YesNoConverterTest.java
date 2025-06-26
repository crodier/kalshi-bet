package com.kalshi.mock.converter;

import com.fbg.api.market.KalshiAction;
import com.fbg.api.market.KalshiSide;
import com.kalshi.mock.converter.YesNoConverter.ConvertedOrder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for YesNoConverter.
 * Verifies all order conversions work correctly for the buy-only architecture.
 */
public class YesNoConverterTest {
    
    // ==================== REST API Conversion Tests ====================
    
    @Test
    void testBuyYesRemainsUnchanged() {
        ConvertedOrder result = YesNoConverter.convertToBuyOnly(KalshiSide.yes, KalshiAction.buy, 60);
        
        assertEquals(KalshiSide.yes, result.getSide());
        assertEquals(KalshiAction.buy, result.getAction());
        assertEquals(60, result.getPrice());
        assertFalse(result.isWasConverted());
    }
    
    @Test
    void testBuyNoRemainsUnchanged() {
        ConvertedOrder result = YesNoConverter.convertToBuyOnly(KalshiSide.no, KalshiAction.buy, 40);
        
        assertEquals(KalshiSide.no, result.getSide());
        assertEquals(KalshiAction.buy, result.getAction());
        assertEquals(40, result.getPrice());
        assertFalse(result.isWasConverted());
    }
    
    @Test
    void testSellYesConvertsToBuyNo() {
        // Sell YES @ 70¢ → Buy NO @ 30¢
        ConvertedOrder result = YesNoConverter.convertToBuyOnly(KalshiSide.yes, KalshiAction.sell, 70);
        
        assertEquals(KalshiSide.no, result.getSide());
        assertEquals(KalshiAction.buy, result.getAction());
        assertEquals(30, result.getPrice());
        assertTrue(result.isWasConverted());
    }
    
    @Test
    void testSellNoConvertsToBuyYes() {
        // Sell NO @ 35¢ → Buy YES @ 65¢
        ConvertedOrder result = YesNoConverter.convertToBuyOnly(KalshiSide.no, KalshiAction.sell, 35);
        
        assertEquals(KalshiSide.yes, result.getSide());
        assertEquals(KalshiAction.buy, result.getAction());
        assertEquals(65, result.getPrice());
        assertTrue(result.isWasConverted());
    }
    
    // ==================== FIX Conversion Tests ====================
    
    @Test
    void testFixBuyRemainsYesBuy() {
        // FIX Buy (Side=1) @ 60 → Buy YES @ 60
        ConvertedOrder result = YesNoConverter.convertFixOrder("1", 60);
        
        assertEquals(KalshiSide.yes, result.getSide());
        assertEquals(KalshiAction.buy, result.getAction());
        assertEquals(60, result.getPrice());
        assertFalse(result.isWasConverted());
    }
    
    @Test
    void testFixSellConvertsToBuyNo() {
        // FIX Sell (Side=2) @ 70 → Buy NO @ 30
        ConvertedOrder result = YesNoConverter.convertFixOrder("2", 70);
        
        assertEquals(KalshiSide.no, result.getSide());
        assertEquals(KalshiAction.buy, result.getAction());
        assertEquals(30, result.getPrice());
        assertTrue(result.isWasConverted());
    }
    
    @Test
    void testFixInvalidSideThrows() {
        assertThrows(IllegalArgumentException.class, () -> 
            YesNoConverter.convertFixOrder("3", 50)
        );
        
        assertThrows(IllegalArgumentException.class, () -> 
            YesNoConverter.convertFixOrder("", 50)
        );
    }
    
    // ==================== Edge Case Tests ====================
    
    @ParameterizedTest
    @CsvSource({
        "yes, sell, 1, no, 99",     // Sell YES @ 1¢ → Buy NO @ 99¢
        "yes, sell, 99, no, 1",     // Sell YES @ 99¢ → Buy NO @ 1¢
        "no, sell, 1, yes, 99",     // Sell NO @ 1¢ → Buy YES @ 99¢
        "no, sell, 99, yes, 1",     // Sell NO @ 99¢ → Buy YES @ 1¢
        "yes, sell, 50, no, 50",    // Sell YES @ 50¢ → Buy NO @ 50¢
        "no, sell, 50, yes, 50"     // Sell NO @ 50¢ → Buy YES @ 50¢
    })
    void testEdgeCaseConversions(String side, String action, int price, 
                                  String expectedSide, int expectedPrice) {
        ConvertedOrder result = YesNoConverter.convertToBuyOnly(
            KalshiSide.valueOf(side), KalshiAction.valueOf(action), price);
        
        assertEquals(KalshiSide.valueOf(expectedSide), result.getSide());
        assertEquals(KalshiAction.buy, result.getAction());
        assertEquals(expectedPrice, result.getPrice());
        assertTrue(result.isWasConverted());
    }
    
    // ==================== Price Validation Tests ====================
    
    @Test
    void testInvalidPricesThrowException() {
        // Price too low
        assertThrows(IllegalArgumentException.class, () -> 
            YesNoConverter.convertToBuyOnly(KalshiSide.yes, KalshiAction.buy, 0)
        );
        
        // Price too high
        assertThrows(IllegalArgumentException.class, () -> 
            YesNoConverter.convertToBuyOnly(KalshiSide.yes, KalshiAction.buy, 100)
        );
        
        // Negative price
        assertThrows(IllegalArgumentException.class, () -> 
            YesNoConverter.convertToBuyOnly(KalshiSide.yes, KalshiAction.buy, -10)
        );
        
        // Way too high
        assertThrows(IllegalArgumentException.class, () -> 
            YesNoConverter.convertToBuyOnly(KalshiSide.yes, KalshiAction.buy, 150)
        );
    }
    
    // ==================== Display Conversion Tests ====================
    
    @ParameterizedTest
    @CsvSource({
        "30, 70",   // Buy NO @ 30¢ displays as Sell YES @ 70¢
        "40, 60",   // Buy NO @ 40¢ displays as Sell YES @ 60¢
        "1, 99",    // Buy NO @ 1¢ displays as Sell YES @ 99¢
        "99, 1",    // Buy NO @ 99¢ displays as Sell YES @ 1¢
        "50, 50"    // Buy NO @ 50¢ displays as Sell YES @ 50¢
    })
    void testBuyNoDisplayAsYesSell(int noPrice, int expectedYesSellPrice) {
        int displayPrice = YesNoConverter.getBuyNoAsYesSellPrice(noPrice);
        assertEquals(expectedYesSellPrice, displayPrice);
    }
    
    // ==================== Cross Detection Tests ====================
    
    @Test
    void testExternalCrossDetection() {
        // Buy YES @ 65, Buy NO @ 36 → Total 101 > 100 (CROSS!)
        assertTrue(YesNoConverter.checkExternalCross(65, 36));
        
        // Buy YES @ 60, Buy NO @ 40 → Total 100 (NO CROSS)
        assertFalse(YesNoConverter.checkExternalCross(60, 40));
        
        // Buy YES @ 55, Buy NO @ 44 → Total 99 < 100 (NO CROSS)
        assertFalse(YesNoConverter.checkExternalCross(55, 44));
        
        // Edge case: Buy YES @ 99, Buy NO @ 2 → Total 101 > 100 (CROSS!)
        assertTrue(YesNoConverter.checkExternalCross(99, 2));
    }
    
    // ==================== Debug String Tests ====================
    
    @Test
    void testDebugStringForUnconvertedOrder() {
        ConvertedOrder order = YesNoConverter.convertToBuyOnly(KalshiSide.yes, KalshiAction.buy, 60);
        String debug = order.toDebugString(KalshiSide.yes, KalshiAction.buy, 60);
        assertEquals("buy yes @ 60¢", debug);
    }
    
    @Test
    void testDebugStringForConvertedOrder() {
        // Sell YES @ 70 → Buy NO @ 30
        ConvertedOrder order = YesNoConverter.convertToBuyOnly(KalshiSide.yes, KalshiAction.sell, 70);
        String debug = order.toDebugString(KalshiSide.yes, KalshiAction.sell, 70);
        assertEquals("sell yes @ 70¢ → buy no @ 30¢", debug);
    }
    
    // ==================== Comprehensive Scenario Tests ====================
    
    @Test
    void testPacersExample() {
        // User wants to Sell YES on "Pacers win" at 70¢
        // This means they think Pacers will lose
        // Converts to Buy NO at 30¢
        ConvertedOrder result = YesNoConverter.convertToBuyOnly(KalshiSide.yes, KalshiAction.sell, 70);
        
        assertEquals(KalshiSide.no, result.getSide());
        assertEquals(KalshiAction.buy, result.getAction());
        assertEquals(30, result.getPrice());
        
        // Verify the economics are equivalent
        // Original: Sell YES @ 70¢ - receive 70¢, owe $1 if Pacers win
        // Converted: Buy NO @ 30¢ - pay 30¢, receive $1 if Pacers lose
        // Net position if Pacers win: -30¢ (same as 70¢ - $1.00)
        // Net position if Pacers lose: +70¢ (same as 70¢ - $0)
    }
    
    @Test
    void testAllActionResultsInBuy() {
        // Test that every conversion results in a buy action
        KalshiSide[] sides = {KalshiSide.yes, KalshiSide.no};
        KalshiAction[] actions = {KalshiAction.buy, KalshiAction.sell};
        int[] prices = {1, 25, 50, 75, 99};
        
        for (KalshiSide side : sides) {
            for (KalshiAction action : actions) {
                for (int price : prices) {
                    ConvertedOrder result = YesNoConverter.convertToBuyOnly(side, action, price);
                    assertEquals(KalshiAction.buy, result.getAction(), 
                        String.format("Failed for %s %s @ %d", action, side, price));
                }
            }
        }
    }
}