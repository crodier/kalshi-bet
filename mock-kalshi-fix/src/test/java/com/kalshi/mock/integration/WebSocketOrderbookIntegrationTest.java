package com.kalshi.mock.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.TestPropertySource;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:postgresql://localhost:5432/kalshi_test",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "quickfix.enabled=false"
})
public class WebSocketOrderbookIntegrationTest {
    
    @LocalServerPort
    private int port;
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newHttpClient();
    
    @Test
    public void testOrderbookViaWebSocket() throws Exception {
        // Use the pre-created DUMMY_TEST market
        String marketTicker = "DUMMY_TEST";
        System.out.println("Testing with market: " + marketTicker);
        
        // Note: DUMMY_TEST market is created automatically in OrderBookService.initializeTestMarkets()
        
        // Step 2: Create orders via REST API
        System.out.println("\nCreating orders...");
        
        // YES side asks (sell orders)
        createOrder(marketTicker, "sell", "yes", 65, 100, "test-yes-ask-65");
        createOrder(marketTicker, "sell", "yes", 64, 150, "test-yes-ask-64");
        
        // YES side bids (buy orders)
        createOrder(marketTicker, "buy", "yes", 60, 200, "test-yes-bid-60");
        createOrder(marketTicker, "buy", "yes", 58, 250, "test-yes-bid-58");
        
        // NO side asks (sell orders)
        createOrder(marketTicker, "sell", "no", 40, 300, "test-no-ask-40");
        createOrder(marketTicker, "sell", "no", 38, 350, "test-no-ask-38");
        
        // NO side bids (buy orders)
        createOrder(marketTicker, "buy", "no", 35, 400, "test-no-bid-35");
        createOrder(marketTicker, "buy", "no", 33, 450, "test-no-bid-33");
        
        Thread.sleep(1000); // Give server time to process all orders
        
        // Step 3: Connect WebSocket and get orderbook snapshot
        System.out.println("\nConnecting to WebSocket...");
        CountDownLatch connectLatch = new CountDownLatch(1);
        CountDownLatch messageLatch = new CountDownLatch(1);
        AtomicReference<Map<String, Object>> orderbookSnapshot = new AtomicReference<>();
        
        URI wsUri = new URI("ws://localhost:" + port + "/trade-api/ws/v2");
        WebSocketClient wsClient = new WebSocketClient(wsUri) {
            @Override
            public void onOpen(ServerHandshake handshake) {
                System.out.println("WebSocket connected");
                connectLatch.countDown();
                
                // Subscribe to orderbook snapshot
                try {
                    Map<String, Object> subscribeCmd = new HashMap<>();
                    subscribeCmd.put("id", 1);
                    subscribeCmd.put("cmd", "subscribe");
                    
                    Map<String, Object> params = new HashMap<>();
                    params.put("channels", Arrays.asList("orderbook_snapshot"));
                    params.put("market_tickers", Arrays.asList(marketTicker));
                    subscribeCmd.put("params", params);
                    
                    String json = objectMapper.writeValueAsString(subscribeCmd);
                    System.out.println("Sending subscribe: " + json);
                    send(json);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            
            @Override
            public void onMessage(String message) {
                System.out.println("Received message: " + message);
                try {
                    Map<String, Object> msg = objectMapper.readValue(message, Map.class);
                    
                    if ("orderbook_snapshot".equals(msg.get("type"))) {
                        orderbookSnapshot.set((Map<String, Object>) msg.get("msg"));
                        messageLatch.countDown();
                    }
                } catch (Exception e) {
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
            }
        };
        
        wsClient.connect();
        assertTrue(connectLatch.await(5, TimeUnit.SECONDS), "Failed to connect to WebSocket");
        assertTrue(messageLatch.await(5, TimeUnit.SECONDS), "Failed to receive orderbook snapshot");
        
        // Step 4: Verify the orderbook
        Map<String, Object> orderbook = orderbookSnapshot.get();
        assertNotNull(orderbook, "Orderbook snapshot should not be null");
        assertEquals(marketTicker, orderbook.get("market_ticker"));
        
        List<List<Integer>> yesBook = (List<List<Integer>>) orderbook.get("yes");
        List<List<Integer>> noBook = (List<List<Integer>>) orderbook.get("no");
        
        System.out.println("\n=== ORDERBOOK SNAPSHOT ===");
        System.out.println("Market: " + marketTicker);
        System.out.println("\nYES orderbook (all orders normalized to YES):");
        printOrderbookSide(yesBook);
        
        // The orderbook should only contain YES side with NO orders converted
        // NO buy orders are converted to YES sell orders
        // NO sell orders are converted to YES buy orders
        
        /*
         * Expected orderbook after conversion and aggregation:
         * Price levels will be aggregated where they overlap:
         * 
         * BIDS (sorted high to low):
         * - 67: 450 (from NO sell @ 33)
         * - 65: 400 (from NO sell @ 35) 
         * - 60: 500 (200 original YES + 300 from NO sell @ 40)
         * - 58: 250 (original YES)
         * 
         * ASKS (sorted low to high):
         * - 62: 350 (from NO buy @ 38)
         * - 64: 150 (original YES)
         * - 65: 100 (original YES)
         * - 60: 300 (from NO buy @ 40) - Wait, this should be aggregated!
         * 
         * Actually, after proper aggregation at same price levels:
         * - 58: 250 (YES buy)
         * - 60: 500 (200 YES buy + 300 from NO sell @ 40) 
         * - 62: 350 (from NO buy @ 38 -> YES sell)
         * - 64: 150 (YES sell)
         * - 65: 500 (100 YES sell + 400 from NO sell @ 35 -> YES buy)
         * - 67: 450 (from NO sell @ 33 -> YES buy)
         */
        
        assertEquals(6, yesBook.size(), "YES orderbook should have 6 price levels after NO conversion and aggregation");
        
        // Verify the orderbook contains expected prices
        Set<Integer> prices = new HashSet<>();
        for (List<Integer> level : yesBook) {
            prices.add(level.get(0));
        }
        
        // Check for expected price levels after aggregation
        assertTrue(prices.contains(67), "Should have price level at 67 (from NO sell @ 33)");
        assertTrue(prices.contains(65), "Should have price level at 65 (aggregated)");
        assertTrue(prices.contains(64), "Should have price level at 64 (YES sell)");
        assertTrue(prices.contains(62), "Should have price level at 62 (from NO buy @ 38)");
        assertTrue(prices.contains(60), "Should have price level at 60 (aggregated)");
        assertTrue(prices.contains(58), "Should have price level at 58 (YES buy)");
        
        // Verify specific quantities at key levels
        for (List<Integer> level : yesBook) {
            if (level.get(0) == 60) {
                assertEquals(500, level.get(1), "Price 60 should have aggregated quantity of 500");
            } else if (level.get(0) == 65) {
                assertEquals(500, level.get(1), "Price 65 should have aggregated quantity of 500");
            }
        }
        
        // NO side should be empty as all orders are converted to YES
        assertTrue(noBook == null || noBook.isEmpty(), "NO orderbook should be empty");
        
        // Step 5: Test that REST API updates trigger WebSocket updates
        System.out.println("\n=== Testing REST update triggers WebSocket update ===");
        
        // Reset latch for next message
        CountDownLatch updateLatch = new CountDownLatch(1);
        AtomicReference<Map<String, Object>> orderbookUpdate = new AtomicReference<>();
        
        // Modify the client to capture updates
        wsClient = new WebSocketClient(wsUri) {
            @Override
            public void onOpen(ServerHandshake handshake) {
                // Already connected
            }
            
            @Override
            public void onMessage(String message) {
                System.out.println("Update received: " + message);
                try {
                    Map<String, Object> msg = objectMapper.readValue(message, Map.class);
                    
                    if ("orderbook_snapshot".equals(msg.get("type"))) {
                        // Check if this is a new snapshot (seq > 1)
                        Integer seq = (Integer) msg.get("seq");
                        if (seq != null && seq > 1) {
                            orderbookUpdate.set((Map<String, Object>) msg.get("msg"));
                            updateLatch.countDown();
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            
            @Override
            public void onClose(int code, String reason, boolean remote) {
                System.out.println("Connection closed: " + reason);
            }
            
            @Override
            public void onError(Exception ex) {
                System.err.println("WebSocket error: " + ex.getMessage());
            }
        };
        
        // Reconnect with the new handler
        wsClient.close();
        Thread.sleep(500);
        wsClient.connect();
        Thread.sleep(1000);
        
        // Subscribe again
        Map<String, Object> subscribeCmd = new HashMap<>();
        subscribeCmd.put("id", 2);
        subscribeCmd.put("cmd", "subscribe");
        
        Map<String, Object> params = new HashMap<>();
        params.put("channels", Arrays.asList("orderbook_snapshot"));
        params.put("market_tickers", Arrays.asList(marketTicker));
        subscribeCmd.put("params", params);
        
        wsClient.send(objectMapper.writeValueAsString(subscribeCmd));
        Thread.sleep(1000); // Wait for subscription
        
        // Create a new order via REST that should trigger an update
        System.out.println("\nCreating new order to trigger update...");
        createOrder(marketTicker, "buy", "yes", 61, 100, "test-update-trigger");
        
        // Wait for WebSocket update
        assertTrue(updateLatch.await(5, TimeUnit.SECONDS), "Failed to receive orderbook update after REST order");
        
        // Verify the update contains the new order
        Map<String, Object> updatedOrderbook = orderbookUpdate.get();
        assertNotNull(updatedOrderbook, "Updated orderbook should not be null");
        
        List<List<Integer>> updatedYesBook = (List<List<Integer>>) updatedOrderbook.get("yes");
        System.out.println("\n=== UPDATED ORDERBOOK ===");
        printOrderbookSide(updatedYesBook);
        
        // Check that price 61 now exists
        boolean found61 = false;
        for (List<Integer> level : updatedYesBook) {
            if (level.get(0) == 61) {
                found61 = true;
                assertEquals(100, level.get(1), "New order at price 61 should have quantity 100");
                break;
            }
        }
        assertTrue(found61, "Updated orderbook should contain new order at price 61");
        
        // Step 6: Re-subscribe and verify we get the updated snapshot
        System.out.println("\n=== Testing re-subscription gets current snapshot ===");
        
        // Close current connection
        wsClient.close();
        Thread.sleep(500);
        
        // Create new client and connect
        CountDownLatch resubscribeLatch = new CountDownLatch(1);
        AtomicReference<Map<String, Object>> resubscribeSnapshot = new AtomicReference<>();
        
        WebSocketClient resubscribeClient = new WebSocketClient(wsUri) {
            @Override
            public void onOpen(ServerHandshake handshake) {
                System.out.println("Re-subscription client connected");
                
                // Subscribe to orderbook snapshot
                try {
                    Map<String, Object> subscribeCmd = new HashMap<>();
                    subscribeCmd.put("id", 3);
                    subscribeCmd.put("cmd", "subscribe");
                    
                    Map<String, Object> params = new HashMap<>();
                    params.put("channels", Arrays.asList("orderbook_snapshot"));
                    params.put("market_tickers", Arrays.asList(marketTicker));
                    subscribeCmd.put("params", params);
                    
                    String json = objectMapper.writeValueAsString(subscribeCmd);
                    System.out.println("Sending re-subscribe: " + json);
                    send(json);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            
            @Override
            public void onMessage(String message) {
                System.out.println("Re-subscribe received: " + message);
                try {
                    Map<String, Object> msg = objectMapper.readValue(message, Map.class);
                    
                    if ("orderbook_snapshot".equals(msg.get("type"))) {
                        resubscribeSnapshot.set((Map<String, Object>) msg.get("msg"));
                        resubscribeLatch.countDown();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            
            @Override
            public void onClose(int code, String reason, boolean remote) {
                System.out.println("Re-subscribe client closed: " + reason);
            }
            
            @Override
            public void onError(Exception ex) {
                System.err.println("Re-subscribe error: " + ex.getMessage());
            }
        };
        
        resubscribeClient.connect();
        assertTrue(resubscribeLatch.await(5, TimeUnit.SECONDS), "Failed to receive snapshot on re-subscribe");
        
        // Verify the re-subscribe snapshot contains all our changes
        Map<String, Object> finalSnapshot = resubscribeSnapshot.get();
        assertNotNull(finalSnapshot, "Re-subscribe snapshot should not be null");
        assertEquals(marketTicker, finalSnapshot.get("market_ticker"));
        
        List<List<Integer>> finalYesBook = (List<List<Integer>>) finalSnapshot.get("yes");
        System.out.println("\n=== FINAL SNAPSHOT AFTER RE-SUBSCRIBE ===");
        printOrderbookSide(finalYesBook);
        
        // Should now have 7 price levels (original 6 + new one at 61)
        assertEquals(7, finalYesBook.size(), "Final snapshot should have 7 price levels");
        
        // Verify all expected prices are present
        Set<Integer> finalPrices = new HashSet<>();
        for (List<Integer> level : finalYesBook) {
            finalPrices.add(level.get(0));
        }
        
        assertTrue(finalPrices.contains(67), "Should still have price 67");
        assertTrue(finalPrices.contains(65), "Should still have price 65");
        assertTrue(finalPrices.contains(64), "Should still have price 64");
        assertTrue(finalPrices.contains(62), "Should still have price 62");
        assertTrue(finalPrices.contains(61), "Should have new price 61");
        assertTrue(finalPrices.contains(60), "Should still have price 60");
        assertTrue(finalPrices.contains(58), "Should still have price 58");
        
        // Clean up
        resubscribeClient.close();
        
        System.out.println("\n✓ Integration test with updates and re-subscription passed!");
    }
    
    private void createOrder(String marketTicker, String action, String side, int price, int count, String clientOrderId) throws Exception {
        Map<String, Object> orderRequest = new HashMap<>();
        orderRequest.put("action", action);
        orderRequest.put("side", side);
        orderRequest.put("market_ticker", marketTicker);
        orderRequest.put("type", "limit");
        orderRequest.put("price", price);
        orderRequest.put("count", count);
        orderRequest.put("client_order_id", clientOrderId);
        
        String json = objectMapper.writeValueAsString(orderRequest);
        
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + port + "/trade-api/v2/portfolio/orders"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json))
            .build();
        
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        System.out.println("Created order: " + action + " " + count + " " + side + " @ " + price + " - Status: " + response.statusCode());
    }
    
    private void printOrderbookSide(List<List<Integer>> side) {
        for (List<Integer> level : side) {
            System.out.println("  Price: " + level.get(0) + ", Quantity: " + level.get(1));
        }
    }
}