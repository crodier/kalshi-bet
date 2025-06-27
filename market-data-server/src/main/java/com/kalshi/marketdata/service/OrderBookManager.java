package com.kalshi.marketdata.service;

import com.kalshi.marketdata.model.OrderBookState;
import com.fbg.api.kalshi.InternalOrderBook;
import com.kalshi.marketdata.event.OrderBookUpdateEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages order book states for all markets and determines what updates to publish
 */
@Slf4j
@Service
public class OrderBookManager {
    
    // Market ticker -> OrderBookState (legacy for backward compatibility)
    private final ConcurrentHashMap<String, OrderBookState> orderBooks = new ConcurrentHashMap<>();
    
    // Market ticker -> InternalOrderBook (new internal format)
    private final ConcurrentHashMap<String, InternalOrderBook> internalOrderBooks = new ConcurrentHashMap<>();
    
    // Track markets that have been bootstrapped
    private final ConcurrentHashMap<String, Boolean> bootstrappedMarkets = new ConcurrentHashMap<>();
    
    @Autowired
    private OrderBookConverter orderBookConverter;
    
    @Autowired(required = false)
    private ApplicationEventPublisher eventPublisher;
    
    /**
     * Process an incoming WebSocket message and determine if it should be published
     * Also converts to InternalOrderBook format for internal use
     * @return true if the message should be published, false if it should be skipped
     */
    public boolean shouldPublishMessage(Map<String, Object> message) {
        String channel = (String) message.get("channel");
        String marketTicker = (String) message.get("market_ticker");
        
        if (marketTicker == null || channel == null) {
            // Non-market specific messages or missing data, publish them
            return true;
        }
        
        // Always publish non-orderbook messages (trades, ticker updates, etc)
        if (!"orderbook_snapshot".equals(channel) && !"orderbook_delta".equals(channel)) {
            return true;
        }
        
        Object seqObj = message.get("seq");
        Long sequence = seqObj != null ? ((Number) seqObj).longValue() : null;
        long receivedTimestamp = System.currentTimeMillis();
        
        // Handle legacy OrderBookState for backward compatibility
        OrderBookState currentState = orderBooks.computeIfAbsent(marketTicker, 
            k -> {
                OrderBookState state = new OrderBookState();
                state.setMarketTicker(marketTicker);
                return state;
            });
        
        synchronized (currentState) {
            boolean shouldPublish;
            
            if ("orderbook_snapshot".equals(channel)) {
                shouldPublish = handleSnapshot(currentState, message, sequence, marketTicker);
                // Convert to InternalOrderBook format
                handleInternalSnapshot(marketTicker, message, receivedTimestamp, sequence);
            } else if ("orderbook_delta".equals(channel)) {
                shouldPublish = handleDelta(currentState, message, sequence, marketTicker);
                // Convert to InternalOrderBook format
                handleInternalDelta(marketTicker, message, receivedTimestamp, sequence);
            } else {
                shouldPublish = true;
            }
            
            return shouldPublish;
        }
    }
    
    /**
     * Handle snapshot in InternalOrderBook format
     */
    private void handleInternalSnapshot(String marketTicker, Map<String, Object> message, 
                                      long receivedTimestamp, Long sequence) {
        try {
            InternalOrderBook newBook = orderBookConverter.convertSnapshot(marketTicker, message, receivedTimestamp, sequence);
            internalOrderBooks.put(marketTicker, newBook);
            log.debug("Updated internal order book for {} with snapshot (seq: {})", marketTicker, sequence);
        } catch (Exception e) {
            log.error("Failed to convert snapshot to InternalOrderBook for {}: {}", marketTicker, e.getMessage(), e);
        }
    }
    
    /**
     * Handle delta in InternalOrderBook format
     */
    private void handleInternalDelta(String marketTicker, Map<String, Object> message, 
                                   long receivedTimestamp, Long sequence) {
        try {
            InternalOrderBook existingBook = internalOrderBooks.get(marketTicker);
            if (existingBook != null) {
                InternalOrderBook updatedBook = orderBookConverter.applyDelta(existingBook, message, receivedTimestamp, sequence);
                internalOrderBooks.put(marketTicker, updatedBook);
                log.debug("Updated internal order book for {} with delta (seq: {})", marketTicker, sequence);
            } else {
                log.warn("Received delta for {} but no existing InternalOrderBook found", marketTicker);
            }
        } catch (Exception e) {
            log.error("Failed to apply delta to InternalOrderBook for {}: {}", marketTicker, e.getMessage(), e);
        }
    }
    
