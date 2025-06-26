package com.kalshi.marketdata.service;

import com.kalshi.marketdata.model.OrderBookState;
import com.fbg.api.kalshi.InternalOrderBook;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Admin service for system statistics and monitoring
 */
@Service
@Slf4j
public class AdminService {
    
    @Autowired
    private OrderBookManager orderBookManager;
    
    @Autowired
    private MarketSearchService marketSearchService;
    
    @Autowired
    private OrderBookConverter orderBookConverter;
    
    @Value("${mock.kalshi.websocket.url}")
    private String websocketUrl;
    
    // Statistics tracking
    private final AtomicLong totalMessagesReceived = new AtomicLong(0);
    private final AtomicLong totalMessagesPublished = new AtomicLong(0);
    private final AtomicLong totalMessagesSkipped = new AtomicLong(0);
    private final AtomicLong maxThroughput = new AtomicLong(0);
    
    // Latency tracking
    private volatile long averageLatency = 0;
    private volatile long maxLatency = 0;
    private volatile long currentMessagesPerSecond = 0;
    
    // Application start time
    private final Instant startTime = Instant.now();
    
    // WebSocket status
    private volatile String webSocketStatus = "UNKNOWN";
    private volatile Instant lastUpdateTime = null;
    
    /**
     * Get comprehensive system statistics
     */
    public Map<String, Object> getSystemStatistics() {
        Map<String, Object> stats = new HashMap<>();
        
        // Basic counts
        stats.put("totalMarkets", marketSearchService.getAllMarkets().size());
        stats.put("activeMarkets", getActiveMarketCount());
        stats.put("bootstrappedMarkets", orderBookManager.getBootstrappedMarketCount());
        
        // Message statistics
        stats.put("totalMessagesReceived", totalMessagesReceived.get());
        stats.put("totalMessagesPublished", totalMessagesPublished.get());
        stats.put("totalMessagesSkipped", totalMessagesSkipped.get());
        
        // Calculate skip rate
        long totalReceived = totalMessagesReceived.get();
        double skipRate = totalReceived > 0 ? 
            (double) totalMessagesSkipped.get() / totalReceived * 100 : 0;
        stats.put("skipRate", Math.round(skipRate * 100.0) / 100.0);
        
        // Performance metrics
        stats.put("messagesPerSecond", currentMessagesPerSecond);
        stats.put("maxThroughput", maxThroughput.get());
        stats.put("averageLatency", averageLatency);
        stats.put("maxLatency", maxLatency);
        
        // System info
        stats.put("webSocketStatus", webSocketStatus);
        stats.put("websocketUrl", websocketUrl);
        stats.put("lastUpdateTime", lastUpdateTime != null ? lastUpdateTime.toEpochMilli() : null);
        stats.put("uptime", formatUptime());
        
        // JVM metrics
        stats.put("memoryUsed", getMemoryUsedMB());
        stats.put("memoryMax", getMemoryMaxMB());
        
        return stats;
    }
    
    /**
     * Get order book data for a specific market (using InternalOrderBook format)
     */
    public Map<String, Object> getOrderBookData(String marketTicker) {
        InternalOrderBook internalBook = orderBookManager.getInternalOrderBook(marketTicker);
        if (internalBook == null) {
            // Fallback to legacy format if InternalOrderBook not available
            return getLegacyOrderBookData(marketTicker);
        }
        
        // Use converter to get admin format with timestamps
        return orderBookConverter.convertToAdminFormat(internalBook);
    }
    
    /**
     * Get all order books with InternalOrderBook format
     */
    public Map<String, Map<String, Object>> getAllOrderBooks() {
        Map<String, Map<String, Object>> result = new HashMap<>();
        Map<String, InternalOrderBook> allBooks = orderBookManager.getAllInternalOrderBooks();
        
        for (Map.Entry<String, InternalOrderBook> entry : allBooks.entrySet()) {
            result.put(entry.getKey(), orderBookConverter.convertToAdminFormat(entry.getValue()));
        }
        
        return result;
    }
    
    /**
     * Legacy order book format (for backward compatibility)
     */
    private Map<String, Object> getLegacyOrderBookData(String marketTicker) {
        OrderBookState state = orderBookManager.getOrderBookState(marketTicker);
        if (state == null) {
            return null;
        }
        
        Map<String, Object> orderBookData = new HashMap<>();
        orderBookData.put("marketTicker", marketTicker);
        orderBookData.put("lastSequence", state.getLastSequence());
        orderBookData.put("lastUpdateTimestamp", state.getLastUpdateTimestamp());
        
        // Add YES side data
        Map<String, Object> yesData = new HashMap<>();
        yesData.put("bids", state.getYesBids());
        orderBookData.put("yes", yesData);
        
        // Add NO side data  
        Map<String, Object> noData = new HashMap<>();
        noData.put("bids", state.getNoBids());
        orderBookData.put("no", noData);
        
        // Add timing information
        orderBookData.put("receivedTimestamp", System.currentTimeMillis());
        orderBookData.put("publishedTimestamp", System.currentTimeMillis());
        
        return orderBookData;
    }
    
    /**
     * Update message statistics
     */
    public void recordMessageReceived() {
        totalMessagesReceived.incrementAndGet();
        lastUpdateTime = Instant.now();
    }
    
    public void recordMessagePublished() {
        totalMessagesPublished.incrementAndGet();
    }
    
    public void recordMessageSkipped() {
        totalMessagesSkipped.incrementAndGet();
    }
    
    public void updateLatencyStats(long latency) {
        averageLatency = (averageLatency + latency) / 2; // Simple moving average
        if (latency > maxLatency) {
            maxLatency = latency;
        }
    }
    
    public void updateThroughputStats(long messagesPerSecond) {
        currentMessagesPerSecond = messagesPerSecond;
        if (messagesPerSecond > maxThroughput.get()) {
            maxThroughput.set(messagesPerSecond);
        }
    }
    
    public void updateWebSocketStatus(String status) {
        this.webSocketStatus = status;
    }
    
    private long getActiveMarketCount() {
        return marketSearchService.getAllMarkets().stream()
                .mapToLong(market -> market.isActive() ? 1 : 0)
                .sum();
    }
    
    private String formatUptime() {
        Duration uptime = Duration.between(startTime, Instant.now());
        long hours = uptime.toHours();
        long minutes = uptime.toMinutesPart();
        long seconds = uptime.toSecondsPart();
        
        if (hours > 0) {
            return String.format("%dh %dm %ds", hours, minutes, seconds);
        } else if (minutes > 0) {
            return String.format("%dm %ds", minutes, seconds);
        } else {
            return String.format("%ds", seconds);
        }
    }
    
    private long getMemoryUsedMB() {
        Runtime runtime = Runtime.getRuntime();
        return (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
    }
    
    private long getMemoryMaxMB() {
        return Runtime.getRuntime().maxMemory() / (1024 * 1024);
    }
}