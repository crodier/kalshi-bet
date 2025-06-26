package com.kalshi.orderbook.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kalshi.orderbook.service.OrderBookManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Controller
@RequiredArgsConstructor
public class AdminWebSocketHandler {
    
    private final SimpMessagingTemplate messagingTemplate;
    private final OrderBookManager orderBookManager;
    private final ObjectMapper objectMapper;
    
    // Track subscriptions per session
    private final Map<String, SubscriptionInfo> sessionSubscriptions = new ConcurrentHashMap<>();
    
    @MessageMapping("/admin/subscribe")
    public void handleAdminSubscription(@Payload Map<String, Object> message, SimpMessageHeaderAccessor headerAccessor) {
        try {
            String sessionId = headerAccessor.getSessionId();
            log.debug("Admin subscription request from session: {}", sessionId);
            
            // Parse subscription request
            String command = (String) message.get("cmd");
            Map<String, Object> params = (Map<String, Object>) message.get("params");
            
            if ("subscribe".equals(command) && params != null) {
                List<String> channels = (List<String>) params.get("channels");
                List<String> marketTickers = (List<String>) params.get("market_tickers");
                String searchPrefix = (String) params.get("search_prefix");
                Integer maxMarkets = (Integer) params.get("max_markets");
                
                // Store subscription info
                SubscriptionInfo subInfo = new SubscriptionInfo();
                subInfo.sessionId = sessionId;
                subInfo.channels = channels;
                subInfo.searchPrefix = searchPrefix;
                subInfo.maxMarkets = maxMarkets != null ? maxMarkets : 50;
                
                if (marketTickers != null && !marketTickers.isEmpty()) {
                    subInfo.specificMarkets = marketTickers;
                } else if (searchPrefix != null && !searchPrefix.trim().isEmpty()) {
                    // Find markets matching the prefix
                    subInfo.matchedMarkets = orderBookManager.searchMarkets(searchPrefix, subInfo.maxMarkets);
                    log.debug("Found {} markets matching prefix '{}' for session {}", 
                        subInfo.matchedMarkets.size(), searchPrefix, sessionId);
                } else {
                    // Subscribe to all markets (limited)
                    subInfo.matchedMarkets = orderBookManager.getAllMarketTickers()
                        .stream()
                        .limit(subInfo.maxMarkets)
                        .toList();
                }
                
                sessionSubscriptions.put(sessionId, subInfo);
                
                // Send subscription confirmation
                Map<String, Object> response = Map.of(
                    "type", "subscription_response",
                    "status", "success",
                    "session_id", sessionId,
                    "subscribed_markets", subInfo.getEffectiveMarkets().size(),
                    "channels", channels
                );
                
                messagingTemplate.convertAndSendToUser(sessionId, "/admin/subscriptions", response);
                
                // Send initial data for subscribed markets
                sendInitialMarketData(sessionId, subInfo);
                
            } else if ("unsubscribe".equals(command)) {
                sessionSubscriptions.remove(sessionId);
                log.debug("Unsubscribed session: {}", sessionId);
            }
            
        } catch (Exception e) {
            log.error("Error handling admin subscription", e);
            
            String sessionId = headerAccessor.getSessionId();
            Map<String, Object> errorResponse = Map.of(
                "type", "subscription_error",
                "error", e.getMessage(),
                "session_id", sessionId
            );
            
            messagingTemplate.convertAndSendToUser(sessionId, "/admin/subscriptions", errorResponse);
        }
    }
    
    @MessageMapping("/admin/market_search")
    public void handleMarketSearch(@Payload Map<String, Object> message, SimpMessageHeaderAccessor headerAccessor) {
        try {
            String sessionId = headerAccessor.getSessionId();
            String query = (String) message.get("query");
            Integer limit = (Integer) message.get("limit");
            
            if (limit == null) limit = 20;
            
            List<String> suggestions = orderBookManager.getMarketSuggestions(query, limit);
            
            Map<String, Object> response = Map.of(
                "type", "market_search_response",
                "query", query,
                "suggestions", suggestions,
                "count", suggestions.size()
            );
            
            messagingTemplate.convertAndSendToUser(sessionId, "/admin/search", response);
            
        } catch (Exception e) {
            log.error("Error handling market search", e);
        }
    }
    
    /**
     * Called when order book updates are published
     */
    public void broadcastOrderBookUpdate(String marketTicker, Map<String, Object> orderBookData) {
        sessionSubscriptions.forEach((sessionId, subInfo) -> {
            if (subInfo.isSubscribedToMarket(marketTicker) && 
                subInfo.channels != null && 
                subInfo.channels.contains("orderbook_snapshot")) {
                
                Map<String, Object> message = Map.of(
                    "type", "orderbook_snapshot",
                    "market_ticker", marketTicker,
                    "data", orderBookData,
                    "timestamp", System.currentTimeMillis()
                );
                
                messagingTemplate.convertAndSendToUser(sessionId, "/admin/orderbook", message);
            }
        });
    }
    
    /**
     * Called when market data deltas are published
     */
    public void broadcastOrderBookDelta(String marketTicker, Map<String, Object> deltaData) {
        sessionSubscriptions.forEach((sessionId, subInfo) -> {
            if (subInfo.isSubscribedToMarket(marketTicker) && 
                subInfo.channels != null && 
                subInfo.channels.contains("orderbook_delta")) {
                
                Map<String, Object> message = Map.of(
                    "type", "orderbook_delta",
                    "market_ticker", marketTicker,
                    "data", deltaData,
                    "timestamp", System.currentTimeMillis()
                );
                
                messagingTemplate.convertAndSendToUser(sessionId, "/admin/orderbook", message);
            }
        });
    }
    
    private void sendInitialMarketData(String sessionId, SubscriptionInfo subInfo) {
        List<String> markets = subInfo.getEffectiveMarkets();
        
        for (String marketTicker : markets) {
            var orderBook = orderBookManager.getOrderBook(marketTicker);
            if (orderBook != null) {
                Map<String, Object> initialData = Map.of(
                    "type", "initial_orderbook",
                    "market_ticker", marketTicker,
                    "data", Map.of(
                        "marketTicker", marketTicker,
                        "status", orderBook.getMarketStatus(),
                        "lastUpdateTimestamp", orderBook.getLastUpdateTimestamp(),
                        "bestYes", orderBook.getBestYes(),
                        "bestNo", orderBook.getBestNo(),
                        "yesLevels", orderBook.getTopYes(10),
                        "noLevels", orderBook.getTopNo(10)
                    ),
                    "timestamp", System.currentTimeMillis()
                );
                
                messagingTemplate.convertAndSendToUser(sessionId, "/admin/orderbook", initialData);
            }
        }
    }
    
    private static class SubscriptionInfo {
        String sessionId;
        List<String> channels;
        List<String> specificMarkets;
        List<String> matchedMarkets;
        String searchPrefix;
        int maxMarkets = 50;
        
        List<String> getEffectiveMarkets() {
            if (specificMarkets != null && !specificMarkets.isEmpty()) {
                return specificMarkets;
            } else if (matchedMarkets != null) {
                return matchedMarkets;
            }
            return List.of();
        }
        
        boolean isSubscribedToMarket(String marketTicker) {
            return getEffectiveMarkets().contains(marketTicker);
        }
    }
}