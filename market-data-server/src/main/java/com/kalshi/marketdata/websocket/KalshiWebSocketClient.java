package com.kalshi.marketdata.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kalshi.marketdata.service.OrderBookManager;
import lombok.extern.slf4j.Slf4j;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Component
public class KalshiWebSocketClient extends WebSocketClient {
    
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final OrderBookManager orderBookManager;
    private final String kafkaTopic;
    private final CountDownLatch connectionLatch = new CountDownLatch(1);
    private final AtomicInteger messageId = new AtomicInteger(1);
    
    // Statistics
    private final AtomicLong totalMessagesReceived = new AtomicLong(0);
    private final AtomicLong messagesPublished = new AtomicLong(0);
    private final AtomicLong messagesSkipped = new AtomicLong(0);
    
    public KalshiWebSocketClient(URI serverUri, 
                                KafkaTemplate<String, String> kafkaTemplate,
                                ObjectMapper objectMapper,
                                OrderBookManager orderBookManager,
                                String kafkaTopic) {
        super(serverUri);
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.orderBookManager = orderBookManager;
        this.kafkaTopic = kafkaTopic;
    }
    
    @Override
    public void onOpen(ServerHandshake handshake) {
        log.info("WebSocket connection opened to Kalshi mock server");
        connectionLatch.countDown();
    }
    
