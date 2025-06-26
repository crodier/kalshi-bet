package com.kalshi.orderbook.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderBookWebSocketHandler extends TextWebSocketHandler {
    
    private final WebSocketSessionManager sessionManager;
    private final ObjectMapper objectMapper;
    
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        com.kalshi.orderbook.websocket.WebSocketSession wsSession = new com.kalshi.orderbook.websocket.WebSocketSession(session);
        sessionManager.addSession(wsSession);
        
        // Send welcome message
        Map<String, Object> welcome = Map.of(
            "type", "welcome",
            "message", "Connected to Order Book WebSocket",
            "sessionId", session.getId()
        );
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(welcome)));
    }
    
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        try {
            Map<String, Object> payload = objectMapper.readValue(message.getPayload(), Map.class);
            String type = (String) payload.get("type");
            
            switch (type) {
                case "subscribe":
                    handleSubscribe(session.getId(), payload);
                    break;
                case "unsubscribe":
                    handleUnsubscribe(session.getId(), payload);
                    break;
                case "heartbeat":
                    handleHeartbeat(session.getId());
                    break;
                default:
                    log.warn("Unknown message type: {}", type);
            }
        } catch (Exception e) {
            log.error("Error handling WebSocket message", e);
            sendError(session, "Invalid message format");
        }
    }
    
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        sessionManager.removeSession(session.getId());
    }
    
    private void handleSubscribe(String sessionId, Map<String, Object> payload) {
        String marketTicker = (String) payload.get("market");
        Boolean allChanges = (Boolean) payload.getOrDefault("allChanges", false);
        
        if (marketTicker != null) {
            sessionManager.subscribeToMarket(sessionId, marketTicker);
            
            com.kalshi.orderbook.websocket.WebSocketSession wsSession = sessionManager.getSession(sessionId);
            if (wsSession != null) {
                wsSession.setSubscribeToAllChanges(allChanges);
            }
        }
    }
    
    private void handleUnsubscribe(String sessionId, Map<String, Object> payload) {
        String marketTicker = (String) payload.get("market");
        if (marketTicker != null) {
            sessionManager.unsubscribeFromMarket(sessionId, marketTicker);
        }
    }
    
    private void handleHeartbeat(String sessionId) {
        com.kalshi.orderbook.websocket.WebSocketSession wsSession = sessionManager.getSession(sessionId);
        if (wsSession != null) {
            wsSession.setLastHeartbeat(System.currentTimeMillis());
        }
    }
    
    private void sendError(WebSocketSession session, String error) {
        try {
            Map<String, Object> errorMsg = Map.of(
                "type", "error",
                "message", error
            );
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(errorMsg)));
        } catch (Exception e) {
            log.error("Failed to send error message", e);
        }
    }
}