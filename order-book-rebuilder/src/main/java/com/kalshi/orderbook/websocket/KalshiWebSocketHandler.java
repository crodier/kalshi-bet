package com.kalshi.orderbook.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
@RequiredArgsConstructor
public class KalshiWebSocketHandler extends TextWebSocketHandler {
    
    private final WebSocketSessionManager sessionManager;
    private final ObjectMapper objectMapper;
    private final AtomicInteger messageIdCounter = new AtomicInteger(1);
    
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        com.kalshi.orderbook.websocket.WebSocketSession wsSession = 
            new com.kalshi.orderbook.websocket.WebSocketSession(session);
        sessionManager.addSession(wsSession);
        
        log.info("Kalshi WebSocket connection established: {}", session.getId());
    }
    
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        try {
            Map<String, Object> payload = objectMapper.readValue(message.getPayload(), Map.class);
            String cmd = (String) payload.get("cmd");
            Object id = payload.get("id");
            
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
                    sendError(session, "Unknown command: " + cmd, id);
            }
        } catch (Exception e) {
            log.error("Error handling Kalshi WebSocket message", e);
            sendError(session, "Invalid message format", null);
        }
    }
    
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        sessionManager.removeSession(session.getId());
        log.info("Kalshi WebSocket connection closed: {}", session.getId());
    }
    
    private void handleSubscribe(WebSocketSession session, Map<String, Object> payload, Object id) {
        try {
            Map<String, Object> params = (Map<String, Object>) payload.get("params");
            List<String> channels = (List<String>) params.get("channels");
            List<String> marketTickers = (List<String>) params.get("market_tickers");
            
            // Generate subscription ID
            String sid = UUID.randomUUID().toString();
            
            // Subscribe to markets
            if (marketTickers != null) {
                for (String ticker : marketTickers) {
                    sessionManager.subscribeToMarket(session.getId(), ticker);
                }
            }
            
            // Send success response
            Map<String, Object> response = Map.of(
                "type", "response",
                "id", id,
                "msg", Map.of(
                    "sid", sid,
                    "subscribed_channels", channels != null ? channels : List.of(),
                    "subscribed_markets", marketTickers != null ? marketTickers : List.of()
                )
            );
            
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
            
        } catch (Exception e) {
            log.error("Error handling subscribe command", e);
            sendError(session, "Subscribe failed: " + e.getMessage(), id);
        }
    }
    
    private void handleUnsubscribe(WebSocketSession session, Map<String, Object> payload, Object id) {
        try {
            Map<String, Object> params = (Map<String, Object>) payload.get("params");
            List<String> sids = (List<String>) params.get("sids");
            
            // For simplicity, unsubscribe from all markets for this session
            com.kalshi.orderbook.websocket.WebSocketSession wsSession = sessionManager.getSession(session.getId());
            if (wsSession != null) {
                wsSession.clearSubscriptions();
            }
            
            Map<String, Object> response = Map.of(
                "type", "response",
                "id", id,
                "msg", Map.of(
                    "unsubscribed_sids", sids != null ? sids : List.of()
                )
            );
            
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
            
        } catch (Exception e) {
            log.error("Error handling unsubscribe command", e);
            sendError(session, "Unsubscribe failed: " + e.getMessage(), id);
        }
    }
    
    private void handleUpdateSubscription(WebSocketSession session, Map<String, Object> payload, Object id) {
        try {
            Map<String, Object> params = (Map<String, Object>) payload.get("params");
            String sid = (String) params.get("sid");
            List<String> marketTickers = (List<String>) params.get("market_tickers");
            
            // Update subscriptions
            if (marketTickers != null) {
                // Clear existing and add new
                com.kalshi.orderbook.websocket.WebSocketSession wsSession = sessionManager.getSession(session.getId());
                if (wsSession != null) {
                    wsSession.clearSubscriptions();
                    for (String ticker : marketTickers) {
                        sessionManager.subscribeToMarket(session.getId(), ticker);
                    }
                }
            }
            
            Map<String, Object> response = Map.of(
                "type", "response",
                "id", id,
                "msg", Map.of(
                    "sid", sid,
                    "updated_markets", marketTickers != null ? marketTickers : List.of()
                )
            );
            
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
            
        } catch (Exception e) {
            log.error("Error handling update subscription command", e);
            sendError(session, "Update subscription failed: " + e.getMessage(), id);
        }
    }
    
    private void sendError(WebSocketSession session, String error, Object id) {
        try {
            Map<String, Object> errorMsg = Map.of(
                "type", "error",
                "id", id != null ? id : 0,
                "msg", error
            );
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(errorMsg)));
        } catch (Exception e) {
            log.error("Failed to send error message", e);
        }
    }
}