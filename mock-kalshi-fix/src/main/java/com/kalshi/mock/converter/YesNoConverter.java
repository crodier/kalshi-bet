package com.kalshi.mock.converter;

import com.fbg.api.market.KalshiAction;
import com.fbg.api.market.KalshiSide;
import lombok.Value;

/**
 * Converts all orders to Buy-only format following Kalshi's architecture.
 * 
 * Key conversions:
 * - Sell YES @ X → Buy NO @ (100-X)
 * - Sell NO @ X → Buy YES @ (100-X)
 * - Buy orders remain unchanged
 * 
 * This ensures the order book only contains Buy orders on both YES and NO sides.
 */
public class YesNoConverter {
    
    /**
     * Convert any order to buy-only format.
     * 
     * @param side The market side (yes or no)
     * @param action The action (buy or sell)
     * @param price The price in cents (1-99)
     * @return ConvertedOrder with buy action and appropriate side/price
     */
    public static ConvertedOrder convertToBuyOnly(KalshiSide side, KalshiAction action, int price) {
        validatePrice(price);
        
        if (action == KalshiAction.buy) {
            // Already a buy, no conversion needed
            return new ConvertedOrder(side, KalshiAction.buy, price, false);
        }
        
        // Convert sells to buys on opposite side
        if (side == KalshiSide.yes) {
            // Sell YES @ X → Buy NO @ (100-X)
            return new ConvertedOrder(KalshiSide.no, KalshiAction.buy, 100 - price, true);
        } else {
            // Sell NO @ X → Buy YES @ (100-X)
            return new ConvertedOrder(KalshiSide.yes, KalshiAction.buy, 100 - price, true);
        }
    }
    
    /**
     * Convert FIX orders to buy-only format.
     * FIX orders always come as YES side only.
     * 
     * @param fixSide FIX side value ("1" for Buy, "2" for Sell)
     * @param price The price in cents (1-99)
     * @return ConvertedOrder with buy action and appropriate side/price
     */
    public static ConvertedOrder convertFixOrder(String fixSide, int price) {
        validatePrice(price);
        
        if ("1".equals(fixSide)) { // FIX Buy
            // Buy YES stays as Buy YES
            return new ConvertedOrder(KalshiSide.yes, KalshiAction.buy, price, false);
        } else if ("2".equals(fixSide)) { // FIX Sell
            // Sell YES → Buy NO @ (100-price)
            return new ConvertedOrder(KalshiSide.no, KalshiAction.buy, 100 - price, true);
        } else {
            throw new IllegalArgumentException("Invalid FIX side: " + fixSide);
        }
    }
    
    /**
     * For display purposes - convert Buy NO price to equivalent Sell YES price.
     * This is used when displaying Buy NO orders as asks in the UI.
     * 
     * @param noPrice The Buy NO price
     * @return The equivalent Sell YES price
     */
    public static int getBuyNoAsYesSellPrice(int noPrice) {
        validatePrice(noPrice);
        return 100 - noPrice;
    }
    
    /**
     * Check if two orders can match (external cross).
     * Buy YES @ X and Buy NO @ Y match when X + Y > 100
     * 
     * @param yesBuyPrice Price of Buy YES order
     * @param noBuyPrice Price of Buy NO order
     * @return true if orders cross
     */
    public static boolean checkExternalCross(int yesBuyPrice, int noBuyPrice) {
        return yesBuyPrice + noBuyPrice > 100;
    }
    
    /**
     * Validate price is in valid range (1-99 cents)
     */
    private static void validatePrice(int price) {
        if (price < 1 || price > 99) {
            throw new IllegalArgumentException("Price must be between 1 and 99 cents, got: " + price);
        }
    }
    
    /**
     * Result of order conversion.
     * Contains the converted order details and whether conversion occurred.
     */
    @Value
    public static class ConvertedOrder {
        KalshiSide side;           // yes or no
        KalshiAction action;       // always buy
        int price;                 // converted price
        boolean wasConverted;      // true if sell was converted to buy
        
        /**
         * Get a debug string showing the conversion
         */
        public String toDebugString(KalshiSide originalSide, KalshiAction originalAction, int originalPrice) {
            if (!wasConverted) {
                return String.format("%s %s @ %d¢", originalAction, originalSide, originalPrice);
            } else {
                return String.format("%s %s @ %d¢ → %s %s @ %d¢", 
                    originalAction, originalSide, originalPrice,
                    action, side, price);
            }
        }
    }
}