package com.kalshi.marketdata.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
public class HealthController {
    
    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;
    
    @Autowired
    private RedisTemplate<String, String> redisTemplate;
    
    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("service", "market-data-server");
        
        // Check Kafka
        try {
            kafkaTemplate.send("health-check", "test").get();
            health.put("kafka", "UP");
        } catch (Exception e) {
            health.put("kafka", "DOWN");
            health.put("kafkaError", e.getMessage());
        }
        
        // Check Redis
        try {
            redisTemplate.opsForValue().get("health-check");
            health.put("redis", "UP");
        } catch (Exception e) {
            health.put("redis", "DOWN");
            health.put("redisError", e.getMessage());
        }
        
        return health;
    }
    
    @GetMapping("/")
    public Map<String, Object> info() {
        return Map.of(
            "service", "Kalshi Market Data Server",
            "description", "WebSocket proxy for Kalshi market data distribution",
            "endpoints", Map.of(
                "websocket", "/ws/market-data",
                "health", "/health"
            ),
            "subscriptionChannels", Map.of(
                "all", "All market data",
                "{marketTicker}:orderbook_snapshot", "Order book snapshots for specific market",
                "{marketTicker}:orderbook_delta", "Order book deltas for specific market",
                "{marketTicker}:ticker_v2", "Ticker updates for specific market",
                "{marketTicker}:trade", "Trade events for specific market"
            )
        );
    }
}