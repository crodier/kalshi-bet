package com.kalshi.orderbook.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kalshi.orderbook.dto.OrderBookSnapshot;
import com.fbg.api.kalshi.InternalOrderBook;
import com.fbg.api.kalshi.PriceLevel;
import com.kalshi.orderbook.websocket.WebSocketSession;
import com.kalshi.orderbook.websocket.WebSocketSessionManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderBookUpdatePublisher {
    
    private final WebSocketSessionManager sessionManager;
    private final ObjectMapper objectMapper;
    
    // Track last top of book for each market to detect changes
    private final Map<String, TopOfBook> lastTopOfBook = new ConcurrentHashMap<>();
    
    public void publishOrderBookUpdate(InternalOrderBook orderBook) {
        String marketTicker = orderBook.getMarketTicker();
        
        // Get current top of book
        PriceLevel currentBestYes = orderBook.getBestYesLevel().orElse(null);
        PriceLevel currentBestNo = orderBook.getBestNoLevel().orElse(null);
        
        // Check if top of book has changed
        TopOfBook currentTop = new TopOfBook(
            currentBestYes != null ? (double)currentBestYes.getPrice() : null,
            currentBestYes != null ? currentBestYes.getQuantity() : null,
            currentBestNo != null ? (double)currentBestNo.getPrice() : null,
            currentBestNo != null ? currentBestNo.getQuantity() : null
        );
        
        TopOfBook lastTop = lastTopOfBook.get(marketTicker);
        boolean topOfBookChanged = !currentTop.equals(lastTop);
        
        if (topOfBookChanged) {
            lastTopOfBook.put(marketTicker, currentTop);
        }
        
        // Get sessions subscribed to this market
        var sessions = sessionManager.getSessionsForMarket(marketTicker);
        
        for (WebSocketSession session : sessions) {
            try {
                // Only send if top of book changed or if subscribed to all changes
                if (topOfBookChanged || session.isSubscribeToAllChanges()) {
                    Map<String, Object> update = Map.of(
                        "type", "orderbook_update",
                        "market", marketTicker,
                        "bestYes", currentBestYes != null ? Map.of(
                            "price", currentBestYes.getPrice(),
                            "size", currentBestYes.getQuantity()
                        ) : null,
                        "bestNo", currentBestNo != null ? Map.of(
                            "price", currentBestNo.getPrice(),
                            "size", currentBestNo.getQuantity()
                        ) : null,
                        "timestamp", orderBook.getLastUpdateTimestamp(),
                        "topChanged", topOfBookChanged
                    );
                    
                    String json = objectMapper.writeValueAsString(update);
                    session.getSession().sendMessage(new TextMessage(json));
                }
            } catch (Exception e) {
                log.error("Failed to send update to session: {}", session.getSessionId(), e);
            }
        }
    }
    
    private record TopOfBook(Double bidPrice, Long bidQuantity, Double askPrice, Long askQuantity) {}
}