package com.kalshi.marketdata.service;

import com.fbg.api.kalshi.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Converts Kalshi format order book data to InternalOrderBook format
 * with granular timestamp tracking at every price level
 */
@Service
@Slf4j
public class OrderBookConverter {
    
    /**
     * Convert Kalshi orderbook snapshot to InternalOrderBook
     */
    public InternalOrderBook convertSnapshot(String marketTicker, Map<String, Object> kalshiData, 
                                           long receivedTimestamp, Long sequenceNumber) {
        
        InternalOrderBookBuilder builder = new InternalOrderBookBuilder(marketTicker, receivedTimestamp);
        
        if (sequenceNumber != null) {
            builder.setSequenceNumber(sequenceNumber);
        }
        
        // Process YES side (Buy YES orders)
        Object yesData = kalshiData.get("yes");
        if (yesData instanceof List<?> yesList) {
            for (Object levelObj : yesList) {
                if (levelObj instanceof List<?> level && level.size() >= 2) {
                    try {
                        int price = ((Number) level.get(0)).intValue();
                        long quantity = ((Number) level.get(1)).longValue();
                        builder.addYesLevel(price, quantity, UpdateType.SNAPSHOT);
                    } catch (Exception e) {
                        log.warn("Failed to parse YES level in snapshot for {}: {}", marketTicker, level, e);
                    }
                }
            }
        }
        
        // Process NO side (Buy NO orders)  
        Object noData = kalshiData.get("no");
        if (noData instanceof List<?> noList) {
            for (Object levelObj : noList) {
                if (levelObj instanceof List<?> level && level.size() >= 2) {
                    try {
                        int price = ((Number) level.get(0)).intValue();
                        long quantity = ((Number) level.get(1)).longValue();
                        builder.addNoLevel(price, quantity, UpdateType.SNAPSHOT);
                    } catch (Exception e) {
                        log.warn("Failed to parse NO level in snapshot for {}: {}", marketTicker, level, e);
                    }
                }
            }
        }
        
        InternalOrderBook result = builder.build();
        log.debug("Converted snapshot for {}: {} YES levels, {} NO levels", 
                marketTicker, result.getYesSide().getLevelCount(), result.getNoSide().getLevelCount());
        
        return result;
    }
    
    /**
     * Apply Kalshi delta update to existing InternalOrderBook
     */
    public InternalOrderBook applyDelta(InternalOrderBook existingBook, Map<String, Object> deltaData, 
                                      long receivedTimestamp, Long sequenceNumber) {
        
        String marketTicker = existingBook.getMarketTicker();
        
        // Extract delta information
        Object priceObj = deltaData.get("price");
        Object deltaObj = deltaData.get("delta");
        Object sideObj = deltaData.get("side");
        
        if (priceObj == null || deltaObj == null || sideObj == null) {
            log.warn("Invalid delta data for {}: missing price, delta, or side", marketTicker);
            return existingBook;
        }
        
        try {
            int price = ((Number) priceObj).intValue();
            long deltaQuantity = ((Number) deltaObj).longValue();
            String side = sideObj.toString();
            
            // Create builder from existing book
            InternalOrderBookBuilder builder = new InternalOrderBookBuilder(marketTicker, receivedTimestamp);
            if (sequenceNumber != null) {
                builder.setSequenceNumber(sequenceNumber);
            }
            
            // Copy existing levels
            copyExistingLevels(builder, existingBook);
            
            // Apply delta update
            if ("yes".equals(side)) {
                applyYesDelta(builder, existingBook, price, deltaQuantity);
            } else if ("no".equals(side)) {
                applyNoDelta(builder, existingBook, price, deltaQuantity);
            } else {
                log.warn("Unknown side '{}' in delta for {}", side, marketTicker);
                return existingBook;
            }
            
            InternalOrderBook result = builder.build();
            log.debug("Applied delta to {}: {} {} @ {} (delta: {})", 
                    marketTicker, side, price, deltaQuantity > 0 ? "ADD" : "REMOVE", Math.abs(deltaQuantity));
            
            return result;
            
        } catch (Exception e) {
            log.error("Failed to apply delta to {}: {}", marketTicker, deltaData, e);
            return existingBook;
        }
    }
    
    private void copyExistingLevels(InternalOrderBookBuilder builder, InternalOrderBook existingBook) {
        // Copy existing YES levels
        for (PriceLevel level : existingBook.getYesSide().getLevels().values()) {
            builder.addYesLevel(level.getPrice(), level.getQuantity(), level.getLastUpdateType());
        }
        
        // Copy existing NO levels
        for (PriceLevel level : existingBook.getNoSide().getLevels().values()) {
            builder.addNoLevel(level.getPrice(), level.getQuantity(), level.getLastUpdateType());
        }
    }
    
