package com.kalshi.mock.websocket.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;

public class WebSocketOrderTest {
    
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static boolean connected = false;
    
    public static void main(String[] args) throws Exception {
        URI serverUri = new URI("ws://localhost:9090/trade-api/ws/v2");
        
        WebSocketClient client = new WebSocketClient(serverUri) {
            @Override
            public void onOpen(ServerHandshake handshake) {
                System.out.println("Connected to WebSocket server");
                connected = true;
                
                // Send subscribe command
                try {
                    Map<String, Object> command = new HashMap<>();
                    command.put("id", 1);
                    command.put("cmd", "subscribe");
                    
                    Map<String, Object> params = new HashMap<>();
                    params.put("channels", Arrays.asList("orderbook_snapshot", "orderbook_delta", "ticker", "trade"));
                    params.put("market_tickers", Arrays.asList("INXD-23DEC29-B5000"));
                    command.put("params", params);
                    
                    String json = objectMapper.writeValueAsString(command);
                    System.out.println("Sending subscribe: " + json);
                    send(json);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            
            @Override
            public void onMessage(String message) {
                System.out.println("\n=== RECEIVED MESSAGE ===");
                try {
                    Object json = objectMapper.readValue(message, Object.class);
                    String prettyJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(json);
                    System.out.println(prettyJson);
                } catch (Exception e) {
                    System.err.println("Failed to parse JSON: " + e.getMessage());
                }
                System.out.println("========================\n");
            }
            
            @Override
            public void onClose(int code, String reason, boolean remote) {
                System.out.println("Connection closed: " + reason);
            }
            
            @Override
            public void onError(Exception ex) {
                System.err.println("WebSocket error: " + ex.getMessage());
                ex.printStackTrace();
            }
        };
        
        System.out.println("Connecting to " + serverUri + "...");
        client.connect();
        
        // Wait for connection
        Thread.sleep(2000);
        
        if (connected) {
            System.out.println("\nCreating orders to trigger events...\n");
            
            // Create a few orders
            createOrder("buy", "yes", 45, 10, "test-ws-1");
            Thread.sleep(1000);
            
            createOrder("sell", "no", 60, 5, "test-ws-2");
            Thread.sleep(1000);
            
            createOrder("buy", "yes", 48, 15, "test-ws-3");
            Thread.sleep(1000);
            
            // Wait for events
            System.out.println("\nWaiting for events...");
            Thread.sleep(5000);
        }
        
        System.out.println("Closing connection...");
        client.close();
    }
    
    private static void createOrder(String action, String side, int price, int count, String clientOrderId) {
        try {
            HttpClient httpClient = HttpClient.newHttpClient();
            
            Map<String, Object> orderRequest = new HashMap<>();
            orderRequest.put("action", action);
            orderRequest.put("side", side);
            orderRequest.put("market_ticker", "INXD-23DEC29-B5000");
            orderRequest.put("type", "limit");
            orderRequest.put("price", price);
            orderRequest.put("count", count);
            orderRequest.put("client_order_id", clientOrderId);
            
            String json = objectMapper.writeValueAsString(orderRequest);
            
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:9090/trade-api/v2/portfolio/orders"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
            
            System.out.println("Creating order: " + action + " " + count + " " + side + " @ " + price);
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            System.out.println("Order response status: " + response.statusCode());
            
        } catch (Exception e) {
            System.err.println("Failed to create order: " + e.getMessage());
        }
    }
}