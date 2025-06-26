package com.kalshi.marketdata.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
public class MarketDataWebSocketHandler extends TextWebSocketHandler {
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @Autowired
    private RedisMessageListenerContainer redisMessageListenerContainer;
    
    // Track sessions and their subscriptions
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> sessionSubscriptions = new ConcurrentHashMap<>();
    private final Map<String, MessageListener> sessionListeners = new ConcurrentHashMap<>();
    
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        log.info("Client connected: {}", session.getId());
        sessions.put(session.getId(), session);
        sessionSubscriptions.put(session.getId(), ConcurrentHashMap.newKeySet());
        
        // Send welcome message
        Map<String, Object> welcome = Map.of(
            "type", "connected",
            "message", "Connected to Kalshi Market Data Server",
            "sessionId", session.getId()
        );
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(welcome)));
    }
    
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        log.debug("Received from client {}: {}", session.getId(), payload);
        
        try {
            Map<String, Object> request = objectMapper.readValue(payload, Map.class);
            String action = (String) request.get("action");
            
            switch (action) {
                case "subscribe":
                    handleSubscribe(session, request);
                    break;
                case "unsubscribe":
                    handleUnsubscribe(session, request);
                    break;
                case "ping":
                    handlePing(session);
                    break;
                default:
                    sendError(session, "Unknown action: " + action);
            }
        } catch (Exception e) {
            log.error("Error processing client message", e);
            sendError(session, "Invalid message format");
        }
    }
    
    private void handleSubscribe(WebSocketSession session, Map<String, Object> request) throws IOException {
        String channel = (String) request.get("channel");
        
        if (channel == null) {
            sendError(session, "Channel is required");
            return;
        }
        
        String sessionId = session.getId();
        Set<String> subscriptions = sessionSubscriptions.get(sessionId);
        
        // Check if already subscribed
        if (subscriptions.contains(channel)) {
            sendResponse(session, "already_subscribed", Map.of("channel", channel));
            return;
        }
        
        // Create message listener for this session
        MessageListener listener = sessionListeners.computeIfAbsent(sessionId, k -> 
            (Message msg, byte[] pattern) -> {
                try {
                    String messageStr = new String(msg.getBody());
                    WebSocketSession ws = sessions.get(sessionId);
                    if (ws != null && ws.isOpen()) {
                        ws.sendMessage(new TextMessage(messageStr));
                    }
                } catch (Exception e) {
                    log.error("Error forwarding message to WebSocket client", e);
                }
            }
        );
        
        // Subscribe to Redis channel
        String redisChannel = "market-data:" + channel;
        redisMessageListenerContainer.addMessageListener(listener, new ChannelTopic(redisChannel));
        subscriptions.add(channel);
        
        log.info("Client {} subscribed to channel: {}", sessionId, channel);
        sendResponse(session, "subscribed", Map.of("channel", channel));
    }
    
    private void handleUnsubscribe(WebSocketSession session, Map<String, Object> request) throws IOException {
        String channel = (String) request.get("channel");
        
        if (channel == null) {
            sendError(session, "Channel is required");
            return;
        }
        
        String sessionId = session.getId();
        Set<String> subscriptions = sessionSubscriptions.get(sessionId);
        
        if (subscriptions.remove(channel)) {
            // If no more subscriptions, remove the listener
            if (subscriptions.isEmpty()) {
                MessageListener listener = sessionListeners.remove(sessionId);
                if (listener != null) {
                    redisMessageListenerContainer.removeMessageListener(listener);
                }
            }
            
            log.info("Client {} unsubscribed from channel: {}", sessionId, channel);
            sendResponse(session, "unsubscribed", Map.of("channel", channel));
        } else {
            sendResponse(session, "not_subscribed", Map.of("channel", channel));
        }
    }
    
    private void handlePing(WebSocketSession session) throws IOException {
        sendResponse(session, "pong", Map.of("timestamp", System.currentTimeMillis()));
    }
    
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String sessionId = session.getId();
        log.info("Client disconnected: {} - {}", sessionId, status);
        
        // Clean up
        sessions.remove(sessionId);
        sessionSubscriptions.remove(sessionId);
        
        // Remove Redis listener
        MessageListener listener = sessionListeners.remove(sessionId);
        if (listener != null) {
            redisMessageListenerContainer.removeMessageListener(listener);
        }
    }
    
    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.error("WebSocket transport error for session: " + session.getId(), exception);
    }
    
    private void sendResponse(WebSocketSession session, String type, Map<String, Object> data) throws IOException {
        Map<String, Object> response = new HashMap<>();
        response.put("type", type);
        response.put("data", data);
        response.put("timestamp", System.currentTimeMillis());
        
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
    }
    
    private void sendError(WebSocketSession session, String error) throws IOException {
        Map<String, Object> response = Map.of(
            "type", "error",
            "error", error,
            "timestamp", System.currentTimeMillis()
        );
        
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
    }
}