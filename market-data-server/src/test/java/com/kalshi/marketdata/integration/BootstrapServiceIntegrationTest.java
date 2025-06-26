package com.kalshi.marketdata.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kalshi.marketdata.model.OrderBookState;
import com.kalshi.marketdata.service.BootstrapService;
import com.kalshi.marketdata.service.OrderBookManager;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@EmbeddedKafka(partitions = 3, 
               topics = {"market-data-all"},
               brokerProperties = {"log.dir=target/kafka-logs"})
@TestPropertySource(properties = {
    "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
    "bootstrap.enabled=false", // Disable auto-bootstrap
    "bootstrap.lookback.minutes=5",
    "logging.level.com.kalshi.marketdata=DEBUG"
})
@DirtiesContext
class BootstrapServiceIntegrationTest {

    @Autowired
    private BootstrapService bootstrapService;

    @Autowired
    private OrderBookManager orderBookManager;

    @Autowired
    private EmbeddedKafkaBroker embeddedKafka;

    private ObjectMapper objectMapper = new ObjectMapper();
    private KafkaProducer<String, String> producer;

    @BeforeEach
    void setUp() {
        // Clear any existing state
        orderBookManager.clearAll();
        
        // Create Kafka producer
        Map<String, Object> producerProps = KafkaTestUtils.producerProps(embeddedKafka);
        producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, 
                         "org.apache.kafka.common.serialization.StringSerializer");
        producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, 
                         "org.apache.kafka.common.serialization.StringSerializer");
        producer = new KafkaProducer<>(producerProps);
    }

    @AfterEach
    void tearDown() {
        if (producer != null) {
            producer.close();
        }
    }

    @Test
    void testBootstrapLoadsHistoricalOrderBookData() throws Exception {
        // Given - Publish historical messages to Kafka
        long now = System.currentTimeMillis();
        long twoMinutesAgo = now - TimeUnit.MINUTES.toMillis(2);
        long fourMinutesAgo = now - TimeUnit.MINUTES.toMillis(4);
        
        // Publish old snapshot (should be loaded)
        publishMessage("MARKET1", createSnapshotEnvelope("MARKET1", 100L, 
                Arrays.asList(Arrays.asList(65, 100)), 
                Arrays.asList(Arrays.asList(35, 150))), fourMinutesAgo);
        
        // Publish delta update (should be applied)
        publishMessage("MARKET1", createDeltaEnvelope("MARKET1", 101L, "yes", 65, 50), twoMinutesAgo);
        
        // Publish another market
        publishMessage("MARKET2", createSnapshotEnvelope("MARKET2", 200L, 
                Arrays.asList(Arrays.asList(70, 200)), 
                Arrays.asList(Arrays.asList(30, 300))), twoMinutesAgo);
        
        // Wait for messages to be committed
        producer.flush();
        Thread.sleep(1000);
        
        // When - Run bootstrap
        Map<String, OrderBookState> bootstrappedStates = bootstrapService.loadHistoricalData();
        
        // Load states into manager
        for (Map.Entry<String, OrderBookState> entry : bootstrappedStates.entrySet()) {
            orderBookManager.loadHistoricalState(entry.getKey(), entry.getValue());
        }
        
        // Then
        assertEquals(2, bootstrappedStates.size());
        assertTrue(orderBookManager.isMarketBootstrapped("MARKET1"));
        assertTrue(orderBookManager.isMarketBootstrapped("MARKET2"));
        
        // Verify MARKET1 has delta applied
        OrderBookState market1State = orderBookManager.getOrderBookState("MARKET1");
        assertNotNull(market1State);
        assertEquals(101L, market1State.getLastSequence());
        assertEquals(150, market1State.getYesBids().get(65)); // 100 + 50
        
        // Verify MARKET2 snapshot
        OrderBookState market2State = orderBookManager.getOrderBookState("MARKET2");
        assertNotNull(market2State);
        assertEquals(200L, market2State.getLastSequence());
        assertEquals(200, market2State.getYesBids().get(70));
    }

    @Test
    void testBootstrapIgnoresOldMessages() throws Exception {
        // Given - Publish messages older than lookback window
        long now = System.currentTimeMillis();
        long tenMinutesAgo = now - TimeUnit.MINUTES.toMillis(10);
        
        publishMessage("OLD-MARKET", createSnapshotEnvelope("OLD-MARKET", 50L, 
                Arrays.asList(Arrays.asList(60, 100)), 
                Arrays.asList(Arrays.asList(40, 100))), tenMinutesAgo);
        
        producer.flush();
        Thread.sleep(1000);
        
        // When
        Map<String, OrderBookState> bootstrappedStates = bootstrapService.loadHistoricalData();
        
        // Then
        assertTrue(bootstrappedStates.isEmpty());
    }

    @Test
    void testBootstrapHandlesMultipleSnapshotsKeepsLatest() throws Exception {
        // Given - Multiple snapshots for same market
        long now = System.currentTimeMillis();
        long threeMinutesAgo = now - TimeUnit.MINUTES.toMillis(3);
        long twoMinutesAgo = now - TimeUnit.MINUTES.toMillis(2);
        
        // Older snapshot
        publishMessage("MARKET1", createSnapshotEnvelope("MARKET1", 100L, 
                Arrays.asList(Arrays.asList(65, 100)), 
                Arrays.asList(Arrays.asList(35, 150))), threeMinutesAgo);
        
        // Newer snapshot (should replace the older one)
        publishMessage("MARKET1", createSnapshotEnvelope("MARKET1", 110L, 
                Arrays.asList(Arrays.asList(66, 200)), 
                Arrays.asList(Arrays.asList(34, 300))), twoMinutesAgo);
        
        producer.flush();
        Thread.sleep(1000);
        
        // When
        Map<String, OrderBookState> bootstrappedStates = bootstrapService.loadHistoricalData();
        
        // Then
        assertEquals(1, bootstrappedStates.size());
        OrderBookState state = bootstrappedStates.get("MARKET1");
        assertEquals(110L, state.getLastSequence());
        assertEquals(200, state.getYesBids().get(66));
        assertFalse(state.getYesBids().containsKey(65)); // Old price should be gone
    }

    @Test
    void testBootstrapSkipsDuplicateSnapshotsAfterLoad() throws Exception {
        // Given - Bootstrap with initial state
        long now = System.currentTimeMillis();
        long twoMinutesAgo = now - TimeUnit.MINUTES.toMillis(2);
        
        publishMessage("MARKET1", createSnapshotEnvelope("MARKET1", 100L, 
                Arrays.asList(Arrays.asList(65, 100)), 
                Arrays.asList(Arrays.asList(35, 150))), twoMinutesAgo);
        
        producer.flush();
        Thread.sleep(1000);
        
        Map<String, OrderBookState> bootstrappedStates = bootstrapService.loadHistoricalData();
        for (Map.Entry<String, OrderBookState> entry : bootstrappedStates.entrySet()) {
            orderBookManager.loadHistoricalState(entry.getKey(), entry.getValue());
        }
        
        // When - Receive identical snapshot
        Map<String, Object> identicalSnapshot = createSnapshot("MARKET1", 101L,
                Arrays.asList(Arrays.asList(65, 100)),
                Arrays.asList(Arrays.asList(35, 150)));
        
        boolean shouldPublish = orderBookManager.shouldPublishMessage(identicalSnapshot);
        
        // Then
        assertFalse(shouldPublish); // Should skip identical snapshot
    }

    // Helper methods
    private void publishMessage(String key, Map<String, Object> envelope, long timestamp) throws Exception {
        String json = objectMapper.writeValueAsString(envelope);
        ProducerRecord<String, String> record = new ProducerRecord<>("market-data-all", null, 
                timestamp, key, json);
        producer.send(record).get(5, TimeUnit.SECONDS);
    }

    private Map<String, Object> createSnapshotEnvelope(String marketTicker, Long sequence,
                                                      Object yesBids, Object noBids) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("channel", "orderbook_snapshot");
        payload.put("market_ticker", marketTicker);
        payload.put("seq", sequence);
        
        Map<String, Object> data = new HashMap<>();
        data.put("yes", yesBids);
        data.put("no", noBids);
        payload.put("data", data);
        
        return createEnvelope(payload, marketTicker, "orderbook_snapshot", sequence);
    }

    private Map<String, Object> createDeltaEnvelope(String marketTicker, Long sequence,
                                                   String side, Integer price, Integer delta) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("channel", "orderbook_delta");
        payload.put("market_ticker", marketTicker);
        payload.put("seq", sequence);
        
        Map<String, Object> data = new HashMap<>();
        data.put("side", side);
        data.put("price", price);
        data.put("delta", delta);
        payload.put("data", data);
        
        return createEnvelope(payload, marketTicker, "orderbook_delta", sequence);
    }

    private Map<String, Object> createEnvelope(Map<String, Object> payload, String marketTicker,
                                             String channel, Long sequence) {
        Map<String, Object> envelope = new HashMap<>();
        envelope.put("payload", payload);
        envelope.put("receivedTimestamp", System.currentTimeMillis());
        envelope.put("publishedTimestamp", System.currentTimeMillis());
        envelope.put("channel", channel);
        envelope.put("marketTicker", marketTicker);
        envelope.put("sequence", sequence);
        envelope.put("source", "kalshi-websocket");
        envelope.put("version", 1);
        return envelope;
    }

    private Map<String, Object> createSnapshot(String marketTicker, Long sequence,
                                             Object yesBids, Object noBids) {
        Map<String, Object> message = new HashMap<>();
        message.put("channel", "orderbook_snapshot");
        message.put("market_ticker", marketTicker);
        message.put("seq", sequence);
        
        Map<String, Object> data = new HashMap<>();
        data.put("yes", yesBids);
        data.put("no", noBids);
        message.put("data", data);
        
        return message;
    }
}