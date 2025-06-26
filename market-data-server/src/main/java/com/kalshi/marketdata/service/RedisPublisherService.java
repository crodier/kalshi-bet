package com.kalshi.marketdata.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@Slf4j
public class RedisPublisherService {
    
    @Autowired
    private RedisTemplate<String, String> redisTemplate;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    private static final String REDIS_CHANNEL_PREFIX = "market-data:";
    
    /**
     * Listens to Kafka messages and publishes them to Redis channels
     */
    @KafkaListener(topics = "${kafka.topic.market-data}", groupId = "market-data-redis-publisher")
    public void consumeAndPublish(String message) {
        try {
            // Parse the message to determine routing
            Map<String, Object> messageData = objectMapper.readValue(message, Map.class);
            Map<String, Object> originalMessage = (Map<String, Object>) messageData.get("originalMessage");
            
            if (originalMessage != null) {
                // Extract channel and market ticker for routing
                String channel = (String) originalMessage.get("type");
                Map<String, Object> msgData = (Map<String, Object>) originalMessage.get("msg");
                
                if (msgData != null) {
                    String marketTicker = (String) msgData.get("marketTicker");
                    
                    if (channel != null && marketTicker != null) {
                        // Publish to market-specific Redis channel
                        String redisChannel = REDIS_CHANNEL_PREFIX + marketTicker + ":" + channel;
                        redisTemplate.convertAndSend(redisChannel, message);
                        log.trace("Published to Redis channel: {}", redisChannel);
                        
                        // Also publish to a general channel for all market data
                        redisTemplate.convertAndSend(REDIS_CHANNEL_PREFIX + "all", message);
                    }
                }
            }
            
        } catch (Exception e) {
            log.error("Error processing message for Redis publishing", e);
        }
    }
}