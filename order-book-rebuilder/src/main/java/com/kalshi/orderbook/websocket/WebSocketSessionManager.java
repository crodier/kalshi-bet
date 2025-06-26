package com.kalshi.orderbook.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

@Slf4j
@Component
public class WebSocketSessionManager {
    
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final Map<String, List<com.kalshi.orderbook.websocket.WebSocketSession>> marketSubscriptions = new ConcurrentHashMap<>();
    
    @Value("${websocket.max-sessions-per-market:1000}")
    private int maxSessionsPerMarket;
    
    @Value("${websocket.heartbeat-interval-ms:30000}")
    private long heartbeatIntervalMs;
    
    public void addSession(WebSocketSession session) {
        sessions.put(session.getSessionId(), session);
        log.info("Added WebSocket session: {}", session.getSessionId());
    }
    
    public void removeSession(String sessionId) {
        WebSocketSession session = sessions.remove(sessionId);
        if (session != null) {
            // Remove from all market subscriptions
            session.getSubscribedMarkets().forEach(market -> {
                marketSubscriptions.computeIfPresent(market, (k, v) -> {
                    v.remove(session);
                    return v.isEmpty() ? null : v;
                });
            });
            log.info("Removed WebSocket session: {}", sessionId);
        }
    }
    
    public void subscribeToMarket(String sessionId, String marketTicker) {
        WebSocketSession session = sessions.get(sessionId);
        if (session == null) {
            log.warn("Session not found: {}", sessionId);
            return;
        }
        
        session.addSubscription(marketTicker);
        marketSubscriptions.computeIfAbsent(marketTicker, k -> new CopyOnWriteArrayList<>())
            .add(session);
        
        log.debug("Session {} subscribed to market {}", sessionId, marketTicker);
    }
    
    public void unsubscribeFromMarket(String sessionId, String marketTicker) {
        WebSocketSession session = sessions.get(sessionId);
        if (session != null) {
            session.removeSubscription(marketTicker);
            marketSubscriptions.computeIfPresent(marketTicker, (k, v) -> {
                v.remove(session);
                return v.isEmpty() ? null : v;
            });
            log.debug("Session {} unsubscribed from market {}", sessionId, marketTicker);
        }
    }
    
    public Collection<WebSocketSession> getSessionsForMarket(String marketTicker) {
        return marketSubscriptions.getOrDefault(marketTicker, List.of());
    }
    
    public Collection<WebSocketSession> getAllSessions() {
        return sessions.values();
    }
    
    public WebSocketSession getSession(String sessionId) {
        return sessions.get(sessionId);
    }
    
    @Scheduled(fixedDelayString = "${websocket.heartbeat-check-interval-ms:60000}")
    public void checkSessionHealth() {
        long now = System.currentTimeMillis();
        sessions.entrySet().removeIf(entry -> {
            WebSocketSession session = entry.getValue();
            if (now - session.getLastHeartbeat() > heartbeatIntervalMs * 2) {
                log.warn("Removing stale session: {}", session.getSessionId());
                removeSession(session.getSessionId());
                return true;
            }
            return false;
        });
    }
}