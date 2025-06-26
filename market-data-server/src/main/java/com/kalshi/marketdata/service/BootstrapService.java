package com.kalshi.marketdata.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kalshi.marketdata.model.OrderBookState;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import org.springframework.scheduling.annotation.Async;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Service to bootstrap order book state from previously published Kafka messages
 */
@Slf4j
@Service
public class BootstrapService {
    
    @Autowired
    private OrderBookManager orderBookManager;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @Value("${kafka.topic.market-data}")
    private String kafkaTopic;
    
    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;
    
    @Value("${bootstrap.lookback.minutes:120}")
    private int lookbackMinutes;
    
    @Value("${bootstrap.enabled:true}")
    private boolean bootstrapEnabled;
    
    /**
     * Bootstrap order book states from Kafka on application startup
     */
    @PostConstruct
    public void initializeBootstrap() {
        if (!bootstrapEnabled) {
            log.info("Bootstrap is disabled, skipping historical data load");
            return;
        }
        
        // Run bootstrap asynchronously to not block application startup
        bootstrapAsync().exceptionally(throwable -> {
            log.error("Async bootstrap failed", throwable);
            return null;
        });
    }
    
    /**
     * Asynchronous bootstrap method
     */
    @Async
    public CompletableFuture<Void> bootstrapAsync() {
        log.info("Starting async bootstrap process, looking back {} minutes", lookbackMinutes);
        long startTime = System.currentTimeMillis();
        
        try {
            Map<String, OrderBookState> bootstrappedStates = loadHistoricalData();
            
            // Load states into OrderBookManager
            for (Map.Entry<String, OrderBookState> entry : bootstrappedStates.entrySet()) {
                orderBookManager.loadHistoricalState(entry.getKey(), entry.getValue());
            }
            
            long duration = System.currentTimeMillis() - startTime;
            log.info("Bootstrap completed in {}ms, loaded {} market states", 
                    duration, bootstrappedStates.size());
            
        } catch (Exception e) {
            log.error("Bootstrap failed - application will continue with empty state", e);
            // Don't fail startup, just continue without historical data
        }
        
        return CompletableFuture.completedFuture(null);
    }
    
    /**
     * Load historical market data from Kafka
     */
    public Map<String, OrderBookState> loadHistoricalData() {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "market-data-bootstrap-" + UUID.randomUUID());
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, 
                "org.apache.kafka.common.serialization.StringDeserializer");
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, 
                "org.apache.kafka.common.serialization.StringDeserializer");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, "5000");
        
        Map<String, OrderBookState> marketStates = new HashMap<>();
        
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            // Get all partitions for the topic
            List<TopicPartition> partitions = new ArrayList<>();
            consumer.partitionsFor(kafkaTopic).forEach(partitionInfo -> 
                partitions.add(new TopicPartition(kafkaTopic, partitionInfo.partition())));
            
            consumer.assign(partitions);
            
            // Calculate target timestamp (current time - lookback period)
            long targetTimestamp = Instant.now()
                    .minus(Duration.ofMinutes(lookbackMinutes))
                    .toEpochMilli();
            
            // Seek to the target timestamp for each partition
            Map<TopicPartition, Long> timestampsToSearch = new HashMap<>();
            partitions.forEach(tp -> timestampsToSearch.put(tp, targetTimestamp));
            
            Map<TopicPartition, Long> offsetsForTimes = consumer.offsetsForTimes(timestampsToSearch)
                    .entrySet().stream()
                    .filter(e -> e.getValue() != null)
                    .collect(HashMap::new, 
                            (m, e) -> m.put(e.getKey(), e.getValue().offset()), 
                            HashMap::putAll);
            
            // Seek to the calculated offsets
            offsetsForTimes.forEach(consumer::seek);
            
            // Read messages and build state
            int totalMessages = 0;
            int processedMessages = 0;
            boolean done = false;
            
            while (!done) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(5));
                
                if (records.isEmpty()) {
                    done = true;
                    continue;
                }
                
                for (ConsumerRecord<String, String> record : records) {
                    totalMessages++;
                    
                    try {
                        Map<String, Object> envelope = objectMapper.readValue(record.value(), Map.class);
                        Map<String, Object> payload = (Map<String, Object>) envelope.get("payload");
                        
                        if (payload == null) {
                            continue;
                        }
                        
                        String channel = (String) payload.get("channel");
                        String marketTicker = (String) payload.get("market_ticker");
                        
                        if (marketTicker == null || channel == null) {
                            continue;
                        }
                        
                        // Only process orderbook messages
                        if ("orderbook_snapshot".equals(channel) || "orderbook_delta".equals(channel)) {
                            processOrderBookMessage(marketStates, marketTicker, payload);
                            processedMessages++;
                        }
                        
                    } catch (Exception e) {
                        log.warn("Failed to process bootstrap message", e);
                    }
                }
                
                // Check if we've reached current time
                if (!records.isEmpty()) {
                    ConsumerRecord<String, String> lastRecord = 
                            records.records(partitions.get(0)).get(records.count() - 1);
                    if (lastRecord.timestamp() >= System.currentTimeMillis() - 1000) {
                        done = true;
                    }
                }
            }
            
            log.info("Bootstrap read {} total messages, processed {} orderbook messages", 
                    totalMessages, processedMessages);
            
        } catch (Exception e) {
            log.error("Error during bootstrap", e);
        }
        
        return marketStates;
    }
    
    private void processOrderBookMessage(Map<String, OrderBookState> marketStates, 
                                       String marketTicker, Map<String, Object> payload) {
        String channel = (String) payload.get("channel");
        Object seqObj = payload.get("seq");
        Long sequence = seqObj != null ? ((Number) seqObj).longValue() : null;
        
        OrderBookState state = marketStates.computeIfAbsent(marketTicker, 
            k -> {
                OrderBookState newState = new OrderBookState();
                newState.setMarketTicker(marketTicker);
                return newState;
            });
        
        if ("orderbook_snapshot".equals(channel)) {
            state.applySnapshot(payload, sequence);
        } else if ("orderbook_delta".equals(channel)) {
            state.applyDelta(payload, sequence);
        }
    }
}