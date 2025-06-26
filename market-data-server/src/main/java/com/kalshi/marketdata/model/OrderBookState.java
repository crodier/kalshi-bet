package com.kalshi.marketdata.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Represents the current state of an order book for a specific market
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderBookState {
    
    private String marketTicker;
    private Long lastSequence;
    private Long lastUpdateTimestamp;
    
    // Price -> Quantity mapping for YES side (using TreeMap for sorted prices)
    private TreeMap<Integer, Integer> yesBids = new TreeMap<>();
    
    // Price -> Quantity mapping for NO side
    private TreeMap<Integer, Integer> noBids = new TreeMap<>();
    
    /**
     * Apply a snapshot to completely replace the order book state
     */
    public void applySnapshot(Map<String, Object> snapshotData, Long sequence) {
        this.lastSequence = sequence;
        this.lastUpdateTimestamp = System.currentTimeMillis();
        
        // Clear existing state
        yesBids.clear();
        noBids.clear();
        
        // Parse snapshot data
        Map<String, Object> data = (Map<String, Object>) snapshotData.get("data");
        if (data != null) {
            parseSide(data.get("yes"), yesBids);
            parseSide(data.get("no"), noBids);
        }
    }
    
    /**
     * Apply a delta update to modify the order book state
     * @return true if the state changed, false if it was identical
     */
    public boolean applyDelta(Map<String, Object> deltaData, Long sequence) {
        // Check sequence to ensure we're not processing out of order
        if (lastSequence != null && sequence != null && sequence <= lastSequence) {
            return false; // Skip old updates
        }
        
        Map<String, Object> data = (Map<String, Object>) deltaData.get("data");
        if (data == null) {
            return false;
        }
        
        String side = (String) data.get("side");
        Integer price = ((Number) data.get("price")).intValue();
        Integer delta = ((Number) data.get("delta")).intValue();
        
        TreeMap<Integer, Integer> bookSide = "yes".equals(side) ? yesBids : noBids;
        
        // Get current quantity at this price level
        Integer currentQty = bookSide.getOrDefault(price, 0);
        Integer newQty = currentQty + delta;
        
        boolean changed = false;
        
        if (newQty <= 0) {
            // Remove price level if quantity is 0 or negative
            if (bookSide.containsKey(price)) {
                bookSide.remove(price);
                changed = true;
            }
        } else if (!newQty.equals(currentQty)) {
            // Update price level
            bookSide.put(price, newQty);
            changed = true;
        }
        
        if (changed) {
            this.lastSequence = sequence;
            this.lastUpdateTimestamp = System.currentTimeMillis();
        }
        
        return changed;
    }
    
    /**
     * Check if incoming snapshot matches current state
     */
    public boolean isSnapshotIdentical(Map<String, Object> snapshotData) {
        Map<String, Object> data = (Map<String, Object>) snapshotData.get("data");
        if (data == null) {
            return yesBids.isEmpty() && noBids.isEmpty();
        }
        
        TreeMap<Integer, Integer> incomingYes = new TreeMap<>();
        TreeMap<Integer, Integer> incomingNo = new TreeMap<>();
        
        parseSide(data.get("yes"), incomingYes);
        parseSide(data.get("no"), incomingNo);
        
        return yesBids.equals(incomingYes) && noBids.equals(incomingNo);
    }
    
    /**
     * Create a deep copy of the current state
     */
    public OrderBookState copy() {
        OrderBookState copy = new OrderBookState();
        copy.marketTicker = this.marketTicker;
        copy.lastSequence = this.lastSequence;
        copy.lastUpdateTimestamp = this.lastUpdateTimestamp;
        copy.yesBids = new TreeMap<>(this.yesBids);
        copy.noBids = new TreeMap<>(this.noBids);
        return copy;
    }
    
    private void parseSide(Object sideData, TreeMap<Integer, Integer> targetMap) {
        if (sideData instanceof Iterable) {
            for (Object priceLevel : (Iterable<?>) sideData) {
                if (priceLevel instanceof Iterable) {
                    List<Object> levelList = new ArrayList<>();
                    ((Iterable<?>) priceLevel).forEach(levelList::add);
                    
                    if (levelList.size() >= 2) {
                        Integer price = ((Number) levelList.get(0)).intValue();
                        Integer quantity = ((Number) levelList.get(1)).intValue();
                        if (quantity > 0) {
                            targetMap.put(price, quantity);
                        }
                    }
                }
            }
        }
    }
}