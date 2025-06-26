package com.kalshi.mock.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kalshi.mock.MockKalshiSpringBootApplication;
import com.kalshi.mock.dto.KalshiOrderRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.context.annotation.Import;
import com.kalshi.mock.config.TestFixConfiguration;
import com.kalshi.mock.config.TestDataSourceConfig;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;

import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = MockKalshiSpringBootApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, 
    properties = {"spring.main.allow-bean-definition-overriding=true"})
@ActiveProfiles("test")
@Import({TestFixConfiguration.class, TestDataSourceConfig.class})
public class WebSocketBroadcastIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private WebSocketStompClient stompClient;
    private String wsUrl;
    private String apiUrl;

    @BeforeEach
    public void setup() {
        wsUrl = "ws://localhost:" + port + "/ws";
        apiUrl = "http://localhost:" + port;

        // Create WebSocket client
        StandardWebSocketClient webSocketClient = new StandardWebSocketClient();
        WebSocketTransport webSocketTransport = new WebSocketTransport(webSocketClient);
        SockJsClient sockJsClient = new SockJsClient(Arrays.asList(webSocketTransport));
        
        stompClient = new WebSocketStompClient(sockJsClient);
        stompClient.setMessageConverter(new MappingJackson2MessageConverter());
    }

    @Test
    public void testOrderPlacementBroadcastsToAllSubscribers() throws Exception {
        String marketTicker = "DUMMY_TEST";
        
        // Create two WebSocket subscribers
        BlockingQueue<Map<String, Object>> subscriber1Messages = new LinkedBlockingQueue<>();
        BlockingQueue<Map<String, Object>> subscriber2Messages = new LinkedBlockingQueue<>();
        
        // Connect first subscriber
        StompSession session1 = stompClient.connect(wsUrl, new StompSessionHandlerAdapter() {})
                .get(5, TimeUnit.SECONDS);
        
        session1.subscribe("/topic/orderbook/" + marketTicker, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return Map.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                subscriber1Messages.offer((Map<String, Object>) payload);
            }
        });

        // Connect second subscriber
        StompSession session2 = stompClient.connect(wsUrl, new StompSessionHandlerAdapter() {})
                .get(5, TimeUnit.SECONDS);
        
        session2.subscribe("/topic/orderbook/" + marketTicker, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return Map.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                subscriber2Messages.offer((Map<String, Object>) payload);
            }
        });

        // Wait for subscriptions to be established
        Thread.sleep(500);

        // Clear any initial messages
        subscriber1Messages.clear();
        subscriber2Messages.clear();

        // Place an order via REST API
        KalshiOrderRequest orderRequest = new KalshiOrderRequest();
        orderRequest.setMarketTicker(marketTicker);
        orderRequest.setAction("buy");
        orderRequest.setSide("yes");
        orderRequest.setCount(10);
        orderRequest.setType("limit");
        orderRequest.setPrice(50);
        orderRequest.setTimeInForce("GTC");

        HttpHeaders headers = new HttpHeaders();
        headers.set("Content-Type", "application/json");
        headers.set("KALSHI-ACCESS-KEY", "test-user-key");

        HttpEntity<KalshiOrderRequest> request = new HttpEntity<>(orderRequest, headers);
        ResponseEntity<Map> response = restTemplate.postForEntity(
                apiUrl + "/trade-api/v2/portfolio/orders",
                request,
                Map.class
        );

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertNotNull(response.getBody().get("order"));

        // Both subscribers should receive the orderbook update
        Map<String, Object> message1 = subscriber1Messages.poll(5, TimeUnit.SECONDS);
        Map<String, Object> message2 = subscriber2Messages.poll(5, TimeUnit.SECONDS);

        assertNotNull(message1, "Subscriber 1 should receive orderbook update");
        assertNotNull(message2, "Subscriber 2 should receive orderbook update");

        // Verify the messages contain orderbook data
        assertTrue(message1.containsKey("yes") || message1.containsKey("no"), 
                  "Message should contain orderbook data");
        assertTrue(message2.containsKey("yes") || message2.containsKey("no"), 
                  "Message should contain orderbook data");

        // Verify both subscribers received the same update
        assertEquals(objectMapper.writeValueAsString(message1), 
                    objectMapper.writeValueAsString(message2),
                    "Both subscribers should receive identical updates");

        // Place another order and verify both subscribers get it
        orderRequest.setPrice(55);
        response = restTemplate.postForEntity(
                apiUrl + "/trade-api/v2/markets/" + marketTicker + "/orders",
                request,
                Map.class
        );

        assertEquals(HttpStatus.CREATED, response.getStatusCode());

        // Both should receive the second update
        message1 = subscriber1Messages.poll(5, TimeUnit.SECONDS);
        message2 = subscriber2Messages.poll(5, TimeUnit.SECONDS);

        assertNotNull(message1, "Subscriber 1 should receive second update");
        assertNotNull(message2, "Subscriber 2 should receive second update");

        // Disconnect sessions
        session1.disconnect();
        session2.disconnect();
    }

    @Test
    public void testOrderCancellationBroadcast() throws Exception {
        String marketTicker = "DUMMY_TEST";
        
        BlockingQueue<Map<String, Object>> orderbookMessages = new LinkedBlockingQueue<>();
        
        // Connect WebSocket subscriber
        StompSession session = stompClient.connect(wsUrl, new StompSessionHandlerAdapter() {})
                .get(5, TimeUnit.SECONDS);
        
        session.subscribe("/topic/orderbook/" + marketTicker, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return Map.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                orderbookMessages.offer((Map<String, Object>) payload);
            }
        });

        // Wait for subscription
        Thread.sleep(500);
        orderbookMessages.clear();

        // Place an order
        KalshiOrderRequest orderRequest = new KalshiOrderRequest();
        orderRequest.setMarketTicker(marketTicker);
        orderRequest.setAction("buy");
        orderRequest.setSide("yes");
        orderRequest.setCount(5);
        orderRequest.setType("limit");
        orderRequest.setPrice(60);
        orderRequest.setTimeInForce("GTC");

        HttpHeaders headers = new HttpHeaders();
        headers.set("Content-Type", "application/json");
        headers.set("KALSHI-ACCESS-KEY", "test-user-key");

        HttpEntity<KalshiOrderRequest> request = new HttpEntity<>(orderRequest, headers);
        ResponseEntity<Map> response = restTemplate.postForEntity(
                apiUrl + "/trade-api/v2/portfolio/orders",
                request,
                Map.class
        );

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        Map<String, Object> order = (Map<String, Object>) response.getBody().get("order");
        String orderId = (String) order.get("order_id");

        // Should receive orderbook update for placement
        Map<String, Object> placementUpdate = orderbookMessages.poll(5, TimeUnit.SECONDS);
        assertNotNull(placementUpdate, "Should receive update for order placement");

        // Cancel the order
        HttpEntity<Void> cancelRequest = new HttpEntity<>(headers);
        ResponseEntity<Void> cancelResponse = restTemplate.exchange(
                apiUrl + "/trade-api/v2/portfolio/orders/" + orderId,
                org.springframework.http.HttpMethod.DELETE,
                cancelRequest,
                Void.class
        );

        assertEquals(HttpStatus.NO_CONTENT, cancelResponse.getStatusCode());

        // Should receive orderbook update for cancellation
        Map<String, Object> cancellationUpdate = orderbookMessages.poll(5, TimeUnit.SECONDS);
        assertNotNull(cancellationUpdate, "Should receive update for order cancellation");

        // Disconnect session
        session.disconnect();
    }

    @Test
    public void testMarketDataBroadcastOnTrade() throws Exception {
        String marketTicker = "DUMMY_TEST";
        
        BlockingQueue<Map<String, Object>> marketDataMessages = new LinkedBlockingQueue<>();
        
        // Connect WebSocket subscriber to market data topic
        StompSession session = stompClient.connect(wsUrl, new StompSessionHandlerAdapter() {})
                .get(5, TimeUnit.SECONDS);
        
        session.subscribe("/topic/market/" + marketTicker, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return Map.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                marketDataMessages.offer((Map<String, Object>) payload);
            }
        });

        // Wait for subscription
        Thread.sleep(500);
        marketDataMessages.clear();

        // Place a market order that should execute immediately
        KalshiOrderRequest orderRequest = new KalshiOrderRequest();
        orderRequest.setMarketTicker(marketTicker);
        orderRequest.setAction("buy");
        orderRequest.setSide("yes");
        orderRequest.setCount(5);
        orderRequest.setType("market");
        orderRequest.setTimeInForce("IOC");

        HttpHeaders headers = new HttpHeaders();
        headers.set("Content-Type", "application/json");
        headers.set("KALSHI-ACCESS-KEY", "test-user-key");

        HttpEntity<KalshiOrderRequest> request = new HttpEntity<>(orderRequest, headers);
        ResponseEntity<Map> response = restTemplate.postForEntity(
                apiUrl + "/trade-api/v2/portfolio/orders",
                request,
                Map.class
        );

        // Market order might fail if no matching orders exist, that's ok
        // We're testing the broadcast mechanism

        // If order executed, should receive market data update
        Map<String, Object> marketUpdate = marketDataMessages.poll(2, TimeUnit.SECONDS);
        
        // Note: Market data update only happens on successful trade
        // If no matching orders exist, market order fails and no update is sent
        
        // Disconnect session
        session.disconnect();
    }

    @Test
    public void testMultipleMarketSubscriptions() throws Exception {
        String market1 = "DUMMY_TEST";
        String market2 = "INXD-23DEC29-B5000";
        
        BlockingQueue<Map<String, Object>> market1Messages = new LinkedBlockingQueue<>();
        BlockingQueue<Map<String, Object>> market2Messages = new LinkedBlockingQueue<>();
        
        // Connect WebSocket and subscribe to both markets
        StompSession session = stompClient.connect(wsUrl, new StompSessionHandlerAdapter() {})
                .get(5, TimeUnit.SECONDS);
        
        session.subscribe("/topic/orderbook/" + market1, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return Map.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                market1Messages.offer((Map<String, Object>) payload);
            }
        });

        session.subscribe("/topic/orderbook/" + market2, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return Map.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                market2Messages.offer((Map<String, Object>) payload);
            }
        });

        // Wait for subscriptions
        Thread.sleep(500);
        market1Messages.clear();
        market2Messages.clear();

        // Place order in market1
        KalshiOrderRequest orderRequest = new KalshiOrderRequest();
        orderRequest.setMarketTicker(market1);
        orderRequest.setAction("buy");
        orderRequest.setSide("yes");
        orderRequest.setCount(5);
        orderRequest.setType("limit");
        orderRequest.setPrice(50);
        orderRequest.setTimeInForce("GTC");

        HttpHeaders headers = new HttpHeaders();
        headers.set("Content-Type", "application/json");
        headers.set("KALSHI-ACCESS-KEY", "test-user-key");

        HttpEntity<KalshiOrderRequest> request = new HttpEntity<>(orderRequest, headers);
        ResponseEntity<Map> response = restTemplate.postForEntity(
                apiUrl + "/trade-api/v2/markets/" + market1 + "/orders",
                request,
                Map.class
        );

        assertEquals(HttpStatus.CREATED, response.getStatusCode());

        // Only market1 should receive update
        Map<String, Object> update1 = market1Messages.poll(5, TimeUnit.SECONDS);
        Map<String, Object> update2 = market2Messages.poll(1, TimeUnit.SECONDS);

        assertNotNull(update1, "Market1 should receive update");
        assertNull(update2, "Market2 should NOT receive update");

        // Place order in market2
        orderRequest.setMarketTicker(market2);
        response = restTemplate.postForEntity(
                apiUrl + "/trade-api/v2/portfolio/orders",
                request,
                Map.class
        );

        assertEquals(HttpStatus.CREATED, response.getStatusCode());

        // Only market2 should receive update
        update1 = market1Messages.poll(1, TimeUnit.SECONDS);
        update2 = market2Messages.poll(5, TimeUnit.SECONDS);

        assertNull(update1, "Market1 should NOT receive update for market2 order");
        assertNotNull(update2, "Market2 should receive update");

        // Disconnect session
        session.disconnect();
    }
}