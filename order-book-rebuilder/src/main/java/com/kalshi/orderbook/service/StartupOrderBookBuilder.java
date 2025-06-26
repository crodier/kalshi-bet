package com.kalshi.orderbook.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class StartupOrderBookBuilder {
    
    private final ConsumerFactory<String, String> stringConsumerFactory;
    private final MarketDataProcessor marketDataProcessor;
    
    @Value("${orderbook.startup.rewind.enabled:true}")
    private boolean rewindEnabled;
    
    @Value("${orderbook.startup.rewind.minutes:60}")
    private int rewindMinutes;
    
    @Value("${kafka.topic.market-data}")
    private String marketDataTopic;
    
    @EventListener(ApplicationReadyEvent.class)
    public void buildOrderBooksFromHistory() {
        if (!rewindEnabled) {
            log.info("Startup rewind disabled");
            return;
        }
        
        log.info("Starting order book rebuild from {} minutes of history", rewindMinutes);
        
        try (Consumer<String, String> consumer = stringConsumerFactory.createConsumer("startup-builder", "startup-builder")) {
            TopicPartition partition = new TopicPartition(marketDataTopic, 0);
            consumer.assign(Collections.singletonList(partition));
            
            // Calculate timestamp for rewind
            long rewindTimestamp = Instant.now().minus(Duration.ofMinutes(rewindMinutes)).toEpochMilli();
            
            // Get offset for timestamp
            Map<TopicPartition, Long> timestampToSearch = Map.of(partition, rewindTimestamp);
            Map<TopicPartition, org.apache.kafka.clients.consumer.OffsetAndTimestamp> offsetMap = 
                consumer.offsetsForTimes(timestampToSearch);
            
            if (offsetMap.get(partition) != null) {
                long startOffset = offsetMap.get(partition).offset();
                consumer.seek(partition, startOffset);
                log.info("Rewinding to offset {} (timestamp: {})", startOffset, rewindTimestamp);
            } else {
                consumer.seekToBeginning(Collections.singletonList(partition));
                log.info("No offset found for timestamp, starting from beginning");
            }
            
            int messagesProcessed = 0;
            boolean reachedEnd = false;
            
            while (!reachedEnd) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(1));
                
                if (records.isEmpty()) {
                    // Check if we've reached the end by getting current end offset
                    Map<TopicPartition, Long> endOffsets = consumer.endOffsets(Collections.singletonList(partition));
                    long currentPosition = consumer.position(partition);
                    long endOffset = endOffsets.get(partition);
                    
                    if (currentPosition >= endOffset) {
                        reachedEnd = true;
                        log.info("Reached end of topic at offset {}", currentPosition);
                    }
                    continue;
                }
                
                for (ConsumerRecord<String, String> record : records) {
                    try {
                        // Parse and process the message
                        com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
                        com.kalshi.orderbook.model.MarketDataEnvelope envelope = 
                            objectMapper.readValue(record.value(), com.kalshi.orderbook.model.MarketDataEnvelope.class);
                        
                        marketDataProcessor.processMarketData(envelope);
                        messagesProcessed++;
                        
                        if (messagesProcessed % 1000 == 0) {
                            log.debug("Processed {} historical messages", messagesProcessed);
                        }
                        
                    } catch (Exception e) {
                        log.warn("Failed to process historical message at offset {}: {}", record.offset(), e.getMessage());
                    }
                }
            }
            
            log.info("Completed startup rebuild: processed {} historical messages", messagesProcessed);
            
        } catch (Exception e) {
            log.error("Failed to rebuild order books from history", e);
        }
    }
}