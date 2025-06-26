package com.kalshi.mock.websocket.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kalshi.mock.websocket.dto.*;
import com.kalshi.mock.websocket.service.SubscriptionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class KalshiWebSocketHandler extends TextWebSocketHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(KalshiWebSocketHandler.class);
    
    @Autowired
    private SubscriptionManager subscriptionManager;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @Autowired
    private com.kalshi.mock.service.OrderBookService orderBookService;
    
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        logger.info("WebSocket connection established: {}", session.getId());
        sessions.put(session.getId(), session);
    }
    
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        logger.debug("Received message from {}: {}", session.getId(), message.getPayload());
        
        try {
            Map<String, Object> payload = objectMapper.readValue(message.getPayload(), Map.class);
            String cmd = (String) payload.get("cmd");
            Integer id = (Integer) payload.get("id");
            
            switch (cmd) {
                case "subscribe":
                    handleSubscribe(session, payload, id);
                    break;
                case "unsubscribe":
                    handleUnsubscribe(session, payload, id);
                    break;
                case "update_subscription":
                    handleUpdateSubscription(session, payload, id);
                    break;
                default:
                    sendError(session, id, "Unknown command: " + cmd);
            }
        } catch (Exception e) {
            logger.error("Error processing message", e);
            sendError(session, null, "Invalid message format: " + e.getMessage());
        }
    }
    
    private void handleSubscribe(WebSocketSession session, Map<String, Object> payload, Integer id) throws IOException {
        SubscribeCommand command = objectMapper.convertValue(payload, SubscribeCommand.class);
        
        List<SubscriptionResponse.Subscription> subscriptions = new CopyOnWriteArrayList<>();
        
        if (command.getParams() != null && command.getParams().getChannels() != null) {
            for (String channel : command.getParams().getChannels()) {
                SubscriptionResponse.Subscription sub = subscriptionManager.subscribe(
                    session.getId(), 
                    channel, 
                    command.getParams().getMarketTickers()
                );
                subscriptions.add(sub);
            }
        }
        
        SubscriptionResponse response = new SubscriptionResponse();
        response.setId(id);
        response.setSubscriptions(subscriptions);
        
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
        
        // Send initial snapshots for orderbook_snapshot subscriptions
        if (command.getParams() != null && command.getParams().getChannels() != null 
            && command.getParams().getChannels().contains("orderbook_snapshot")
            && command.getParams().getMarketTickers() != null) {
            for (String marketTicker : command.getParams().getMarketTickers()) {
                orderBookService.publishInitialSnapshot(marketTicker, session.getId());
            }
        }
    }
    
    private void handleUnsubscribe(WebSocketSession session, Map<String, Object> payload, Integer id) throws IOException {
        UnsubscribeCommand command = objectMapper.convertValue(payload, UnsubscribeCommand.class);
        
        boolean success = true;
        if (command.getParams() != null && command.getParams().getSids() != null) {
            for (String sid : command.getParams().getSids()) {
                if (!subscriptionManager.unsubscribe(session.getId(), sid)) {
                    success = false;
                }
            }
        }
        
        Map<String, Object> response = new HashMap<>();
        response.put("type", "unsubscribed");
        response.put("id", id);
        response.put("success", success);
        
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
    }
    
    private void handleUpdateSubscription(WebSocketSession session, Map<String, Object> payload, Integer id) throws IOException {
        UpdateSubscriptionCommand command = objectMapper.convertValue(payload, UpdateSubscriptionCommand.class);
        
        if (command.getParams() != null) {
            SubscriptionResponse.Subscription sub = subscriptionManager.updateSubscription(
                session.getId(),
                command.getParams().getSid(),
                command.getParams().getMarketTickers()
            );
            
            if (sub != null) {
                SubscriptionResponse response = new SubscriptionResponse();
                response.setId(id);
                response.setType("subscription_updated");
                response.setSubscriptions(Collections.singletonList(sub));
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
            } else {
                sendError(session, id, "Subscription not found");
            }
        }
    }
    
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        logger.info("WebSocket connection closed: {} with status: {}", session.getId(), status);
        sessions.remove(session.getId());
        subscriptionManager.removeSession(session.getId());
    }
    
    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        logger.error("WebSocket transport error for session: " + session.getId(), exception);
        sessions.remove(session.getId());
        subscriptionManager.removeSession(session.getId());
    }
    
    private void sendError(WebSocketSession session, Integer id, String errorMessage) {
        try {
            Map<String, Object> error = new HashMap<>();
            error.put("type", "error");
            error.put("msg", errorMessage);
            if (id != null) {
                error.put("id", id);
            }
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(error)));
        } catch (IOException e) {
            logger.error("Failed to send error message", e);
        }
    }
    
    public void sendMessage(String sessionId, String message) throws IOException {
        WebSocketSession session = sessions.get(sessionId);
        if (session != null && session.isOpen()) {
            session.sendMessage(new TextMessage(message));
        }
    }
}