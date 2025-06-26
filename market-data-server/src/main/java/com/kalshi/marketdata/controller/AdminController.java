package com.kalshi.marketdata.controller;

import com.kalshi.marketdata.model.MarketInfo;
import com.kalshi.marketdata.service.AdminService;
import com.kalshi.marketdata.service.MarketSearchService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST controller for admin interface
 */
@RestController
@RequestMapping("/api/admin")
@CrossOrigin(origins = "*") // Allow CORS for development
@Slf4j
public class AdminController {
    
    @Autowired
    private AdminService adminService;
    
    @Autowired
    private MarketSearchService marketSearchService;
    
    /**
     * Get system statistics
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getSystemStats() {
        try {
            Map<String, Object> stats = adminService.getSystemStatistics();
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            log.error("Failed to get system statistics", e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Get all markets
     */
    @GetMapping("/markets")
    public ResponseEntity<List<MarketInfo>> getAllMarkets() {
        try {
            List<MarketInfo> markets = marketSearchService.getAllMarkets();
            return ResponseEntity.ok(markets);
        } catch (Exception e) {
            log.error("Failed to get all markets", e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Search markets by query
     */
    @GetMapping("/markets/search")
    public ResponseEntity<List<MarketInfo>> searchMarkets(@RequestParam("q") String query) {
        try {
            List<MarketInfo> markets = marketSearchService.searchMarkets(query);
            return ResponseEntity.ok(markets);
        } catch (Exception e) {
            log.error("Failed to search markets with query: {}", query, e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Get market details
     */
    @GetMapping("/markets/{marketTicker}")
    public ResponseEntity<MarketInfo> getMarketDetails(@PathVariable String marketTicker) {
        try {
            List<MarketInfo> allMarkets = marketSearchService.getAllMarkets();
            MarketInfo market = allMarkets.stream()
                    .filter(m -> m.getTicker().equals(marketTicker))
                    .findFirst()
                    .orElse(null);
            
            if (market != null) {
                return ResponseEntity.ok(market);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            log.error("Failed to get market details for: {}", marketTicker, e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Get order book data for a market
     */
    @GetMapping("/orderbook/{marketTicker}")
    public ResponseEntity<Map<String, Object>> getOrderBook(@PathVariable String marketTicker) {
        try {
            Map<String, Object> orderBookData = adminService.getOrderBookData(marketTicker);
            if (orderBookData != null) {
                return ResponseEntity.ok(orderBookData);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            log.error("Failed to get order book for market: {}", marketTicker, e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Refresh market cache
     */
    @PostMapping("/markets/refresh")
    public ResponseEntity<Map<String, String>> refreshMarkets() {
        try {
            marketSearchService.refreshMarketCache();
            return ResponseEntity.ok(Map.of("status", "success", "message", "Market cache refreshed"));
        } catch (Exception e) {
            log.error("Failed to refresh market cache", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("status", "error", "message", "Failed to refresh markets"));
        }
    }
}