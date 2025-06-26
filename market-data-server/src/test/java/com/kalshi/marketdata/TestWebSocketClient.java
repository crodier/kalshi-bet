package com.kalshi.marketdata;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.util.Scanner;

public class TestWebSocketClient {
    
    public static void main(String[] args) throws Exception {
        URI uri = new URI("ws://localhost:8084/ws/market-data");
        
        WebSocketClient client = new WebSocketClient(uri) {
            @Override
            public void onOpen(ServerHandshake handshake) {
                System.out.println("Connected to Market Data Server");
                
                // Subscribe to all market data
                send("{\"action\":\"subscribe\",\"channel\":\"all\"}");
            }
            
            @Override
            public void onMessage(String message) {
                System.out.println("Received: " + message);
            }
            
            @Override
            public void onClose(int code, String reason, boolean remote) {
                System.out.println("Connection closed: " + reason);
            }
            
            @Override
            public void onError(Exception ex) {
                ex.printStackTrace();
            }
        };
        
        client.connect();
        
        System.out.println("Press Enter to exit...");
        new Scanner(System.in).nextLine();
        
        client.close();
    }
}