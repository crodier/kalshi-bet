package com.kalshi.mock.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public class WebSocketDeltaTest {

    @LocalServerPort
    private int port;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();

    @Test
    public void testWebSocketDeltaUpdates() throws Exception {
        String wsUrl = "ws://localhost:" + port + "/trade-api/ws/v2";
        String restUrl = "http://localhost:" + port;

        // Counters for message types
        AtomicInteger snapshotCount = new AtomicInteger(0);
        AtomicInteger deltaCount = new AtomicInteger(0);
        AtomicInteger totalMessages = new AtomicInteger(0);
        
        // Store messages for analysis
        List<String> allMessages = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch connectedLatch = new CountDownLatch(1);
        CountDownLatch messagesLatch = new CountDownLatch(20); // Wait for 20 messages

        // Create WebSocket client
        WebSocketClient client = new WebSocketClient(new URI(wsUrl)) {
            @Override
            public void onOpen(ServerHandshake handshake) {
                System.out.println("WebSocket connected");
                connectedLatch.countDown();
            }

            @Override
            public void onMessage(String message) {
                try {
                    allMessages.add(message);
                    totalMessages.incrementAndGet();
                    
                    JsonNode json = objectMapper.readTree(message);
                    String type = json.get("type").asText();
                    
                    System.out.println("Received message #" + totalMessages.get() + 
                                     " - Type: " + type + 
                                     " - Full message: " + message);
                    
                    if ("orderbook_snapshot".equals(type)) {
                        snapshotCount.incrementAndGet();
                        System.out.println("  -> SNAPSHOT #" + snapshotCount.get());
                    } else if ("orderbook_delta".equals(type)) {
                        deltaCount.incrementAndGet();
                        JsonNode msg = json.get("msg");
                        System.out.println("  -> DELTA #" + deltaCount.get() + 
                                         " - Price: " + msg.get("price") + 
                                         ", Delta: " + msg.get("delta") + 
                                         ", Side: " + msg.get("side"));
                    }
                    
                    messagesLatch.countDown();
                } catch (Exception e) {
                    System.err.println("Error parsing message: " + e.getMessage());
                    e.printStackTrace();
                }
            }

            @Override
            public void onClose(int code, String reason, boolean remote) {
                System.out.println("WebSocket closed: " + reason);
            }

            @Override
            public void onError(Exception ex) {
                System.err.println("WebSocket error: " + ex.getMessage());
                ex.printStackTrace();
            }
        };

        // Connect
        client.connect();
        assertTrue(connectedLatch.await(10, TimeUnit.SECONDS), "Failed to connect");

        // Subscribe to MARKET_MAKER orderbook updates
        String subscribeMsg = "{\"id\":1,\"cmd\":\"subscribe\",\"params\":{" +
                             "\"channels\":[\"orderbook_snapshot\",\"orderbook_delta\"]," +
                             "\"market_tickers\":[\"MARKET_MAKER\"]}}";
        client.send(subscribeMsg);
        System.out.println("Sent subscription: " + subscribeMsg);

        // Wait a bit for initial snapshot
        Thread.sleep(2000);

        // Make REST calls to trigger order book changes
        System.out.println("\n=== Making REST calls to modify order book ===");
        
        // Place a buy order
        String buyOrderJson = """
            {
                "order_type": "limit",
                "side": "yes",
                "quantity": 10,
                "price": 49,
                "time_in_force": "GTC"
            }
            """;
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-User-Id", "TEST-USER-1");
        
        HttpEntity<String> buyRequest = new HttpEntity<>(buyOrderJson, headers);
        ResponseEntity<String> buyResponse = restTemplate.exchange(
            restUrl + "/trade-api/v2/markets/MARKET_MAKER/orders",
            HttpMethod.POST,
            buyRequest,
            String.class
        );
        System.out.println("Buy order response: " + buyResponse.getStatusCode());
        
        // Wait for updates
        Thread.sleep(1000);
        
        // Place a sell order
        String sellOrderJson = """
            {
                "order_type": "limit",
                "side": "yes", 
                "quantity": 15,
                "price": 51,
                "time_in_force": "GTC"
            }
            """;
        
        HttpEntity<String> sellRequest = new HttpEntity<>(sellOrderJson, headers);
        ResponseEntity<String> sellResponse = restTemplate.exchange(
            restUrl + "/trade-api/v2/markets/MARKET_MAKER/orders",
            HttpMethod.POST,
            sellRequest,
            String.class
        );
        System.out.println("Sell order response: " + sellResponse.getStatusCode());
        
        // Wait for updates
        Thread.sleep(1000);
        
        // Cancel the buy order
        JsonNode buyOrderData = objectMapper.readTree(buyResponse.getBody());
        String orderId = buyOrderData.get("order").get("id").asText();
        
        ResponseEntity<String> cancelResponse = restTemplate.exchange(
            restUrl + "/trade-api/v2/orders/" + orderId,
            HttpMethod.DELETE,
            new HttpEntity<>(headers),
            String.class
        );
        System.out.println("Cancel order response: " + cancelResponse.getStatusCode());
        
        // Wait for more messages
        boolean gotAllMessages = messagesLatch.await(30, TimeUnit.SECONDS);
        
        // Analysis
        System.out.println("\n=== WebSocket Message Analysis ===");
        System.out.println("Total messages received: " + totalMessages.get());
        System.out.println("Snapshots: " + snapshotCount.get());
        System.out.println("Deltas: " + deltaCount.get());
        
        // Print message sequence
        System.out.println("\nMessage sequence:");
        for (int i = 0; i < Math.min(allMessages.size(), 20); i++) {
            JsonNode msg = objectMapper.readTree(allMessages.get(i));
            System.out.println((i + 1) + ". " + msg.get("type").asText());
        }
        
        // Assertions
        assertTrue(totalMessages.get() > 0, "Should receive at least one message");
        assertTrue(snapshotCount.get() > 0, "Should receive at least one snapshot");
        
        // Check for delta pattern
        if (totalMessages.get() >= 10) {
            // Should have received some deltas between snapshots
            assertTrue(deltaCount.get() > 0, 
                "Should receive delta updates when order book changes");
            
            // Verify snapshot interval (every 10th update)
            int expectedSnapshots = totalMessages.get() / 10 + 1; // +1 for initial
            assertTrue(snapshotCount.get() <= expectedSnapshots + 1, 
                "Too many snapshots - should be every 10th update");
        }
        
        // Close connection
        client.close();
    }
}