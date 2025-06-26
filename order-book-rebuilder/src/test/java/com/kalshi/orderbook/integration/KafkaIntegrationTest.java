package com.kalshi.orderbook.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.kalshi.orderbook.model.MarketDataEnvelope;
import com.kalshi.orderbook.service.OrderBookManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@EmbeddedKafka(partitions = 1, topics = {"MARKET-DATA-ALL"})
@DirtiesContext
@ActiveProfiles("test")
public class KafkaIntegrationTest {
    
    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;
    
    @Autowired
    private OrderBookManager orderBookManager;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @Value("${kafka.topic.market-data}")
    private String marketDataTopic;
    
    @Test
    public void testKafkaOrderBookIntegration() throws Exception {
        String testMarket = "SELF_TEST_MARKET";
        
        // Create a snapshot message
        ObjectNode snapshotPayload = createSnapshotMessage(testMarket);
        MarketDataEnvelope snapshotEnvelope = new MarketDataEnvelope();
        snapshotEnvelope.setPayload(snapshotPayload);
        snapshotEnvelope.setChannel("orderbook_snapshot");
        snapshotEnvelope.setMarketTicker(testMarket);
        snapshotEnvelope.setReceivedTimestamp(System.currentTimeMillis() - 100);
        snapshotEnvelope.setPublishedTimestamp(System.currentTimeMillis());
        snapshotEnvelope.setSequence(1L);
        
        // Send snapshot to Kafka
        String snapshotJson = objectMapper.writeValueAsString(snapshotEnvelope);
        kafkaTemplate.send(marketDataTopic, testMarket, snapshotJson);
        
        // Wait for message to be processed
        await().atMost(10, TimeUnit.SECONDS)
            .until(() -> orderBookManager.hasOrderBook(testMarket));
        
        // Verify order book was created and populated
        var orderBook = orderBookManager.getOrderBook(testMarket);
        assertNotNull(orderBook);
        assertEquals(testMarket, orderBook.getMarketTicker());
        
        // Verify yes side has levels
        var yesSide = orderBook.getYesSideSnapshot();
        assertFalse(yesSide.isEmpty());
        assertEquals(55, yesSide.get(0).getPrice()); // Best yes price
        assertEquals(1000, yesSide.get(0).getSize());
        
        // Verify no side has levels
        var noSide = orderBook.getNoSideSnapshot();
        assertFalse(noSide.isEmpty());
        assertEquals(45, noSide.get(0).getPrice()); // Best no price
        assertEquals(1500, noSide.get(0).getSize());
        
        // Create and send a delta message
        ObjectNode deltaPayload = createDeltaMessage(testMarket, 56, 500, "yes");
        MarketDataEnvelope deltaEnvelope = new MarketDataEnvelope();
        deltaEnvelope.setPayload(deltaPayload);
        deltaEnvelope.setChannel("orderbook_delta");
        deltaEnvelope.setMarketTicker(testMarket);
        deltaEnvelope.setReceivedTimestamp(System.currentTimeMillis() - 50);
        deltaEnvelope.setPublishedTimestamp(System.currentTimeMillis());
        deltaEnvelope.setSequence(2L);
        
        String deltaJson = objectMapper.writeValueAsString(deltaEnvelope);
        kafkaTemplate.send(marketDataTopic, testMarket, deltaJson);
        
        // Wait for delta to be processed
        await().atMost(5, TimeUnit.SECONDS)
            .until(() -> {
                var updated = orderBookManager.getOrderBook(testMarket);
                return updated.getYesSideSnapshot().stream()
                    .anyMatch(level -> level.getPrice() == 56);
            });
        
        // Verify delta was applied
        var updatedOrderBook = orderBookManager.getOrderBook(testMarket);
        var updatedYesSide = updatedOrderBook.getYesSideSnapshot();
        
        boolean hasNewLevel = updatedYesSide.stream()
            .anyMatch(level -> level.getPrice() == 56 && level.getSize() == 500);
        assertTrue(hasNewLevel, "Delta update should have added new level at price 56");
    }
    
    private ObjectNode createSnapshotMessage(String marketTicker) {
        ObjectNode message = objectMapper.createObjectNode();
        message.put("type", "orderbook_snapshot");
        message.put("seq", 1);
        
        ObjectNode msg = message.putObject("msg");
        msg.put("market_ticker", marketTicker);
        msg.put("ts", System.currentTimeMillis());
        
        // Create yes side levels (prices in cents)
        ArrayNode yesSide = msg.putArray("yes");
        yesSide.add(objectMapper.createArrayNode().add(55).add(1000)); // 55 cents, 1000 size
        yesSide.add(objectMapper.createArrayNode().add(54).add(800));  // 54 cents, 800 size
        yesSide.add(objectMapper.createArrayNode().add(53).add(600));  // 53 cents, 600 size
        
        // Create no side levels
        ArrayNode noSide = msg.putArray("no");
        noSide.add(objectMapper.createArrayNode().add(45).add(1500)); // 45 cents, 1500 size
        noSide.add(objectMapper.createArrayNode().add(46).add(1200)); // 46 cents, 1200 size
        noSide.add(objectMapper.createArrayNode().add(47).add(900));  // 47 cents, 900 size
        
        return message;
    }
    
    private ObjectNode createDeltaMessage(String marketTicker, int price, long delta, String side) {
        ObjectNode message = objectMapper.createObjectNode();
        message.put("type", "orderbook_delta");
        message.put("seq", 2);
        
        ObjectNode msg = message.putObject("msg");
        msg.put("market_ticker", marketTicker);
        msg.put("price", price);
        msg.put("delta", delta);
        msg.put("side", side);
        msg.put("ts", System.currentTimeMillis());
        
        return message;
    }
}