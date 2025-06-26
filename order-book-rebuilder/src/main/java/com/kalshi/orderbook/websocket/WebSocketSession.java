package com.kalshi.orderbook.websocket;

import lombok.Getter;
import lombok.Setter;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Getter
public class WebSocketSession {
    private final String sessionId;
    private final org.springframework.web.socket.WebSocketSession session;
    private final Set<String> subscribedMarkets = ConcurrentHashMap.newKeySet();
    
    @Setter
    private boolean subscribeToAllChanges = false;
    
    @Setter
    private long lastHeartbeat;
    
    public WebSocketSession(org.springframework.web.socket.WebSocketSession session) {
        this.session = session;
        this.sessionId = session.getId();
        this.lastHeartbeat = System.currentTimeMillis();
    }
    
    public void addSubscription(String marketTicker) {
        subscribedMarkets.add(marketTicker);
    }
    
    public void removeSubscription(String marketTicker) {
        subscribedMarkets.remove(marketTicker);
    }
    
    public void clearSubscriptions() {
        subscribedMarkets.clear();
    }
    
    public boolean isSubscribedTo(String marketTicker) {
        return subscribedMarkets.contains(marketTicker);
    }
}