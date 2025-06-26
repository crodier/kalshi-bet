package com.kalshi.orderbook.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kalshi.orderbook.model.MarketDataEnvelope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.net.URI;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
    "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
    "orderbook.startup.rewind.enabled=false"
})
public class WebSocketIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private WebSocketStompClient stompClient;
    private StompSession stompSession;
    private BlockingQueue<String> blockingQueue;

    @BeforeEach
    public void setup() throws Exception {
        this.stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        this.stompClient.setMessageConverter(new MappingJackson2MessageConverter());
        this.blockingQueue = new LinkedBlockingQueue<>();
        
        String wsUrl = "ws://localhost:" + port + "/trade-api/ws/v2";
        this.stompSession = stompClient.connect(wsUrl, new StompSessionHandlerAdapter() {}).get(5, TimeUnit.SECONDS);
    }

    @Test
    public void testWebSocketOrderBookUpdates() throws Exception {
        String testMarket = "TEST_MARKET_WS";
        
        // Subscribe to order book updates for the test market
        StompSession.Subscription subscription = stompSession.subscribe(
            "/topic/orderbook/" + testMarket,
            new StompFrameHandler() {
                @Override
                public Type getPayloadType(StompHeaders headers) {
                    return String.class;
                }

                @Override
                public void handleFrame(StompHeaders headers, Object payload) {
                    blockingQueue.offer((String) payload);
                }
            }
        );

        // Create test market data envelope with order book snapshot
        MarketDataEnvelope envelope = createTestOrderBookSnapshot(testMarket);

        // Publish to Kafka
        kafkaTemplate.send("MARKET-DATA-ALL", objectMapper.writeValueAsString(envelope));

        // Wait for WebSocket message
        String message = blockingQueue.poll(10, TimeUnit.SECONDS);
        assertNotNull(message, "Should receive WebSocket message within 10 seconds");

        // Parse the received message
        JsonNode messageNode = objectMapper.readTree(message);
        assertNotNull(messageNode, "Message should be valid JSON");
        
        // Verify message structure
        assertTrue(messageNode.has("marketTicker"), "Message should contain marketTicker");
        assertEquals(testMarket, messageNode.get("marketTicker").asText());

        subscription.unsubscribe();
    }

    @Test
    public void testWebSocketMarketDataUpdates() throws Exception {
        String testMarket = "TEST_MARKET_MD";
        
        // Subscribe to market data updates
        StompSession.Subscription subscription = stompSession.subscribe(
            "/topic/market/" + testMarket,
            new StompFrameHandler() {
                @Override
                public Type getPayloadType(StompHeaders headers) {
                    return String.class;
                }

                @Override
                public void handleFrame(StompHeaders headers, Object payload) {
                    blockingQueue.offer((String) payload);
                }
            }
        );

        // Create test market data envelope with delta update
        MarketDataEnvelope envelope = createTestOrderBookDelta(testMarket);

        // Publish to Kafka
        kafkaTemplate.send("MARKET-DATA-ALL", objectMapper.writeValueAsString(envelope));

        // Wait for WebSocket message
        String message = blockingQueue.poll(10, TimeUnit.SECONDS);
        assertNotNull(message, "Should receive WebSocket message within 10 seconds");

        // Parse the received message
        JsonNode messageNode = objectMapper.readTree(message);
        assertNotNull(messageNode, "Message should be valid JSON");

        subscription.unsubscribe();
    }

    @Test
    public void testMultipleClientSubscriptions() throws Exception {
        String testMarket = "TEST_MARKET_MULTI";
        
        // Create second client
        WebSocketStompClient stompClient2 = new WebSocketStompClient(new StandardWebSocketClient());
        stompClient2.setMessageConverter(new MappingJackson2MessageConverter());
        
        String wsUrl = "ws://localhost:" + port + "/trade-api/ws/v2";
        StompSession stompSession2 = stompClient2.connect(wsUrl, new StompSessionHandlerAdapter() {}).get(5, TimeUnit.SECONDS);
        
        BlockingQueue<String> blockingQueue2 = new LinkedBlockingQueue<>();
        
        // Subscribe both clients to the same market
        StompSession.Subscription subscription1 = stompSession.subscribe(
            "/topic/orderbook/" + testMarket,
            new StompFrameHandler() {
                @Override
                public Type getPayloadType(StompHeaders headers) {
                    return String.class;
                }

                @Override
                public void handleFrame(StompHeaders headers, Object payload) {
                    blockingQueue.offer((String) payload);
                }
            }
        );

        StompSession.Subscription subscription2 = stompSession2.subscribe(
            "/topic/orderbook/" + testMarket,
            new StompFrameHandler() {
                @Override
                public Type getPayloadType(StompHeaders headers) {
                    return String.class;
                }

                @Override
                public void handleFrame(StompHeaders headers, Object payload) {
                    blockingQueue2.offer((String) payload);
                }
            }
        );

        // Create and publish test data
        MarketDataEnvelope envelope = createTestOrderBookSnapshot(testMarket);
        kafkaTemplate.send("MARKET-DATA-ALL", objectMapper.writeValueAsString(envelope));

        // Both clients should receive the message
        String message1 = blockingQueue.poll(10, TimeUnit.SECONDS);
        String message2 = blockingQueue2.poll(10, TimeUnit.SECONDS);
        
        assertNotNull(message1, "First client should receive message");
        assertNotNull(message2, "Second client should receive message");
        
        // Messages should be the same
        assertEquals(message1, message2, "Both clients should receive identical messages");

        subscription1.unsubscribe();
        subscription2.unsubscribe();
        stompSession2.disconnect();
    }

    private MarketDataEnvelope createTestOrderBookSnapshot(String marketTicker) {
        String payload = """
            {
                "msg": {
                    "yes": [[55, 100], [54, 200], [53, 300]],
                    "no": [[45, 150], [46, 250], [47, 350]]
                }
            }
            """;
        
        try {
            JsonNode payloadNode = objectMapper.readTree(payload);
            MarketDataEnvelope envelope = new MarketDataEnvelope();
            envelope.setChannel("orderbook_snapshot");
            envelope.setMarketTicker(marketTicker);
            envelope.setPayload(payloadNode);
            envelope.setReceivedTimestamp(System.currentTimeMillis());
            envelope.setPublishedTimestamp(System.currentTimeMillis() - 10);
            return envelope;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private MarketDataEnvelope createTestOrderBookDelta(String marketTicker) {
        String payload = """
            {
                "msg": {
                    "price": 55,
                    "delta": 50,
                    "side": "yes"
                }
            }
            """;
        
        try {
            JsonNode payloadNode = objectMapper.readTree(payload);
            MarketDataEnvelope envelope = new MarketDataEnvelope();
            envelope.setChannel("orderbook_delta");
            envelope.setMarketTicker(marketTicker);
            envelope.setPayload(payloadNode);
            envelope.setReceivedTimestamp(System.currentTimeMillis());
            envelope.setPublishedTimestamp(System.currentTimeMillis() - 5);
            return envelope;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}