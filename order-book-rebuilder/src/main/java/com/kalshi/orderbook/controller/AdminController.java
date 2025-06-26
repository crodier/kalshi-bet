package com.kalshi.orderbook.controller;

import com.fbg.api.kalshi.InternalOrderBook;
import com.kalshi.orderbook.service.OrderBookManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/admin/api/v1")
@RequiredArgsConstructor
public class AdminController {
    
    private final OrderBookManager orderBookManager;
    
    /**
     * Search markets by prefix with limit
     */
    @GetMapping("/markets/search")
    public ResponseEntity<Map<String, Object>> searchMarkets(
            @RequestParam(defaultValue = "") String prefix,
            @RequestParam(defaultValue = "50") int limit) {
        
        List<String> marketTickers = orderBookManager.searchMarkets(prefix, limit);
        
        // Get order book summaries for these markets
        List<Map<String, Object>> markets = marketTickers.stream()
            .map(ticker -> {
                OrderBook orderBook = orderBookManager.getOrderBook(ticker);
                if (orderBook != null) {
                    var bestYes = orderBook.getBestYes();
                    var bestNo = orderBook.getBestNo();
                    
                    return Map.<String, Object>of(
                        "marketTicker", ticker,
                        "status", orderBook.getMarketStatus(),
                        "lastUpdateTimestamp", orderBook.getLastUpdateTimestamp(),
                        "bestYes", bestYes != null ? Map.of(
                            "price", bestYes.getPrice(),
                            "size", bestYes.getSize()
                        ) : null,
                        "bestNo", bestNo != null ? Map.of(
                            "price", bestNo.getPrice(),
                            "size", bestNo.getSize()
                        ) : null,
                        "yesLevels", orderBook.getYesSideSnapshot().size(),
                        "noLevels", orderBook.getNoSideSnapshot().size()
                    );
                }
                return Map.<String, Object>of(
                    "marketTicker", ticker,
                    "status", "unknown"
                );
            })
            .collect(Collectors.toList());
        
        return ResponseEntity.ok(Map.of(
            "query", prefix,
            "totalResults", marketTickers.size(),
            "markets", markets
        ));
    }
    
    /**
     * Get autocomplete suggestions
     */
    @GetMapping("/markets/suggestions")
    public ResponseEntity<List<String>> getMarketSuggestions(
            @RequestParam String query,
            @RequestParam(defaultValue = "10") int limit) {
        
        List<String> suggestions = orderBookManager.getMarketSuggestions(query, limit);
        return ResponseEntity.ok(suggestions);
    }
    
    /**
     * Get all markets with pagination
     */
    @GetMapping("/markets")
    public ResponseEntity<Map<String, Object>> getAllMarkets(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size) {
        
        List<String> allMarkets = orderBookManager.getAllMarketTickers();
        
        // Simple pagination
        int start = page * size;
        int end = Math.min(start + size, allMarkets.size());
        
        if (start >= allMarkets.size()) {
            return ResponseEntity.ok(Map.of(
                "markets", List.of(),
                "totalMarkets", allMarkets.size(),
                "page", page,
                "size", size,
                "hasMore", false
            ));
        }
        
        List<String> pageMarkets = allMarkets.subList(start, end);
        
        // Get summaries for this page
        List<Map<String, Object>> marketSummaries = pageMarkets.stream()
            .map(ticker -> {
                OrderBook orderBook = orderBookManager.getOrderBook(ticker);
                if (orderBook != null) {
                    var bestYes = orderBook.getBestYes();
                    var bestNo = orderBook.getBestNo();
                    
                    return Map.<String, Object>of(
                        "marketTicker", ticker,
                        "status", orderBook.getMarketStatus(),
                        "lastUpdateTimestamp", orderBook.getLastUpdateTimestamp(),
                        "bestYes", bestYes != null ? Map.of(
                            "price", bestYes.getPrice(),
                            "size", bestYes.getSize()
                        ) : null,
                        "bestNo", bestNo != null ? Map.of(
                            "price", bestNo.getPrice(),
                            "size", bestNo.getSize()
                        ) : null,
                        "yesLevels", orderBook.getYesSideSnapshot().size(),
                        "noLevels", orderBook.getNoSideSnapshot().size()
                    );
                }
                return Map.<String, Object>of(
                    "marketTicker", ticker,
                    "status", "unknown"
                );
            })
            .collect(Collectors.toList());
        
        return ResponseEntity.ok(Map.of(
            "markets", marketSummaries,
            "totalMarkets", allMarkets.size(),
            "page", page,
            "size", size,
            "hasMore", end < allMarkets.size()
        ));
    }
    
    /**
     * Get detailed market information
     */
    @GetMapping("/markets/{ticker}")
    public ResponseEntity<Map<String, Object>> getMarketDetails(@PathVariable String ticker) {
        OrderBook orderBook = orderBookManager.getOrderBook(ticker);
        
        if (orderBook == null) {
            return ResponseEntity.notFound().build();
        }
        
        return ResponseEntity.ok(Map.of(
            "marketTicker", ticker,
            "status", orderBook.getMarketStatus(),
            "lastUpdateTimestamp", orderBook.getLastUpdateTimestamp(),
            "lastKafkaPublishTimestamp", orderBook.getLastKafkaPublishTimestamp(),
            "kafkaLatencyMs", orderBook.getKafkaLatencyMs(),
            "orderServerPublishTimestamp", orderBook.getOrderServerPublishTimestamp(),
            "marketClosedTimestamp", orderBook.getMarketClosedTimestamp(),
            "bestYes", orderBook.getBestYes(),
            "bestNo", orderBook.getBestNo(),
            "yesLevels", orderBook.getYesSideSnapshot(),
            "noLevels", orderBook.getNoSideSnapshot(),
            "totalYesLevels", orderBook.getYesSideSnapshot().size(),
            "totalNoLevels", orderBook.getNoSideSnapshot().size()
        ));
    }
    
    /**
     * Get system statistics
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getSystemStats() {
        return ResponseEntity.ok(Map.of(
            "totalMarkets", orderBookManager.getOrderBookCount(),
            "activeMarkets", orderBookManager.getAllOrderBooks().stream()
                .filter(ob -> "active".equals(ob.getMarketStatus()))
                .count(),
            "closedMarkets", orderBookManager.getAllOrderBooks().stream()
                .filter(ob -> "closed".equals(ob.getMarketStatus()))
                .count(),
            "timestamp", System.currentTimeMillis()
        ));
    }
}