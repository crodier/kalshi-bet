package com.fbg.kalshi.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kalshi.mock.event.OrderUpdateEvent;
import com.kalshi.mock.event.OrderUpdateEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * WebSocket handler for internal order updates.
 * This WebSocket endpoint is used for private order updates that should only be sent
 * to the user who owns the orders.
 */
@Component
public class InternalOrdersWebSocketHandler extends TextWebSocketHandler {
    private static final Logger logger = LoggerFactory.getLogger(InternalOrdersWebSocketHandler.class);
    
    private final ObjectMapper objectMapper;
    private final OrderUpdateEventPublisher orderUpdateEventPublisher;
    
    // Session management
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> sessionSubscriptions = new ConcurrentHashMap<>();
    private final Map<String, Map<Integer, SubscriptionInfo>> sessionSubscriptionMap = new ConcurrentHashMap<>();
    private final AtomicInteger subscriptionIdCounter = new AtomicInteger(2000);
    
    @Autowired
    public InternalOrdersWebSocketHandler(ObjectMapper objectMapper, 
                                        OrderUpdateEventPublisher orderUpdateEventPublisher) {
        this.objectMapper = objectMapper;
        this.orderUpdateEventPublisher = orderUpdateEventPublisher;
        
        // Register as a listener for order update events
        orderUpdateEventPublisher.addListener(this::handleOrderUpdateEvent);
    }
    
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        logger.info("Internal orders WebSocket connection established: {}", session.getId());
        sessions.put(session.getId(), session);
        sessionSubscriptions.put(session.getId(), new HashSet<>());
        sessionSubscriptionMap.put(session.getId(), new HashMap<>());
    }
    
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        logger.info("Internal orders WebSocket connection closed: {}", session.getId());
        sessions.remove(session.getId());
        sessionSubscriptions.remove(session.getId());
        sessionSubscriptionMap.remove(session.getId());
    }
    
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        try {
            String payload = message.getPayload();
            logger.debug("Received message on internal orders WebSocket: {}", payload);
            
            Map<String, Object> msg = objectMapper.readValue(payload, Map.class);
            String cmd = (String) msg.get("cmd");
            
            switch (cmd) {
                case "subscribe":
                    handleSubscribe(session, msg);
                    break;
                case "unsubscribe":
                    handleUnsubscribe(session, msg);
                    break;
                case "ping":
                    handlePing(session, msg);
                    break;
                default:
                    logger.warn("Unknown command: {}", cmd);
                    sendError(session, "Unknown command: " + cmd);
            }
        } catch (Exception e) {
            logger.error("Error handling WebSocket message", e);
            sendError(session, "Error processing message: " + e.getMessage());
        }
    }
    
    private void handleSubscribe(WebSocketSession session, Map<String, Object> msg) throws IOException {
        Integer id = (Integer) msg.get("id");
        Map<String, Object> params = (Map<String, Object>) msg.get("params");
        
        if (params == null) {
            sendError(session, "Missing params in subscribe message");
            return;
        }
        
        List<String> channels = (List<String>) params.get("channels");
        List<String> marketTickers = (List<String>) params.get("market_tickers");
        
        if (channels == null || marketTickers == null) {
            sendError(session, "Missing channels or market_tickers in params");
            return;
        }
        
        // For internal orders, we only support the "orders" channel
        if (!channels.contains("orders")) {
            sendError(session, "Internal orders WebSocket only supports 'orders' channel");
            return;
        }
        
        List<Map<String, Object>> subscriptions = new ArrayList<>();
        Set<String> sessionSubs = sessionSubscriptions.get(session.getId());
        Map<Integer, SubscriptionInfo> subMap = sessionSubscriptionMap.get(session.getId());
        
        for (String ticker : marketTickers) {
            String subKey = "orders:" + ticker;
            sessionSubs.add(subKey);
            
            int sid = subscriptionIdCounter.incrementAndGet();
            SubscriptionInfo subInfo = new SubscriptionInfo(sid, "orders", ticker);
            subMap.put(sid, subInfo);
            
            Map<String, Object> subDetail = new HashMap<>();
            subDetail.put("sid", "sub_" + sid);
            subDetail.put("channel", "orders");
            subDetail.put("market_tickers", Collections.singletonList(ticker));
            subscriptions.add(subDetail);
            
            logger.info("Session {} subscribed to orders for market: {}", session.getId(), ticker);
        }
        
        // Send subscription confirmation
        Map<String, Object> subscribedMsg = new HashMap<>();
        subscribedMsg.put("type", "subscribed");
        subscribedMsg.put("id", id);
        subscribedMsg.put("subscriptions", subscriptions);
        sendMessage(session, subscribedMsg);
    }
    
    private void handleUnsubscribe(WebSocketSession session, Map<String, Object> msg) throws IOException {
        Integer id = (Integer) msg.get("id");
        Integer sid = (Integer) msg.get("sid");
        
        if (sid != null) {
            Map<Integer, SubscriptionInfo> subMap = sessionSubscriptionMap.get(session.getId());
            SubscriptionInfo subInfo = subMap.remove(sid);
            
            if (subInfo != null) {
                String subKey = subInfo.channel + ":" + subInfo.marketTicker;
                sessionSubscriptions.get(session.getId()).remove(subKey);
                logger.info("Session {} unsubscribed from {}", session.getId(), subKey);
            }
        }
        
        // Send unsubscribe confirmation
        Map<String, Object> response = new HashMap<>();
        response.put("type", "unsubscribed");
        response.put("id", id);
        sendMessage(session, response);
    }
    
    private void handlePing(WebSocketSession session, Map<String, Object> msg) throws IOException {
        Map<String, Object> pong = new HashMap<>();
        pong.put("type", "pong");
        pong.put("id", msg.get("id"));
        sendMessage(session, pong);
    }
    
    @EventListener
    public void handleOrderUpdateEvent(OrderUpdateEvent event) {
        logger.debug("Handling order update event for order: {} in market: {}", 
                    event.getOrderId(), event.getMarketTicker());
        
        // For each session, check if they're subscribed to this market's orders
        for (Map.Entry<String, WebSocketSession> entry : sessions.entrySet()) {
            String sessionId = entry.getKey();
            WebSocketSession session = entry.getValue();
            
            if (!session.isOpen()) {
                continue;
            }
            
            Set<String> subs = sessionSubscriptions.get(sessionId);
            String subKey = "orders:" + event.getMarketTicker();
            
            if (subs != null && subs.contains(subKey)) {
                try {
                    // In a real system, we would check if this user owns the order
                    // For now, we broadcast all orders to all subscribers of the market
                    // TODO: Add user authentication and filtering
                    
                    // Wrap the OrderUpdateMessage in a WebSocket message envelope
                    Map<String, Object> wsMessage = new HashMap<>();
                    wsMessage.put("type", "order_update");
                    wsMessage.put("msg", event.getOrderUpdate());
                    
                    sendMessage(session, wsMessage);
                    logger.debug("Sent order update to session: {}", sessionId);
                } catch (Exception e) {
                    logger.error("Error sending order update to session: {}", sessionId, e);
                }
            }
        }
    }
    
    private void sendMessage(WebSocketSession session, Object message) throws IOException {
        if (session.isOpen()) {
            String json = objectMapper.writeValueAsString(message);
            session.sendMessage(new TextMessage(json));
        }
    }
    
    private void sendError(WebSocketSession session, String error) throws IOException {
        Map<String, Object> errorMsg = new HashMap<>();
        errorMsg.put("type", "error");
        errorMsg.put("error", error);
        sendMessage(session, errorMsg);
    }
    
    private static class SubscriptionInfo {
        final int sid;
        final String channel;
        final String marketTicker;
        
        SubscriptionInfo(int sid, String channel, String marketTicker) {
            this.sid = sid;
            this.channel = channel;
            this.marketTicker = marketTicker;
        }
    }
}