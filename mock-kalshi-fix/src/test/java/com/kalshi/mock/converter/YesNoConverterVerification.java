package com.kalshi.mock.converter;

import com.fbg.api.market.KalshiAction;
import com.fbg.api.market.KalshiSide;
import com.kalshi.mock.converter.YesNoConverter.ConvertedOrder;

/**
 * Quick verification of YesNoConverter behavior
 */
public class YesNoConverterVerification {
    
    public static void main(String[] args) {
        System.out.println("=== YesNoConverter Verification ===\n");
        
        // REST API Examples
        System.out.println("REST API Conversions:");
        System.out.println("--------------------");
        
        // Buy orders remain unchanged
        verifyConversion(KalshiSide.yes, KalshiAction.buy, 60, "Buy YES @ 60¢");
        verifyConversion(KalshiSide.no, KalshiAction.buy, 40, "Buy NO @ 40¢");
        
        // Sell orders get converted
        verifyConversion(KalshiSide.yes, KalshiAction.sell, 70, "Sell YES @ 70¢");
        verifyConversion(KalshiSide.no, KalshiAction.sell, 35, "Sell NO @ 35¢");
        
        System.out.println("\nFIX API Conversions (always YES):");
        System.out.println("---------------------------------");
        
        // FIX examples
        verifyFixConversion("1", 60, "FIX Buy @ 60");
        verifyFixConversion("2", 70, "FIX Sell @ 70");
        
        System.out.println("\nDisplay Conversions:");
        System.out.println("-------------------");
        
        // Buy NO display prices
        System.out.println("Buy NO @ 30¢ displays as Sell YES @ " + 
            YesNoConverter.getBuyNoAsYesSellPrice(30) + "¢");
        System.out.println("Buy NO @ 40¢ displays as Sell YES @ " + 
            YesNoConverter.getBuyNoAsYesSellPrice(40) + "¢");
        
        System.out.println("\nCross Detection:");
        System.out.println("----------------");
        
        // Cross examples
        checkCross(65, 36, "Buy YES @ 65¢ + Buy NO @ 36¢");
        checkCross(60, 40, "Buy YES @ 60¢ + Buy NO @ 40¢");
        checkCross(55, 44, "Buy YES @ 55¢ + Buy NO @ 44¢");
    }
    
    private static void verifyConversion(KalshiSide side, KalshiAction action, int price, String description) {
        ConvertedOrder result = YesNoConverter.convertToBuyOnly(side, action, price);
        System.out.printf("%-20s → %s\n", 
            description, 
            result.toDebugString(side, action, price));
    }
    
    private static void verifyFixConversion(String fixSide, int price, String description) {
        ConvertedOrder result = YesNoConverter.convertFixOrder(fixSide, price);
        KalshiAction original = fixSide.equals("1") ? KalshiAction.buy : KalshiAction.sell;
        System.out.printf("%-20s → %s\n", 
            description, 
            result.toDebugString(KalshiSide.yes, original, price));
    }
    
    private static void checkCross(int yesPrice, int noPrice, String description) {
        boolean crosses = YesNoConverter.checkExternalCross(yesPrice, noPrice);
        int total = yesPrice + noPrice;
        System.out.printf("%-40s = %d¢ %s\n", 
            description, 
            total,
            crosses ? "CROSSES!" : "(no cross)");
    }
}