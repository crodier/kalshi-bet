package com.kalshi.orderbook.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fbg.api.kalshi.MarketDataEnvelope;
import com.fbg.api.kalshi.InternalOrderBook;
import com.fbg.api.kalshi.InternalOrderBookBuilder;
import com.fbg.api.kalshi.UpdateType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class MarketDataProcessor {
    
    private final OrderBookManager orderBookManager;
    private final OrderBookUpdatePublisher updatePublisher;
    
    public void processMarketData(MarketDataEnvelope envelope) {
        try {
            String channel = envelope.getChannel();
            String marketTicker = envelope.getMarketTicker();
            
            if (marketTicker == null || channel == null) {
                log.warn("Missing market ticker or channel in envelope");
                return;
            }
            
            // Process the market data and create a new InternalOrderBook
            InternalOrderBook newOrderBook = null;
            JsonNode payload = envelope.getPayload();
            
            switch (channel) {
                case "orderbook_snapshot":
                    newOrderBook = processSnapshot(marketTicker, payload, envelope);
                    break;
                case "orderbook_delta":
                    newOrderBook = processDelta(marketTicker, payload, envelope);
                    break;
                case "market_status":
                    processMarketStatus(marketTicker, payload);
                    return; // Don't publish order book updates for market status
                default:
                    log.debug("Ignoring channel: {} for market: {}", channel, marketTicker);
                    return;
            }
            
            if (newOrderBook != null) {
                orderBookManager.updateOrderBook(marketTicker, newOrderBook);
                updatePublisher.publishOrderBookUpdate(newOrderBook);
            }
            
        } catch (Exception e) {
            log.error("Error processing market data envelope", e);
        }
    }
    
    private InternalOrderBook processSnapshot(String marketTicker, JsonNode payload, MarketDataEnvelope envelope) {
        log.debug("Processing snapshot for market: {}", marketTicker);
        
        JsonNode msg = payload.get("msg");
        if (msg == null) return null;
        
        InternalOrderBookBuilder builder = new InternalOrderBookBuilder(marketTicker, envelope.getReceivedTimestamp());
        
        if (envelope.getSequence() != null) {
            builder.setSequenceNumber(envelope.getSequence());
        }
        
        // Process yes side (equivalent to bids)
        JsonNode yesSide = msg.get("yes");
        if (yesSide != null && yesSide.isArray()) {
            for (JsonNode level : yesSide) {
                int price = level.get(0).asInt(); // Price in cents
                long size = level.get(1).asLong();
                // Use the received timestamp as the original data timestamp
                builder.addYesLevel(price, size, envelope.getReceivedTimestamp(), UpdateType.SNAPSHOT);
            }
        }
        
        // Process no side (equivalent to asks)
        JsonNode noSide = msg.get("no");
        if (noSide != null && noSide.isArray()) {
            for (JsonNode level : noSide) {
                int price = level.get(0).asInt(); // Price in cents
                long size = level.get(1).asLong();
                // Use the received timestamp as the original data timestamp
                builder.addNoLevel(price, size, envelope.getReceivedTimestamp(), UpdateType.SNAPSHOT);
            }
        }
        
        return builder.build();
    }
    
    private InternalOrderBook processDelta(String marketTicker, JsonNode payload, MarketDataEnvelope envelope) {
        log.debug("Processing delta for market: {}", marketTicker);
        
        JsonNode msg = payload.get("msg");
        if (msg == null) return null;
        
        // Get the current order book to apply the delta to
        InternalOrderBook currentOrderBook = orderBookManager.getOrCreateOrderBook(marketTicker);
        
        // Kalshi delta format: { "price": 55, "delta": 100, "side": "yes" }
        int price = msg.get("price").asInt();
        long delta = msg.get("delta").asLong();
        String side = msg.get("side").asText();
        
        // Create a new builder based on the current order book
        InternalOrderBookBuilder builder = new InternalOrderBookBuilder(marketTicker, envelope.getReceivedTimestamp());
        
        if (envelope.getSequence() != null) {
            builder.setSequenceNumber(envelope.getSequence());
        }
        
        // Copy existing levels from current order book, preserving original timestamps
        currentOrderBook.getYesSide().getLevels().forEach((existingPrice, existingLevel) -> {
            builder.addYesLevel(existingPrice, existingLevel.getQuantity(), 
                existingLevel.getLastUpdateTimestamp(), UpdateType.SNAPSHOT);
        });
        
        currentOrderBook.getNoSide().getLevels().forEach((existingPrice, existingLevel) -> {
            builder.addNoLevel(existingPrice, existingLevel.getQuantity(), 
                existingLevel.getLastUpdateTimestamp(), UpdateType.SNAPSHOT);
        });
        
        // Apply the delta with the new received timestamp for the changed level
        if ("yes".equals(side)) {
            if (delta == 0) {
                // Remove level - this will be handled by builder if quantity is 0
                builder.addYesLevel(price, 0, envelope.getReceivedTimestamp(), UpdateType.DELTA_REMOVE);
            } else {
                // Get current level and apply delta
                long currentQuantity = currentOrderBook.getYesSide().getLevels().containsKey(price) ? 
                    currentOrderBook.getYesSide().getLevels().get(price).getQuantity() : 0;
                long newSize = Math.max(0, currentQuantity + delta);
                
                UpdateType updateType = currentQuantity == 0 ? UpdateType.DELTA_ADD : 
                    (newSize == 0 ? UpdateType.DELTA_REMOVE : UpdateType.DELTA_MODIFY);
                builder.addYesLevel(price, newSize, envelope.getReceivedTimestamp(), updateType);
            }
        } else if ("no".equals(side)) {
            if (delta == 0) {
                // Remove level
                builder.addNoLevel(price, 0, envelope.getReceivedTimestamp(), UpdateType.DELTA_REMOVE);
            } else {
                // Get current level and apply delta
                long currentQuantity = currentOrderBook.getNoSide().getLevels().containsKey(price) ? 
                    currentOrderBook.getNoSide().getLevels().get(price).getQuantity() : 0;
                long newSize = Math.max(0, currentQuantity + delta);
                
                UpdateType updateType = currentQuantity == 0 ? UpdateType.DELTA_ADD : 
                    (newSize == 0 ? UpdateType.DELTA_REMOVE : UpdateType.DELTA_MODIFY);
                builder.addNoLevel(price, newSize, envelope.getReceivedTimestamp(), updateType);
            }
        }
        
        return builder.build();
    }
    
    private void processMarketStatus(String marketTicker, JsonNode payload) {
        String status = payload.get("status").asText();
        log.info("Market {} status changed to: {}", marketTicker, status);
        
        if ("closed".equals(status)) {
            log.info("Market {} closed", marketTicker);
            // For now, we'll just log market closure
            // In the future, we might want to store market status in a separate service
        }
    }
}