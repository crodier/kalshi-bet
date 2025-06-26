package com.kalshi.mock.websocket.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.util.*;

public class SimpleWebSocketTest {
    
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    public static void main(String[] args) throws Exception {
        URI serverUri = new URI("ws://localhost:9090/trade-api/ws/v2");
        
        WebSocketClient client = new WebSocketClient(serverUri) {
            @Override
            public void onOpen(ServerHandshake handshake) {
                System.out.println("Connected to WebSocket server");
                
                // Immediately send subscribe command
                try {
                    Map<String, Object> command = new HashMap<>();
                    command.put("id", 1);
                    command.put("cmd", "subscribe");
                    
                    Map<String, Object> params = new HashMap<>();
                    params.put("channels", Arrays.asList("orderbook_snapshot", "orderbook_delta", "ticker"));
                    params.put("market_tickers", Arrays.asList("INXD-23DEC29-B5000", "BTCZ-23DEC31-B50000"));
                    command.put("params", params);
                    
                    String json = objectMapper.writeValueAsString(command);
                    System.out.println("Sending: " + json);
                    send(json);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            
            @Override
            public void onMessage(String message) {
                System.out.println("\n=== RECEIVED MESSAGE ===");
                System.out.println("Raw: " + message);
                try {
                    Object json = objectMapper.readValue(message, Object.class);
                    String prettyJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(json);
                    System.out.println("Pretty:\n" + prettyJson);
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
        
        // Keep running for 30 seconds
        Thread.sleep(30000);
        
        System.out.println("Closing connection...");
        client.close();
    }
}