    private boolean handleSnapshot(OrderBookState currentState, Map<String, Object> message, 
                                 Long sequence, String marketTicker) {
        // If we haven't bootstrapped this market yet, always publish the first snapshot
        if (!isMarketBootstrapped(marketTicker)) {
            currentState.applySnapshot(message, sequence);
            markMarketAsBootstrapped(marketTicker);
            log.info("Publishing initial snapshot for non-bootstrapped market: {}", marketTicker);
            return true;
        }
        
        // Check if this snapshot is different from our current state
        if (!currentState.isSnapshotIdentical(message)) {
            log.info("Snapshot differs from current state for market: {}, publishing update", marketTicker);
            currentState.applySnapshot(message, sequence);
            return true;
        } else {
            log.debug("Skipping identical snapshot for market: {} seq: {}", marketTicker, sequence);
            // Update sequence number even if we don't publish
            if (sequence != null) {
                currentState.setLastSequence(sequence);
            }
            return false;
        }
    }
    
    private boolean handleDelta(OrderBookState currentState, Map<String, Object> message, 
                              Long sequence, String marketTicker) {
        // Always apply and publish deltas as they represent actual changes
        boolean changed = currentState.applyDelta(message, sequence);
        
        if (!changed) {
            log.debug("Delta resulted in no change for market: {} seq: {}", marketTicker, sequence);
            return false;
        }
        
        return true;
    }
    
    /**
     * Load historical state from bootstrap data
     */
    public void loadHistoricalState(String marketTicker, OrderBookState state) {
        orderBooks.put(marketTicker, state.copy());
        markMarketAsBootstrapped(marketTicker);
        log.info("Loaded historical state for market: {} with sequence: {}", 
                marketTicker, state.getLastSequence());
    }
    
    /**
     * Mark a market as bootstrapped from historical data
     */
    public void markMarketAsBootstrapped(String marketTicker) {
        bootstrappedMarkets.put(marketTicker, true);
    }
    
    /**
     * Check if a market has been bootstrapped
     */
    public boolean isMarketBootstrapped(String marketTicker) {
        return bootstrappedMarkets.getOrDefault(marketTicker, false);
    }
    
    /**
     * Get current state for a market (for testing/monitoring)
     */
    public OrderBookState getOrderBookState(String marketTicker) {
        OrderBookState state = orderBooks.get(marketTicker);
        return state != null ? state.copy() : null;
    }
    
    /**
     * Get count of tracked markets
     */
    public int getTrackedMarketCount() {
        return orderBooks.size();
    }
    
    /**
     * Get count of bootstrapped markets
     */
    public int getBootstrappedMarketCount() {
        return bootstrappedMarkets.size();
    }
    
    /**
     * Get InternalOrderBook for a market (new format)
     */
    public InternalOrderBook getInternalOrderBook(String marketTicker) {
        return internalOrderBooks.get(marketTicker);
    }
    
    /**
     * Get all internal order books
     */
    public Map<String, InternalOrderBook> getAllInternalOrderBooks() {
        return new ConcurrentHashMap<>(internalOrderBooks);
    }
    
    /**
     * Notify admin WebSocket handler about order book updates
     */
    private void notifyOrderBookUpdate(String marketTicker, InternalOrderBook orderBook) {
        if (eventPublisher != null) {
            try {
                Map<String, Object> adminFormat = orderBookConverter.convertToAdminFormat(orderBook);
                OrderBookUpdateEvent event = new OrderBookUpdateEvent(this, marketTicker, adminFormat);
                eventPublisher.publishEvent(event);
            } catch (Exception e) {
                log.error("Failed to publish order book update event for {}: {}", marketTicker, e.getMessage(), e);
            }
        }
    }
    
    /**
     * Clear all state (useful for testing)
     */
    public void clearAll() {
        orderBooks.clear();
        internalOrderBooks.clear();
        bootstrappedMarkets.clear();
    }
}