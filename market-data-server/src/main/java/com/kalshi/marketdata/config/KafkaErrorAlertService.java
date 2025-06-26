package com.kalshi.marketdata.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;

/**
 * Service to send error alerts to Kafka topic for monitoring
 */
@Service
@Slf4j
public class KafkaErrorAlertService {
    
    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @Value("${kafka.topic.error-alert}")
    private String errorAlertTopic;
    
    @Value("${spring.profiles.active:local}")
    private String environment;
    
    /**
     * Send error alert to Kafka asynchronously
     */
    @Async
    public void sendErrorAlert(String message, String logger, Throwable throwable) {
        try {
            Map<String, Object> alert = Map.of(
                "timestamp", Instant.now().toEpochMilli(),
                "level", "ERROR",
                "service", "market-data-server",
                "environment", environment,
                "logger", logger,
                "message", message,
                "stackTrace", throwable != null ? throwable.toString() : "",
                "host", System.getProperty("hostname", "localhost")
            );
            
            String alertJson = objectMapper.writeValueAsString(alert);
            kafkaTemplate.send(errorAlertTopic, "error-alert", alertJson);
            
        } catch (Exception e) {
            // Don't let error alerting itself cause issues
            log.warn("Failed to send error alert to Kafka: {}", e.getMessage());
        }
    }
    
    /**
     * Send error alert without throwable
     */
    @Async
    public void sendErrorAlert(String message, String logger) {
        sendErrorAlert(message, logger, null);
    }
}