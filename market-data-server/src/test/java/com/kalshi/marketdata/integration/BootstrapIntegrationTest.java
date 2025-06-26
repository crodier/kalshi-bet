package com.kalshi.marketdata.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kalshi.marketdata.model.OrderBookState;
import com.kalshi.marketdata.service.BootstrapService;
import com.kalshi.marketdata.service.OrderBookManager;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.TestPropertySource;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for bootstrap functionality and restart scenarios
 */
@SpringBootTest
@EmbeddedKafka(partitions = 1, topics = {"market-data-all"})
@TestPropertySource(properties = {
    "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
    "bootstrap.enabled=true",
    "bootstrap.lookback.minutes=5",
    "mock.kalshi.rest.url=http://localhost:9999",
    "mock.kalshi.websocket.url=ws://localhost:9999/test"
})
public class BootstrapIntegrationTest {

    @Autowired
    private BootstrapService bootstrapService;
    
    @Autowired
    private OrderBookManager orderBookManager;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;
    
    @Value("${kafka.topic.market-data}")
    private String kafkaTopic;
    
    private KafkaProducer<String, String> producer;
    
    @BeforeEach
    void setUp() {
        // Clear any existing state
        orderBookManager.clearAll();
        
        // Setup Kafka producer for test data
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, 
                "org.apache.kafka.common.serialization.StringSerializer");
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, 
                "org.apache.kafka.common.serialization.StringSerializer");
        
        producer = new KafkaProducer<>(props);
    }
    
    @Test
    void testBootstrapLoadsHistoricalData() throws Exception {
        // Given: Publish some test market data to Kafka
        String marketTicker = "TEST-MARKET-001";
        
        // Create snapshot message
        Map<String, Object> snapshotEnvelope = Map.of(
            "payload", Map.of(
                "channel", "orderbook_snapshot",
                "market_ticker", marketTicker,
                "seq", 100L,
                "yes", Map.of(
                    "bids", Map.of("50", 100, "49", 200),
                    "asks", Map.of("51", 150, "52", 250)
                ),
                "no", Map.of(
                    "bids", Map.of("48", 120, "47", 180),
                    "asks", Map.of("53", 160, "54", 240)
                )
            ),
            "receivedTimestamp", Instant.now().minus(2, ChronoUnit.MINUTES).toEpochMilli(),
            "publishedTimestamp", Instant.now().minus(2, ChronoUnit.MINUTES).toEpochMilli(),
            "channel", "orderbook_snapshot",
            "marketTicker", marketTicker,
            "sequence", 100L,
            "source", "kalshi-websocket",
            "version", 1
        );
        
        // Create delta message
        Map<String, Object> deltaEnvelope = Map.of(
            "payload", Map.of(
                "channel", "orderbook_delta",
                "market_ticker", marketTicker,
                "seq", 101L,
                "yes", Map.of(
                    "bids", Map.of("50", 120)  // Updated quantity
                )
            ),
            "receivedTimestamp", Instant.now().minus(1, ChronoUnit.MINUTES).toEpochMilli(),
            "publishedTimestamp", Instant.now().minus(1, ChronoUnit.MINUTES).toEpochMilli(),
            "channel", "orderbook_delta",
            "marketTicker", marketTicker,
            "sequence", 101L,
            "source", "kalshi-websocket",
            "version", 1
        );
        
        // Publish test messages
        producer.send(new ProducerRecord<>(kafkaTopic, marketTicker, 
                objectMapper.writeValueAsString(snapshotEnvelope))).get();
        producer.send(new ProducerRecord<>(kafkaTopic, marketTicker,
                objectMapper.writeValueAsString(deltaEnvelope))).get();
        
        // Wait for messages to be committed
        Thread.sleep(1000);
        
        // When: Run bootstrap
        CompletableFuture<Void> bootstrapFuture = bootstrapService.bootstrapAsync();
        bootstrapFuture.get(30, TimeUnit.SECONDS);
        
        // Then: Verify state was loaded
        assertTrue(orderBookManager.getTrackedMarketCount() > 0);
        
        OrderBookState state = orderBookManager.getOrderBookState(marketTicker);
        assertNotNull(state);
        assertEquals(marketTicker, state.getMarketTicker());
        assertEquals(101L, state.getLastSequence()); // Should have applied both snapshot and delta
        
        // Verify order book has expected data (after delta update)
        assertEquals(120, state.getYesBids().get(50)); // Updated from 100 to 120
        assertEquals(200, state.getYesBids().get(49)); // Unchanged
    }
    
    @Test
    void testBootstrapHandlesEmptyKafka() throws Exception {
        // Given: Empty Kafka topic (no historical data)
        
        // When: Run bootstrap
        CompletableFuture<Void> bootstrapFuture = bootstrapService.bootstrapAsync();
        bootstrapFuture.get(10, TimeUnit.SECONDS);
        
        // Then: Should complete without error and have empty state
        assertEquals(0, orderBookManager.getTrackedMarketCount());
    }
    
    @Test
    void testBootstrapWithMultipleMarkets() throws Exception {
        // Given: Multiple markets with data
        String market1 = "MARKET-001";
        String market2 = "MARKET-002";
        
        Map<String, Object> market1Envelope = Map.of(
            "payload", Map.of(
                "channel", "orderbook_snapshot",
                "market_ticker", market1,
                "seq", 200L,
                "yes", Map.of("bids", Map.of("60", 300))
            ),
            "receivedTimestamp", Instant.now().minus(1, ChronoUnit.MINUTES).toEpochMilli(),
            "publishedTimestamp", Instant.now().minus(1, ChronoUnit.MINUTES).toEpochMilli(),
            "channel", "orderbook_snapshot",
            "marketTicker", market1,
            "sequence", 200L,
            "source", "kalshi-websocket",
            "version", 1
        );
        
        Map<String, Object> market2Envelope = Map.of(
            "payload", Map.of(
                "channel", "orderbook_snapshot", 
                "market_ticker", market2,
                "seq", 300L,
                "no", Map.of("bids", Map.of("40", 500))
            ),
            "receivedTimestamp", Instant.now().minus(1, ChronoUnit.MINUTES).toEpochMilli(),
            "publishedTimestamp", Instant.now().minus(1, ChronoUnit.MINUTES).toEpochMilli(),
            "channel", "orderbook_snapshot",
            "marketTicker", market2,
            "sequence", 300L,
            "source", "kalshi-websocket",
            "version", 1
        );
        
        // Publish both markets
        producer.send(new ProducerRecord<>(kafkaTopic, market1, 
                objectMapper.writeValueAsString(market1Envelope))).get();
        producer.send(new ProducerRecord<>(kafkaTopic, market2,
                objectMapper.writeValueAsString(market2Envelope))).get();
        
        Thread.sleep(1000);
        
        // When: Bootstrap
        CompletableFuture<Void> bootstrapFuture = bootstrapService.bootstrapAsync();
        bootstrapFuture.get(30, TimeUnit.SECONDS);
        
        // Then: Both markets should be loaded
        assertEquals(2, orderBookManager.getTrackedMarketCount());
        assertNotNull(orderBookManager.getOrderBookState(market1));
        assertNotNull(orderBookManager.getOrderBookState(market2));
        
        assertEquals(200L, orderBookManager.getOrderBookState(market1).getLastSequence());
        assertEquals(300L, orderBookManager.getOrderBookState(market2).getLastSequence());
    }
    
    @Test
    void testBootstrapIgnoresNonOrderbookMessages() throws Exception {
        // Given: Mix of orderbook and non-orderbook messages
        String marketTicker = "TEST-MARKET-003";
        
        Map<String, Object> tradeEnvelope = Map.of(
            "payload", Map.of(
                "channel", "trade",
                "market_ticker", marketTicker,
                "price", 55,
                "quantity", 100
            ),
            "receivedTimestamp", Instant.now().minus(1, ChronoUnit.MINUTES).toEpochMilli(),
            "publishedTimestamp", Instant.now().minus(1, ChronoUnit.MINUTES).toEpochMilli(),
            "channel", "trade",
            "marketTicker", marketTicker,
            "source", "kalshi-websocket",
            "version", 1
        );
        
        Map<String, Object> orderbookEnvelope = Map.of(
            "payload", Map.of(
                "channel", "orderbook_snapshot",
                "market_ticker", marketTicker,
                "seq", 400L,
                "yes", Map.of("bids", Map.of("55", 200))
            ),
            "receivedTimestamp", Instant.now().minus(1, ChronoUnit.MINUTES).toEpochMilli(),
            "publishedTimestamp", Instant.now().minus(1, ChronoUnit.MINUTES).toEpochMilli(),
            "channel", "orderbook_snapshot",
            "marketTicker", marketTicker,
            "sequence", 400L,
            "source", "kalshi-websocket",
            "version", 1
        );
        
        // Publish both messages
        producer.send(new ProducerRecord<>(kafkaTopic, marketTicker,
                objectMapper.writeValueAsString(tradeEnvelope))).get();
        producer.send(new ProducerRecord<>(kafkaTopic, marketTicker,
                objectMapper.writeValueAsString(orderbookEnvelope))).get();
        
        Thread.sleep(1000);
        
        // When: Bootstrap
        CompletableFuture<Void> bootstrapFuture = bootstrapService.bootstrapAsync();
        bootstrapFuture.get(30, TimeUnit.SECONDS);
        
        // Then: Only orderbook message should affect state
        assertEquals(1, orderBookManager.getTrackedMarketCount());
        OrderBookState state = orderBookManager.getOrderBookState(marketTicker);
        assertNotNull(state);
        assertEquals(400L, state.getLastSequence());
        assertEquals(200, state.getYesBids().get(55));
    }
}