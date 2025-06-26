package com.kalshi.mock.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class StandaloneWebSocketTest {

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final RestTemplate restTemplate = new RestTemplate();

    public static void main(String[] args) throws Exception {
        // Assuming the server is running on default ports
        String wsUrl = "ws://localhost:9090/trade-api/ws/v2";
        String restUrl = "http://localhost:9090";

        // Counters for message types
        AtomicInteger snapshotCount = new AtomicInteger(0);
        AtomicInteger deltaCount = new AtomicInteger(0);
        AtomicInteger totalMessages = new AtomicInteger(0);
        
        // Store messages for analysis
        List<String> allMessages = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch connectedLatch = new CountDownLatch(1);

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
                    int msgNum = totalMessages.incrementAndGet();
                    
                    JsonNode json = objectMapper.readTree(message);
                    String type = json.path("type").asText();
                    
                    if ("orderbook_snapshot".equals(type)) {
                        int snapNum = snapshotCount.incrementAndGet();
                        System.out.println(String.format("[MSG #%d] SNAPSHOT #%d", msgNum, snapNum));
                        
                        JsonNode msg = json.get("msg");
                        if (msg != null) {
                            JsonNode yes = msg.get("yes");
                            JsonNode no = msg.get("no");
                            System.out.println("  YES levels: " + (yes != null ? yes.size() : 0) + 
                                             ", NO levels: " + (no != null ? no.size() : 0));
                        }
                    } else if ("orderbook_delta".equals(type)) {
                        int deltaNum = deltaCount.incrementAndGet();
                        JsonNode msg = json.get("msg");
                        System.out.println(String.format("[MSG #%d] DELTA #%d - Price: %s, Delta: %s, Side: %s", 
                                         msgNum, deltaNum,
                                         msg.get("price"), 
                                         msg.get("delta"), 
                                         msg.get("side")));
                    } else if ("subscription".equals(type)) {
                        System.out.println("[MSG #" + msgNum + "] Subscription confirmed: " + message);
                    } else {
                        System.out.println("[MSG #" + msgNum + "] Other: " + type + " - " + message);
                    }
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
        System.out.println("Connecting to WebSocket...");
        client.connect();
        if (!connectedLatch.await(10, TimeUnit.SECONDS)) {
            System.err.println("Failed to connect to WebSocket");
            return;
        }

        // Subscribe to MARKET_MAKER orderbook updates
        String subscribeMsg = "{\"id\":1,\"cmd\":\"subscribe\",\"params\":{" +
                             "\"channels\":[\"orderbook_snapshot\",\"orderbook_delta\"]," +
                             "\"market_tickers\":[\"MARKET_MAKER\"]}}";
        client.send(subscribeMsg);
        System.out.println("Sent subscription: " + subscribeMsg);

        // Wait for initial messages
        Thread.sleep(2000);

        // Make REST calls to trigger order book changes
        System.out.println("\n=== Making REST calls to modify order book ===");
        
        // Place multiple orders to trigger deltas
        for (int i = 0; i < 5; i++) {
            int price = 46 + i;
            String orderJson = String.format("""
                {
                    "type": "limit",
                    "side": "yes",
                    "action": "buy",
                    "count": %d,
                    "price": %d,
                    "time_in_force": "GTC",
                    "market_ticker": "MARKET_MAKER"
                }
                """, 10 + i * 5, price);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-User-Id", "TEST-USER-" + i);
            
            HttpEntity<String> request = new HttpEntity<>(orderJson, headers);
            
            try {
                ResponseEntity<String> response = restTemplate.exchange(
                    restUrl + "/trade-api/v2/portfolio/orders",
                    HttpMethod.POST,
                    request,
                    String.class
                );
                System.out.println("Order " + (i + 1) + " placed at price " + price + 
                                 " - Status: " + response.getStatusCode());
            } catch (Exception e) {
                System.err.println("Failed to place order: " + e.getMessage());
            }
            
            // Wait between orders to see individual updates
            Thread.sleep(1000);
        }
        
        // Wait for more updates
        Thread.sleep(5000);
        
        // Cancel some orders
        System.out.println("\n=== Canceling orders ===");
        
        // Get user orders
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-User-Id", "TEST-USER-0");
        
        try {
            ResponseEntity<String> ordersResponse = restTemplate.exchange(
                restUrl + "/trade-api/v2/portfolio/orders",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class
            );
            
            JsonNode orders = objectMapper.readTree(ordersResponse.getBody());
            JsonNode orderList = orders.get("orders");
            
            if (orderList != null && orderList.isArray() && orderList.size() > 0) {
                String orderId = orderList.get(0).get("id").asText();
                
                ResponseEntity<String> cancelResponse = restTemplate.exchange(
                    restUrl + "/trade-api/v2/portfolio/orders/" + orderId,
                    HttpMethod.DELETE,
                    new HttpEntity<>(headers),
                    String.class
                );
                System.out.println("Canceled order " + orderId + " - Status: " + cancelResponse.getStatusCode());
            }
        } catch (Exception e) {
            System.err.println("Failed to cancel order: " + e.getMessage());
        }
        
        // Wait for final updates
        Thread.sleep(5000);
        
        // Analysis
        System.out.println("\n=== WebSocket Message Analysis ===");
        System.out.println("Total messages received: " + totalMessages.get());
        System.out.println("Snapshots: " + snapshotCount.get());
        System.out.println("Deltas: " + deltaCount.get());
        
        // Check message pattern
        System.out.println("\nMessage sequence (first 30):");
        for (int i = 0; i < Math.min(allMessages.size(), 30); i++) {
            JsonNode msg = objectMapper.readTree(allMessages.get(i));
            String type = msg.path("type").asText();
            if ("orderbook_snapshot".equals(type) || "orderbook_delta".equals(type)) {
                System.out.println((i + 1) + ". " + type);
            }
        }
        
        // Calculate ratios
        if (totalMessages.get() > 0) {
            double deltaRatio = (double) deltaCount.get() / totalMessages.get() * 100;
            double snapshotRatio = (double) snapshotCount.get() / totalMessages.get() * 100;
            System.out.println("\nDelta ratio: " + String.format("%.1f%%", deltaRatio));
            System.out.println("Snapshot ratio: " + String.format("%.1f%%", snapshotRatio));
            
            if (deltaCount.get() == 0) {
                System.out.println("\n⚠️  WARNING: No delta updates received!");
                System.out.println("This suggests the backend is not properly calculating or sending deltas.");
            }
        }
        
        // Close connection
        client.close();
        System.out.println("\nTest completed");
    }
}