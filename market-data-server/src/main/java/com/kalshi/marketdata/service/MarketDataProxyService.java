package com.kalshi.marketdata.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kalshi.marketdata.config.KafkaErrorAlertService;
import com.kalshi.marketdata.websocket.KalshiSpringWebSocketClient;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@Slf4j
public class MarketDataProxyService {
    
    @Autowired
    private MarketDiscoveryService marketDiscoveryService;
    
    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @Autowired
    private OrderBookManager orderBookManager;
    
    @Autowired
    private KafkaErrorAlertService errorAlertService;
    
    @Value("${mock.kalshi.websocket.url}")
    private String websocketUrl;
    
    @Value("${kafka.topic.market-data}")
    private String kafkaTopic;
    
    @Value("${websocket.connection.retry.maxAttempts:10}")
    private int maxRetryAttempts;
    
    @Value("${websocket.connection.retry.delayMs:5000}")
    private long retryDelayMs;
    
    @Value("${kafka.topic.error-alert}")
    private String errorAlertTopic;
    
    @Autowired
    private KalshiSpringWebSocketClient webSocketClient;
    private volatile boolean shouldReconnect = true;
    private final AtomicInteger connectionAttempts = new AtomicInteger(0);
    private volatile boolean isConnecting = false;
    
    @PostConstruct
    public void initialize() {
        log.info("Initializing Market Data Proxy Service");
        // Start connection attempts asynchronously to not block startup
        connectAndSubscribeAsync();
    }
    
    @PreDestroy
    public void shutdown() {
        log.info("Shutting down Market Data Proxy Service");
        shouldReconnect = false;
        if (webSocketClient != null && webSocketClient.isConnected()) {
            webSocketClient.close();
        }
    }
    
    /**
     * Asynchronously connects to the WebSocket and subscribes to all markets
     */
    @Async
    public CompletableFuture<Void> connectAndSubscribeAsync() {
        if (isConnecting) {
            log.debug("Connection attempt already in progress, skipping");
            return CompletableFuture.completedFuture(null);
        }
        
        isConnecting = true;
        int attempt = connectionAttempts.incrementAndGet();
        
        try {
            log.info("Attempting WebSocket connection #{} to: {}", attempt, websocketUrl);
            
            // Connect to WebSocket
            CompletableFuture<Void> connectFuture = webSocketClient.connect(websocketUrl);
            connectFuture.get(10, TimeUnit.SECONDS);
            
            // Wait for connection
            if (webSocketClient.waitForConnection(10, TimeUnit.SECONDS)) {
                log.info("WebSocket connected successfully on attempt #{}", attempt);
                connectionAttempts.set(0); // Reset counter on successful connection
                
                // First subscribe to all markets for channels that support it
                webSocketClient.subscribeToAllMarkets();
                
                // Then fetch market tickers for orderbook subscriptions
                List<String> marketTickers = marketDiscoveryService.getAllMarketTickers();
                
                if (!marketTickers.isEmpty()) {
                    log.info("Subscribing to orderbook channels for {} markets", marketTickers.size());
                    webSocketClient.subscribeToOrderbookChannels(marketTickers);
                } else {
                    log.warn("No markets found for orderbook subscription");
                }
            } else {
                String errorMsg = String.format("Failed to connect to WebSocket on attempt #%d within timeout. Check Kalshi server availability at: %s", attempt, websocketUrl);
                log.error(errorMsg);
                errorAlertService.sendErrorAlert(errorMsg, "MarketDataProxyService");
                scheduleReconnectAsync();
            }
            
        } catch (Exception e) {
            String errorMsg = String.format("Error connecting to WebSocket on attempt #%d to %s: %s", attempt, websocketUrl, e.getMessage());
            log.error(errorMsg, e);
            errorAlertService.sendErrorAlert(errorMsg, "MarketDataProxyService", e);
            scheduleReconnectAsync();
        } finally {
            isConnecting = false;
        }
        
        return CompletableFuture.completedFuture(null);
    }
    
    /**
     * Schedule asynchronous reconnection with exponential backoff
     */
    private void scheduleReconnectAsync() {
        if (!shouldReconnect) {
            return;
        }
        
        int attempt = connectionAttempts.get();
        if (attempt >= maxRetryAttempts) {
            String alertMsg = String.format("CRITICAL: WebSocket connection failed after %d attempts, giving up. Check Kalshi server availability at: %s. Market data streaming has stopped!", maxRetryAttempts, websocketUrl);
            log.error(alertMsg);
            errorAlertService.sendErrorAlert(alertMsg, "MarketDataProxyService");
            return;
        }
        
        // Exponential backoff: delay = baseDelay * 2^(attempt-1), max 60 seconds
        long delay = Math.min(retryDelayMs * (1L << Math.min(attempt - 1, 6)), 60000);
        
        log.info("Scheduling reconnection attempt in {}ms (attempt #{}/{})", delay, attempt + 1, maxRetryAttempts);
        
        CompletableFuture.delayedExecutor(delay, TimeUnit.MILLISECONDS)
                .execute(() -> connectAndSubscribeAsync());
    }
    
    /**
     * Scheduled task to check connection health and reconnect if needed
     */
    @Scheduled(fixedDelay = 30000) // Check every 30 seconds
    public void checkConnectionHealth() {
        if (shouldReconnect && (webSocketClient == null || !webSocketClient.isConnected()) && !isConnecting) {
            log.info("WebSocket connection lost, attempting to reconnect");
            connectAndSubscribeAsync();
        }
    }
    
    /**
     * Periodically refresh market subscriptions to pick up new markets
     */
    @Scheduled(fixedDelay = 300000) // Every 5 minutes
    public void refreshMarketSubscriptions() {
        if (webSocketClient != null && webSocketClient.isConnected()) {
            log.info("Refreshing market subscriptions");
            
            try {
                // Fetch current market list
                List<String> marketTickers = marketDiscoveryService.getAllMarketTickers();
                
                if (!marketTickers.isEmpty()) {
                    // Subscribe to orderbook channels for any new markets
                    webSocketClient.subscribeToOrderbookChannels(marketTickers);
                }
            } catch (Exception e) {
                log.error("Error refreshing market subscriptions", e);
            }
        }
    }
    
    private void scheduleReconnect() {
        // The scheduled health check will handle reconnection
        log.info("Reconnection scheduled for next health check");
    }
}