    private void applyYesDelta(InternalOrderBookBuilder builder, InternalOrderBook existingBook, 
                              int price, long deltaQuantity) {
        
        PriceLevel existingLevel = existingBook.getYesSide().getLevels().get(price);
        
        if (deltaQuantity > 0) {
            // Adding quantity
            if (existingLevel != null) {
                long newQuantity = existingLevel.getQuantity() + deltaQuantity;
                builder.addYesLevel(price, newQuantity, UpdateType.DELTA_MODIFY);
            } else {
                builder.addYesLevel(price, deltaQuantity, UpdateType.DELTA_ADD);
            }
        } else {
            // Removing quantity
            if (existingLevel != null) {
                long newQuantity = existingLevel.getQuantity() + deltaQuantity; // deltaQuantity is negative
                if (newQuantity <= 0) {
                    // Remove level entirely (don't add to builder)
                    log.debug("Removing YES level at price {} (quantity went to {})", price, newQuantity);
                } else {
                    builder.addYesLevel(price, newQuantity, UpdateType.DELTA_MODIFY);
                }
            } else {
                log.warn("Trying to remove quantity from non-existent YES level at price {}", price);
            }
        }
    }
    
    private void applyNoDelta(InternalOrderBookBuilder builder, InternalOrderBook existingBook, 
                             int price, long deltaQuantity) {
        
        PriceLevel existingLevel = existingBook.getNoSide().getLevels().get(price);
        
        if (deltaQuantity > 0) {
            // Adding quantity
            if (existingLevel != null) {
                long newQuantity = existingLevel.getQuantity() + deltaQuantity;
                builder.addNoLevel(price, newQuantity, UpdateType.DELTA_MODIFY);
            } else {
                builder.addNoLevel(price, deltaQuantity, UpdateType.DELTA_ADD);
            }
        } else {
            // Removing quantity
            if (existingLevel != null) {
                long newQuantity = existingLevel.getQuantity() + deltaQuantity; // deltaQuantity is negative
                if (newQuantity <= 0) {
                    // Remove level entirely (don't add to builder)
                    log.debug("Removing NO level at price {} (quantity went to {})", price, newQuantity);
                } else {
                    builder.addNoLevel(price, newQuantity, UpdateType.DELTA_MODIFY);
                }
            } else {
                log.warn("Trying to remove quantity from non-existent NO level at price {}", price);
            }
        }
    }
    
    /**
     * Convert InternalOrderBook back to simple map format for admin display
     */
    public Map<String, Object> convertToAdminFormat(InternalOrderBook internalBook) {
        Map<String, Object> result = new HashMap<>();
        
        result.put("marketTicker", internalBook.getMarketTicker());
        result.put("lastUpdateTimestamp", internalBook.getLastUpdateTimestamp());
        result.put("sequenceNumber", internalBook.getSequenceNumber());
        result.put("receivedTimestamp", internalBook.getReceivedTimestamp());
        result.put("processedTimestamp", internalBook.getProcessedTimestamp());
        result.put("processingLatency", internalBook.getProcessingLatency());
        
        // Convert YES side
        Map<String, Object> yesSide = new HashMap<>();
        Map<String, Object> yesLevels = new HashMap<>();
        for (Map.Entry<Integer, PriceLevel> entry : internalBook.getYesSide().getLevels().entrySet()) {
            PriceLevel level = entry.getValue();
            Map<String, Object> levelData = new HashMap<>();
            levelData.put("quantity", level.getQuantity());
            levelData.put("lastUpdateTimestamp", level.getLastUpdateTimestamp());
            levelData.put("lastUpdateType", level.getLastUpdateType().toString());
            levelData.put("age", level.getAgeMillis());
            levelData.put("isStale", level.isStale());
            yesLevels.put(entry.getKey().toString(), levelData);
        }
        yesSide.put("levels", yesLevels);
        yesSide.put("bestPrice", internalBook.getBestYesPrice());
        result.put("yesSide", yesSide);
        
        // Convert NO side
        Map<String, Object> noSide = new HashMap<>();
        Map<String, Object> noLevels = new HashMap<>();
        for (Map.Entry<Integer, PriceLevel> entry : internalBook.getNoSide().getLevels().entrySet()) {
            PriceLevel level = entry.getValue();
            Map<String, Object> levelData = new HashMap<>();
            levelData.put("quantity", level.getQuantity());
            levelData.put("lastUpdateTimestamp", level.getLastUpdateTimestamp());
            levelData.put("lastUpdateType", level.getLastUpdateType().toString());
            levelData.put("age", level.getAgeMillis());
            levelData.put("isStale", level.isStale());
            noLevels.put(entry.getKey().toString(), levelData);
        }
        noSide.put("levels", noLevels);
        noSide.put("bestPrice", internalBook.getBestNoPrice());
        result.put("noSide", noSide);
        
        return result;
    }
}