    @Override
    public void onMessage(String message) {
        log.debug("Received message: {}", message);
        long receivedTimestamp = System.currentTimeMillis();
        totalMessagesReceived.incrementAndGet();
        
        try {
            // Parse the message to extract metadata
            Map<String, Object> messageData = objectMapper.readValue(message, HashMap.class);
            
            // Extract channel, market ticker and sequence if available
            String channel = (String) messageData.get("channel");
            String marketTicker = (String) messageData.get("market_ticker");
            Object seqObj = messageData.get("seq");
            Long sequence = seqObj != null ? ((Number) seqObj).longValue() : null;
            
            // Check with OrderBookManager if we should publish this message
            boolean shouldPublish = orderBookManager.shouldPublishMessage(messageData);
            
            if (!shouldPublish) {
                messagesSkipped.incrementAndGet();
                log.debug("Skipping duplicate/unchanged message for market: {} channel: {} seq: {}", 
                         marketTicker, channel, sequence);
                
                // Log statistics periodically
                if (totalMessagesReceived.get() % 1000 == 0) {
                    logStatistics();
                }
                return;
            }
            
            // Create the envelope with timing metadata
            Map<String, Object> envelope = new HashMap<>();
            envelope.put("payload", messageData);
            envelope.put("receivedTimestamp", receivedTimestamp);
            envelope.put("publishedTimestamp", System.currentTimeMillis());
            envelope.put("channel", channel);
            envelope.put("marketTicker", marketTicker);
            envelope.put("sequence", sequence);
            envelope.put("source", "kalshi-websocket");
            envelope.put("version", 1);
            
            String envelopeJson = objectMapper.writeValueAsString(envelope);
            
            // Send to Kafka with market ticker as key for partitioning
            String kafkaKey = marketTicker != null ? marketTicker : "all-markets";
            kafkaTemplate.send(kafkaTopic, kafkaKey, envelopeJson)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to send message to Kafka", ex);
                    } else {
                        messagesPublished.incrementAndGet();
                        long latency = System.currentTimeMillis() - receivedTimestamp;
                        log.trace("Message sent to Kafka topic: {}, key: {}, latency: {}ms", 
                                 kafkaTopic, kafkaKey, latency);
                    }
                });
                
        } catch (Exception e) {
            log.error("Error processing WebSocket message", e);
        }
    }
    
    @Override
    public void onClose(int code, String reason, boolean remote) {
        log.warn("WebSocket connection closed. Code: {}, Reason: {}, Remote: {}", code, reason, remote);
    }
    
    @Override
    public void onError(Exception ex) {
        log.error("WebSocket error", ex);
    }
    
    public boolean waitForConnection(long timeout, TimeUnit unit) throws InterruptedException {
        return connectionLatch.await(timeout, unit);
    }
    
    /**
     * Subscribe to market data channels for specified tickers
     */
    public void subscribeToMarkets(List<String> marketTickers, List<String> channels) {
        try {
            Map<String, Object> subscribeMessage = new HashMap<>();
            subscribeMessage.put("id", messageId.getAndIncrement());
            subscribeMessage.put("cmd", "subscribe");
            
            Map<String, Object> params = new HashMap<>();
            params.put("channels", channels);
            params.put("market_tickers", marketTickers);
            subscribeMessage.put("params", params);
            
            String json = objectMapper.writeValueAsString(subscribeMessage);
            log.info("Sending subscription for {} markets on channels: {}", marketTickers.size(), channels);
            send(json);
            
        } catch (Exception e) {
            log.error("Error sending subscription message", e);
        }
    }
    
    /**
     * Subscribe to all markets for supported channels
     * Based on Kalshi docs, some channels support all markets mode without specifying tickers
     */
    public void subscribeToAllMarkets() {
        try {
            // First subscribe to channels that support all markets mode
            Map<String, Object> allMarketsSubscribe = new HashMap<>();
            allMarketsSubscribe.put("id", messageId.getAndIncrement());
            allMarketsSubscribe.put("cmd", "subscribe");
            
            Map<String, Object> allMarketsParams = new HashMap<>();
            // These channels support all markets subscription without specifying tickers
            allMarketsParams.put("channels", List.of("ticker_v2", "trade", "market_lifecycle_v2"));
            allMarketsSubscribe.put("params", allMarketsParams);
            
            String allMarketsJson = objectMapper.writeValueAsString(allMarketsSubscribe);
            log.info("Subscribing to all markets for channels: ticker_v2, trade, market_lifecycle_v2");
            send(allMarketsJson);
            
            // For orderbook channels, we need to specify market tickers
            // This will be handled separately when we know which markets to subscribe to
            
        } catch (Exception e) {
            log.error("Error sending all markets subscription", e);
        }
    }
    
    /**
     * Subscribe to orderbook channels for specific markets
     * Orderbook channels require market tickers to be specified
     */
    public void subscribeToOrderbookChannels(List<String> marketTickers) {
        if (marketTickers == null || marketTickers.isEmpty()) {
            log.warn("No market tickers provided for orderbook subscription");
            return;
        }
        
        List<String> orderbookChannels = List.of("orderbook_snapshot", "orderbook_delta");
        
        // WebSocket might have limits on subscription size, so batch if needed
        int batchSize = 50; // Subscribe to 50 markets at a time
        
        for (int i = 0; i < marketTickers.size(); i += batchSize) {
            int end = Math.min(i + batchSize, marketTickers.size());
            List<String> batch = marketTickers.subList(i, end);
            
            subscribeToMarkets(batch, orderbookChannels);
            
            // Small delay between batches to avoid overwhelming the server
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Interrupted while batching subscriptions", e);
                break;
            }
        }
    }
    
    /**
     * Log statistics about message processing
     */
    private void logStatistics() {
        long total = totalMessagesReceived.get();
        long published = messagesPublished.get();
        long skipped = messagesSkipped.get();
        double skipRate = total > 0 ? (double) skipped / total * 100 : 0;
        
        log.info("WebSocket statistics - Total: {}, Published: {}, Skipped: {} ({}% skip rate), " +
                "Tracked markets: {}, Bootstrapped markets: {}", 
                total, published, skipped, String.format("%.2f", skipRate),
                orderBookManager.getTrackedMarketCount(), 
                orderBookManager.getBootstrappedMarketCount());
    }
    
    /**
     * Get current statistics
     */
    public Map<String, Long> getStatistics() {
        Map<String, Long> stats = new HashMap<>();
        stats.put("totalMessagesReceived", totalMessagesReceived.get());
        stats.put("messagesPublished", messagesPublished.get());
        stats.put("messagesSkipped", messagesSkipped.get());
        stats.put("trackedMarkets", (long) orderBookManager.getTrackedMarketCount());
        stats.put("bootstrappedMarkets", (long) orderBookManager.getBootstrappedMarketCount());
        return stats;
    }
}