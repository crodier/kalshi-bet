package com.kalshi.mock.websocket.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class WebSocketTestClient {
    
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static CountDownLatch connectLatch = new CountDownLatch(1);
    private static int commandId = 1;
    
    public static void main(String[] args) {
        try {
            // Connect to WebSocket server
            URI serverUri = new URI("ws://localhost:9090/trade-api/ws/v2");
            
            WebSocketClient client = new WebSocketClient(serverUri) {
                @Override
                public void onOpen(ServerHandshake handshake) {
                    System.out.println("Connected to WebSocket server");
                    connectLatch.countDown();
                }
                
                @Override
                public void onMessage(String message) {
                    System.out.println("Received: " + message);
                    try {
                        // Pretty print JSON
                        Object json = objectMapper.readValue(message, Object.class);
                        String prettyJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(json);
                        System.out.println("Pretty JSON:\n" + prettyJson);
                    } catch (Exception e) {
                        System.err.println("Failed to parse JSON: " + e.getMessage());
                    }
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
            
            // Connect to the server
            System.out.println("Connecting to " + serverUri + "...");
            client.connect();
            
            // Wait for connection
            if (!connectLatch.await(5, TimeUnit.SECONDS)) {
                System.err.println("Failed to connect within 5 seconds");
                return;
            }
            
            // Create a scanner for user input
            Scanner scanner = new Scanner(System.in);
            
            // Display menu
            while (true) {
                System.out.println("\n=== WebSocket Test Client Menu ===");
                System.out.println("1. Subscribe to channels");
                System.out.println("2. Unsubscribe from channels");
                System.out.println("3. Update subscription");
                System.out.println("4. Send custom JSON");
                System.out.println("5. Exit");
                System.out.print("Choose an option: ");
                
                String choice = scanner.nextLine();
                
                switch (choice) {
                    case "1":
                        subscribeToChannels(client, scanner);
                        break;
                    case "2":
                        unsubscribeFromChannels(client, scanner);
                        break;
                    case "3":
                        updateSubscription(client, scanner);
                        break;
                    case "4":
                        sendCustomJson(client, scanner);
                        break;
                    case "5":
                        System.out.println("Closing connection...");
                        client.close();
                        System.exit(0);
                        break;
                    default:
                        System.out.println("Invalid option");
                }
            }
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private static void subscribeToChannels(WebSocketClient client, Scanner scanner) throws Exception {
        System.out.print("Enter channels (comma-separated, e.g., orderbook_snapshot,ticker): ");
        String channelsInput = scanner.nextLine();
        List<String> channels = Arrays.asList(channelsInput.split(","));
        
        System.out.print("Enter market tickers (comma-separated, e.g., INXD-23DEC29-B5000,BTCZ-23DEC31-B50000): ");
        String tickersInput = scanner.nextLine();
        List<String> tickers = Arrays.asList(tickersInput.split(","));
        
        // Create subscribe command
        Map<String, Object> command = new HashMap<>();
        command.put("id", commandId++);
        command.put("cmd", "subscribe");
        
        Map<String, Object> params = new HashMap<>();
        params.put("channels", channels);
        params.put("market_tickers", tickers);
        command.put("params", params);
        
        String json = objectMapper.writeValueAsString(command);
        System.out.println("Sending: " + json);
        client.send(json);
    }
    
    private static void unsubscribeFromChannels(WebSocketClient client, Scanner scanner) throws Exception {
        System.out.print("Enter subscription IDs to unsubscribe (comma-separated, e.g., sub_1,sub_2): ");
        String sidsInput = scanner.nextLine();
        List<String> sids = Arrays.asList(sidsInput.split(","));
        
        // Create unsubscribe command
        Map<String, Object> command = new HashMap<>();
        command.put("id", commandId++);
        command.put("cmd", "unsubscribe");
        
        Map<String, Object> params = new HashMap<>();
        params.put("sids", sids);
        command.put("params", params);
        
        String json = objectMapper.writeValueAsString(command);
        System.out.println("Sending: " + json);
        client.send(json);
    }
    
    private static void updateSubscription(WebSocketClient client, Scanner scanner) throws Exception {
        System.out.print("Enter subscription ID to update: ");
        String sid = scanner.nextLine();
        
        System.out.print("Enter new market tickers (comma-separated): ");
        String tickersInput = scanner.nextLine();
        List<String> tickers = Arrays.asList(tickersInput.split(","));
        
        // Create update_subscription command
        Map<String, Object> command = new HashMap<>();
        command.put("id", commandId++);
        command.put("cmd", "update_subscription");
        
        Map<String, Object> params = new HashMap<>();
        params.put("sid", sid);
        params.put("market_tickers", tickers);
        command.put("params", params);
        
        String json = objectMapper.writeValueAsString(command);
        System.out.println("Sending: " + json);
        client.send(json);
    }
    
    private static void sendCustomJson(WebSocketClient client, Scanner scanner) throws Exception {
        System.out.println("Enter custom JSON (on one line):");
        String json = scanner.nextLine();
        
        // Validate it's valid JSON
        try {
            objectMapper.readValue(json, Object.class);
            System.out.println("Sending: " + json);
            client.send(json);
        } catch (Exception e) {
            System.err.println("Invalid JSON: " + e.getMessage());
        }
    }
}