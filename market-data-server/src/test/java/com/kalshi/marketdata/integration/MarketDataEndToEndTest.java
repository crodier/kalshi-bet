package com.kalshi.marketdata.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kalshi.marketdata.service.OrderBookManager;
import com.kalshi.marketdata.websocket.KalshiWebSocketClient;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

import java.net.URI;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@EmbeddedKafka(partitions = 3, 
               topics = {"market-data-all"},
               brokerProperties = {"log.dir=target/kafka-logs"})
@TestPropertySource(properties = {
    "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
    "bootstrap.enabled=false",
    "bootstrap.lookback.minutes=5",
    "logging.level.com.kalshi.marketdata=DEBUG"
})
@DirtiesContext
class MarketDataEndToEndTest {

    @Autowired
    private OrderBookManager orderBookManager;

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private EmbeddedKafkaBroker embeddedKafka;

    private ObjectMapper objectMapper = new ObjectMapper();
    private KafkaProducer<String, String> producer;
    private KafkaConsumer<String, String> consumer;

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
        
        // Create Kafka consumer
        Map<String, Object> consumerProps = KafkaTestUtils.consumerProps("test-group", "true", embeddedKafka);
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, 
                         "org.apache.kafka.common.serialization.StringDeserializer");
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, 
                         "org.apache.kafka.common.serialization.StringDeserializer");
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        consumer = new KafkaConsumer<>(consumerProps);
        consumer.subscribe(Collections.singletonList("market-data-all"));
    }

    @AfterEach
    void tearDown() {
        if (producer != null) producer.close();
        if (consumer != null) consumer.close();
    }

    @Test
    void testCompleteFlowWithBootstrapAndDeduplication() throws Exception {
        // Step 1: Simulate historical data for bootstrap
        long now = System.currentTimeMillis();
        long threeMinutesAgo = now - TimeUnit.MINUTES.toMillis(3);
        
        // Publish historical snapshot
        publishHistoricalMessage("MARKET1", createSnapshotPayload("MARKET1", 100L,
                Arrays.asList(Arrays.asList(65, 100), Arrays.asList(64, 200)),
                Arrays.asList(Arrays.asList(35, 150))), threeMinutesAgo);
        
        // Publish historical delta
        publishHistoricalMessage("MARKET1", createDeltaPayload("MARKET1", 101L, "yes", 65, 50), 
                               threeMinutesAgo + 1000);
        
        producer.flush();
        Thread.sleep(1000);
        
        // Step 2: Bootstrap the state
        // Simulate what BootstrapService would do
        orderBookManager.loadHistoricalState("MARKET1", createBootstrappedState("MARKET1", 101L, 65, 150));
        
        // Step 3: Create WebSocket client
        KalshiWebSocketClient wsClient = new KalshiWebSocketClient(
                new URI("ws://localhost:9999"), // dummy URI for test
                kafkaTemplate,
                objectMapper,
                orderBookManager,
                "market-data-all"
        );
        
        // Step 4: Process incoming messages
        // Identical snapshot - should be skipped
        String identicalSnapshot = objectMapper.writeValueAsString(createSnapshot("MARKET1", 102L,
                Arrays.asList(Arrays.asList(65, 150), Arrays.asList(64, 200)),
                Arrays.asList(Arrays.asList(35, 150))));
        
        wsClient.onMessage(identicalSnapshot);
        
        // Different snapshot - should be published
        String differentSnapshot = objectMapper.writeValueAsString(createSnapshot("MARKET1", 103L,
                Arrays.asList(Arrays.asList(66, 100), Arrays.asList(65, 150)),
                Arrays.asList(Arrays.asList(34, 200))));
        
        wsClient.onMessage(differentSnapshot);
        
        // Delta update - should be published
        String deltaUpdate = objectMapper.writeValueAsString(createDelta("MARKET1", 104L, "yes", 66, 50));
        wsClient.onMessage(deltaUpdate);
        
        // Non-orderbook message - should always be published
        String tickerUpdate = objectMapper.writeValueAsString(createTicker("MARKET1", 65));
        wsClient.onMessage(tickerUpdate);
        
        // Wait for messages to be sent
        Thread.sleep(2000);
        
        // Step 5: Verify published messages
        List<ConsumerRecord<String, String>> publishedRecords = new ArrayList<>();
        ConsumerRecords<String, String> records;
        int attempts = 0;
        while (attempts < 10) {
            records = consumer.poll(Duration.ofSeconds(1));
            records.forEach(publishedRecords::add);
            if (publishedRecords.size() >= 3) break; // Expecting 3 messages
            attempts++;
        }
        
        // Should have exactly 3 published messages (skipped the identical snapshot)
        assertEquals(3, publishedRecords.size());
        
        // Verify message types
        Set<String> publishedChannels = new HashSet<>();
        for (ConsumerRecord<String, String> record : publishedRecords) {
            Map<String, Object> envelope = objectMapper.readValue(record.value(), Map.class);
            String channel = (String) envelope.get("channel");
            publishedChannels.add(channel);
            
            // Verify envelope structure
            assertNotNull(envelope.get("payload"));
            assertNotNull(envelope.get("receivedTimestamp"));
            assertNotNull(envelope.get("publishedTimestamp"));
            assertEquals("kalshi-websocket", envelope.get("source"));
            assertEquals(1, envelope.get("version"));
        }
        
        assertTrue(publishedChannels.contains("orderbook_snapshot")); // Different snapshot
        assertTrue(publishedChannels.contains("orderbook_delta"));
        assertTrue(publishedChannels.contains("ticker_v2"));
        
        // Verify statistics
        Map<String, Long> stats = wsClient.getStatistics();
        assertEquals(4L, stats.get("totalMessagesReceived")); // All 4 messages
        assertEquals(3L, stats.get("messagesPublished")); // 3 published
        assertEquals(1L, stats.get("messagesSkipped")); // 1 skipped (identical snapshot)
    }

    @Test
    void testDeltaUpdatesOnlyPublishChanges() throws Exception {
        // Given - Bootstrapped state
        orderBookManager.loadHistoricalState("MARKET2", createBootstrappedState("MARKET2", 200L, 70, 1000));
        
        KalshiWebSocketClient wsClient = new KalshiWebSocketClient(
                new URI("ws://localhost:9999"),
                kafkaTemplate,
                objectMapper,
                orderBookManager,
                "market-data-all"
        );
        
        // When - Process multiple deltas
        // Delta that changes state
        wsClient.onMessage(objectMapper.writeValueAsString(createDelta("MARKET2", 201L, "yes", 70, -500)));
        
        // Delta with old sequence (should be skipped)
        wsClient.onMessage(objectMapper.writeValueAsString(createDelta("MARKET2", 199L, "yes", 70, 100)));
        
        // Delta that results in no change (remove from non-existent level)
        wsClient.onMessage(objectMapper.writeValueAsString(createDelta("MARKET2", 202L, "no", 25, -100)));
        
        Thread.sleep(1000);
        
        // Then - Verify only valid changes were published
        List<ConsumerRecord<String, String>> publishedRecords = new ArrayList<>();
        ConsumerRecords<String, String> records;
        int attempts = 0;
        while (attempts < 5) {
            records = consumer.poll(Duration.ofSeconds(1));
            records.forEach(publishedRecords::add);
            attempts++;
        }
        
        // Only the first delta should be published
        assertEquals(1, publishedRecords.size());
        
        Map<String, Object> envelope = objectMapper.readValue(publishedRecords.get(0).value(), Map.class);
        Map<String, Object> payload = (Map<String, Object>) envelope.get("payload");
        assertEquals("orderbook_delta", payload.get("channel"));
        assertEquals(201L, ((Number) payload.get("seq")).longValue());
    }

    // Helper methods
    private void publishHistoricalMessage(String key, Map<String, Object> payload, long timestamp) throws Exception {
        Map<String, Object> envelope = createEnvelope(payload, 
                (String) payload.get("market_ticker"),
                (String) payload.get("channel"),
                ((Number) payload.get("seq")).longValue());
        
        String json = objectMapper.writeValueAsString(envelope);
        ProducerRecord<String, String> record = new ProducerRecord<>("market-data-all", null, 
                timestamp, key, json);
        producer.send(record).get(5, TimeUnit.SECONDS);
    }

    private com.kalshi.marketdata.model.OrderBookState createBootstrappedState(String marketTicker, 
                                                                              Long sequence, 
                                                                              Integer yesPrice, 
                                                                              Integer yesQty) {
        com.kalshi.marketdata.model.OrderBookState state = new com.kalshi.marketdata.model.OrderBookState();
        state.setMarketTicker(marketTicker);
        state.setLastSequence(sequence);
        state.getYesBids().put(yesPrice, yesQty);
        state.getYesBids().put(64, 200); // Add another level
        state.getNoBids().put(35, 150);
        return state;
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

    private Map<String, Object> createSnapshotPayload(String marketTicker, Long sequence,
                                                    Object yesBids, Object noBids) {
        return createSnapshot(marketTicker, sequence, yesBids, noBids);
    }

    private Map<String, Object> createDelta(String marketTicker, Long sequence,
                                          String side, Integer price, Integer delta) {
        Map<String, Object> message = new HashMap<>();
        message.put("channel", "orderbook_delta");
        message.put("market_ticker", marketTicker);
        message.put("seq", sequence);
        
        Map<String, Object> data = new HashMap<>();
        data.put("side", side);
        data.put("price", price);
        data.put("delta", delta);
        message.put("data", data);
        
        return message;
    }

    private Map<String, Object> createDeltaPayload(String marketTicker, Long sequence,
                                                 String side, Integer price, Integer delta) {
        return createDelta(marketTicker, sequence, side, price, delta);
    }

    private Map<String, Object> createTicker(String marketTicker, Integer price) {
        Map<String, Object> message = new HashMap<>();
        message.put("channel", "ticker_v2");
        message.put("market_ticker", marketTicker);
        
        Map<String, Object> data = new HashMap<>();
        data.put("price", price);
        data.put("yes_bid", price - 1);
        data.put("yes_ask", price + 1);
        message.put("data", data);
        
        return message;
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
}