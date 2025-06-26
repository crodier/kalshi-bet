package com.kalshi.mock.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kalshi.mock.dto.KalshiOrderRequest;
import com.fbg.api.rest.OrderResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.socket.*;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@org.springframework.context.annotation.Import(com.kalshi.mock.config.TestFixConfiguration.class)
public class OrderWebSocketIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private String websocketUrl;
    private String baseUrl;

    @BeforeEach
    public void setup() {
        websocketUrl = "ws://localhost:" + port + "/trade-api/ws/v2";
        baseUrl = "http://localhost:" + port;
    }

    @Test
    public void testOrderCreationBroadcastsToWebSocket() throws Exception {
        // Setup WebSocket connection
        StandardWebSocketClient client = new StandardWebSocketClient();
        CountDownLatch connectionLatch = new CountDownLatch(1);
        CountDownLatch orderUpdateLatch = new CountDownLatch(1);
        CountDownLatch orderbookUpdateLatch = new CountDownLatch(1);
        
        List<String> receivedMessages = new ArrayList<>();
        AtomicBoolean receivedOrderUpdate = new AtomicBoolean(false);
        AtomicBoolean receivedOrderbookUpdate = new AtomicBoolean(false);

        WebSocketSession session = client.doHandshake(new WebSocketHandler() {
            @Override
            public void afterConnectionEstablished(WebSocketSession session) throws Exception {
                System.out.println("WebSocket connection established");
                connectionLatch.countDown();
                
                // Subscribe to both channels
                String subscribeMessage = """
                    {
                        "cmd": "subscribe",
                        "id": 1,
                        "params": {
                            "channels": ["orderbook_snapshot", "orders"],
                            "market_tickers": ["DUMMY_TEST"]
                        }
                    }
                """;
                session.sendMessage(new TextMessage(subscribeMessage));
                System.out.println("Sent subscription: " + subscribeMessage);
            }

            @Override
            public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) throws Exception {
                String payload = message.getPayload().toString();
                System.out.println("Received WebSocket message: " + payload);
                receivedMessages.add(payload);
                
                JsonNode json = objectMapper.readTree(payload);
                String type = json.get("type").asText();
                
                if ("order_update".equals(type)) {
                    System.out.println("Received order update!");
                    receivedOrderUpdate.set(true);
                    orderUpdateLatch.countDown();
                } else if ("orderbook_snapshot".equals(type)) {
                    System.out.println("Received orderbook snapshot!");
                    receivedOrderbookUpdate.set(true);
                    orderbookUpdateLatch.countDown();
                }
            }

            @Override
            public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
                System.err.println("WebSocket transport error: " + exception.getMessage());
            }

            @Override
            public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) throws Exception {
                System.out.println("WebSocket connection closed: " + closeStatus);
            }

            @Override
            public boolean supportsPartialMessages() {
                return false;
            }
        }, websocketUrl).get();

        // Wait for connection
        assertThat(connectionLatch.await(5, TimeUnit.SECONDS)).isTrue();
        
        // Give subscription time to process
        Thread.sleep(1000);

        // Create an order via REST API
        KalshiOrderRequest orderRequest = new KalshiOrderRequest();
        orderRequest.setAction("buy");
        orderRequest.setSide("yes");
        orderRequest.setType("limit");
        orderRequest.setPrice(48);
        orderRequest.setCount(10);
        orderRequest.setTimeInForce("GTC");
        orderRequest.setMarketTicker("DUMMY_TEST");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-User-Id", "TEST_USER");

        HttpEntity<KalshiOrderRequest> entity = new HttpEntity<>(orderRequest, headers);
        
        System.out.println("Creating order via REST API...");
        ResponseEntity<OrderResponse> response = restTemplate.exchange(
            baseUrl + "/trade-api/v2/portfolio/orders",
            HttpMethod.POST,
            entity,
            OrderResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getOrder()).isNotNull();
        System.out.println("Order created: " + response.getBody().getOrder().getId());

        // Wait for WebSocket updates
        boolean ordersReceived = orderUpdateLatch.await(5, TimeUnit.SECONDS);
        boolean orderbookReceived = orderbookUpdateLatch.await(5, TimeUnit.SECONDS);

        // Verify we received updates
        assertThat(receivedOrderUpdate.get())
            .as("Should have received order update on 'orders' channel")
            .isTrue();
            
        assertThat(receivedOrderbookUpdate.get())
            .as("Should have received orderbook update on 'orderbook_snapshot' channel")
            .isTrue();

        // Verify message contents
        boolean foundOrderUpdate = false;
        boolean foundOrderbookUpdate = false;
        
        for (String msg : receivedMessages) {
            JsonNode json = objectMapper.readTree(msg);
            if ("order_update".equals(json.get("type").asText())) {
                foundOrderUpdate = true;
                JsonNode orderData = json.get("msg");
                assertThat(orderData.get("market_ticker").asText()).isEqualTo("DUMMY_TEST");
                assertThat(orderData.get("side").asText()).isEqualTo("yes");
                assertThat(orderData.get("price").asLong()).isEqualTo(48L);
                assertThat(orderData.get("original_quantity").asLong()).isEqualTo(10L);
            } else if ("orderbook_snapshot".equals(json.get("type").asText())) {
                foundOrderbookUpdate = true;
                assertThat(json.has("msg")).isTrue();
                assertThat(json.get("msg").has("yes")).isTrue();
                assertThat(json.get("msg").has("no")).isTrue();
            }
        }
        
        assertThat(foundOrderUpdate).as("Should find order_update message").isTrue();
        assertThat(foundOrderbookUpdate).as("Should find orderbook_snapshot message").isTrue();

        // Cleanup
        session.close();
    }

    @Test
    public void testMultipleOrdersUpdateBothChannels() throws Exception {
        // Setup WebSocket connection
        StandardWebSocketClient client = new StandardWebSocketClient();
        CountDownLatch connectionLatch = new CountDownLatch(1);
        CountDownLatch ordersLatch = new CountDownLatch(3); // Expect 3 order updates
        CountDownLatch orderbookLatch = new CountDownLatch(3); // Expect 3 orderbook updates
        
        List<JsonNode> orderUpdates = new ArrayList<>();
        List<JsonNode> orderbookSnapshots = new ArrayList<>();

        WebSocketSession session = client.doHandshake(new WebSocketHandler() {
            @Override
            public void afterConnectionEstablished(WebSocketSession session) throws Exception {
                connectionLatch.countDown();
                
                // Subscribe to both channels
                String subscribeMessage = """
                    {
                        "cmd": "subscribe",
                        "id": 1,
                        "params": {
                            "channels": ["orderbook_snapshot", "orders"],
                            "market_tickers": ["DUMMY_TEST"]
                        }
                    }
                """;
                session.sendMessage(new TextMessage(subscribeMessage));
            }

            @Override
            public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) throws Exception {
                String payload = message.getPayload().toString();
                JsonNode json = objectMapper.readTree(payload);
                String type = json.get("type").asText();
                
                if ("order_update".equals(type)) {
                    orderUpdates.add(json);
                    ordersLatch.countDown();
                } else if ("orderbook_snapshot".equals(type)) {
                    orderbookSnapshots.add(json);
                    orderbookLatch.countDown();
                }
            }

            @Override
            public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
                System.err.println("WebSocket error: " + exception.getMessage());
            }

            @Override
            public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) throws Exception {
            }

            @Override
            public boolean supportsPartialMessages() {
                return false;
            }
        }, websocketUrl).get();

        // Wait for connection
        assertThat(connectionLatch.await(5, TimeUnit.SECONDS)).isTrue();
        Thread.sleep(500);

        // Create multiple orders
        for (int i = 0; i < 3; i++) {
            KalshiOrderRequest orderRequest = new KalshiOrderRequest();
            orderRequest.setAction("buy");
            orderRequest.setSide("yes");
            orderRequest.setType("limit");
            orderRequest.setPrice(45 + i);
            orderRequest.setCount(10);
            orderRequest.setTimeInForce("GTC");
            orderRequest.setMarketTicker("DUMMY_TEST");

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-User-Id", "TEST_USER_" + i);

            HttpEntity<KalshiOrderRequest> entity = new HttpEntity<>(orderRequest, headers);
            
            ResponseEntity<OrderResponse> response = restTemplate.exchange(
                baseUrl + "/trade-api/v2/portfolio/orders",
                HttpMethod.POST,
                entity,
                OrderResponse.class
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            Thread.sleep(100); // Small delay between orders
        }

        // Wait for all updates
        boolean allOrdersReceived = ordersLatch.await(5, TimeUnit.SECONDS);
        boolean allOrderbooksReceived = orderbookLatch.await(5, TimeUnit.SECONDS);

        assertThat(allOrdersReceived).as("Should receive all order updates").isTrue();
        assertThat(allOrderbooksReceived).as("Should receive all orderbook updates").isTrue();

        // Verify we got the right number of updates
        assertThat(orderUpdates.size()).isGreaterThanOrEqualTo(3);
        assertThat(orderbookSnapshots.size()).isGreaterThanOrEqualTo(3);

        // Cleanup
        session.close();
    }
}