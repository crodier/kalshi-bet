package com.kalshi.marketdata.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kalshi.marketdata.event.OrderBookUpdateEvent;
import com.kalshi.marketdata.service.AdminService;
import com.kalshi.marketdata.service.MarketSearchService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * WebSocket handler for admin interface real-time updates
 */
@Component
@Slf4j
public class AdminWebSocketHandler implements WebSocketHandler {
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @Autowired
    private AdminService adminService;
    
    @Autowired
    private MarketSearchService marketSearchService;
    
    // Track connected sessions and their subscriptions
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final Map<String, String> subscribedMarkets = new ConcurrentHashMap<>(); // sessionId -> marketTicker
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String sessionId = session.getId();
        sessions.put(sessionId, session);
        log.info("Admin WebSocket connection established: {}", sessionId);
        
        // Send initial system stats
        sendSystemStats(session);
        
        // Start periodic updates for this session
        startPeriodicUpdates(session);
    }

    @Override
    public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) throws Exception {
        try {
            String payload = (String) message.getPayload();
            JsonNode messageNode = objectMapper.readTree(payload);
            
            String type = messageNode.get("type").asText();
            
            switch (type) {
                case "subscribe":
                    handleSubscribe(session, messageNode);
                    break;
                case "unsubscribe":
                    handleUnsubscribe(session, messageNode);
                    break;
                case "filter-markets":
                    handleMarketFilter(session, messageNode);
                    break;
                case "pong":
                    // Handle pong response (connection keepalive)
                    log.debug("Received pong from session: {}", session.getId());
                    break;
                default:
                    log.warn("Unknown message type: {} from session: {}", type, session.getId());
            }
            
        } catch (Exception e) {
            log.error("Error handling admin WebSocket message from session: {}", session.getId(), e);
            sendError(session, "Failed to process message: " + e.getMessage());
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.error("Admin WebSocket transport error for session: {}", session.getId(), exception);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) throws Exception {
        String sessionId = session.getId();
        log.info("Admin WebSocket connection closed: {} with status: {}", sessionId, closeStatus);
        
        // Clean up session data
        sessions.remove(sessionId);
        subscribedMarkets.remove(sessionId);
    }

    @Override
    public boolean supportsPartialMessages() {
        return false;
    }
    
    private void handleSubscribe(WebSocketSession session, JsonNode messageNode) throws IOException {
        String channel = messageNode.get("channel").asText();
        
        switch (channel) {
            case "system-stats":
                sendSystemStats(session);
                break;
            case "market-data":
                String marketTicker = messageNode.get("marketTicker").asText();
                subscribeToMarket(session, marketTicker);
                break;
            default:
                log.warn("Unknown subscription channel: {} from session: {}", channel, session.getId());
        }
    }
    
    private void handleUnsubscribe(WebSocketSession session, JsonNode messageNode) {
        String channel = messageNode.get("channel").asText();
        
        if ("market-data".equals(channel)) {
            unsubscribeFromMarket(session);
        }
    }
    
    private void handleMarketFilter(WebSocketSession session, JsonNode messageNode) throws IOException {
        String searchTerm = messageNode.get("searchTerm").asText();
        
        // Use MarketSearchService to find matching markets
        var matchingMarkets = marketSearchService.searchMarkets(searchTerm);
        
        // Send filtered results
        Map<String, Object> response = Map.of(
            "type", "market-filter-result",
            "payload", matchingMarkets
        );
        
        sendMessage(session, response);
    }
    
    private void subscribeToMarket(WebSocketSession session, String marketTicker) throws IOException {
        subscribedMarkets.put(session.getId(), marketTicker);
        log.debug("Session {} subscribed to market: {}", session.getId(), marketTicker);
        
        // Send initial order book data
        var orderBookData = adminService.getOrderBookData(marketTicker);
        if (orderBookData != null) {
            Map<String, Object> response = Map.of(
                "type", "market-data",
                "payload", orderBookData
            );
            sendMessage(session, response);
        }
    }
    
    private void unsubscribeFromMarket(WebSocketSession session) {
        String previousMarket = subscribedMarkets.remove(session.getId());
        if (previousMarket != null) {
            log.debug("Session {} unsubscribed from market: {}", session.getId(), previousMarket);
        }
    }
    
    private void startPeriodicUpdates(WebSocketSession session) {
        // Send system stats every 5 seconds
        scheduler.scheduleAtFixedRate(() -> {
            if (session.isOpen()) {
                try {
                    sendSystemStats(session);
                } catch (IOException e) {
                    log.error("Failed to send periodic system stats to session: {}", session.getId(), e);
                }
            }
        }, 5, 5, TimeUnit.SECONDS);
        
        // Send ping every 30 seconds for connection keepalive
        scheduler.scheduleAtFixedRate(() -> {
            if (session.isOpen()) {
                try {
                    Map<String, Object> ping = Map.of("type", "ping");
                    sendMessage(session, ping);
                } catch (IOException e) {
                    log.error("Failed to send ping to session: {}", session.getId(), e);
                }
            }
        }, 30, 30, TimeUnit.SECONDS);
    }
    
    private void sendSystemStats(WebSocketSession session) throws IOException {
        var stats = adminService.getSystemStatistics();
        
        Map<String, Object> response = Map.of(
            "type", "system-stats",
            "payload", stats
        );
        
        sendMessage(session, response);
    }
    
    public void broadcastOrderBookUpdate(String marketTicker, Object orderBookData) {
        sessions.values().parallelStream().forEach(session -> {
            try {
                String subscribedMarket = subscribedMarkets.get(session.getId());
                if (marketTicker.equals(subscribedMarket) && session.isOpen()) {
                    Map<String, Object> response = Map.of(
                        "type", "market-data",
                        "payload", orderBookData
                    );
                    sendMessage(session, response);
                }
            } catch (Exception e) {
                log.error("Failed to broadcast order book update to session: {}", session.getId(), e);
            }
        });
    }
    
    private void sendError(WebSocketSession session, String errorMessage) throws IOException {
        Map<String, Object> error = Map.of(
            "type", "error",
            "message", errorMessage
        );
        sendMessage(session, error);
    }
    
    @EventListener
    public void handleOrderBookUpdate(OrderBookUpdateEvent event) {
        // Broadcast order book updates to subscribed admin sessions
        broadcastOrderBookUpdate(event.getMarketTicker(), event.getOrderBookData());
    }
    
    private void sendMessage(WebSocketSession session, Object message) throws IOException {
        if (session.isOpen()) {
            String json = objectMapper.writeValueAsString(message);
            session.sendMessage(new TextMessage(json));
        }
    }
}