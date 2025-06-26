package com.kalshi.orderbook.config;

import com.kalshi.orderbook.websocket.KalshiWebSocketHandler;
import com.kalshi.orderbook.websocket.OrderBookWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {
    
    private final KalshiWebSocketHandler kalshiWebSocketHandler;
    private final OrderBookWebSocketHandler orderBookWebSocketHandler;
    
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // Kalshi-compatible WebSocket endpoint
        registry.addHandler(kalshiWebSocketHandler, "/trade-api/ws/v2")
            .setAllowedOrigins("*"); // Configure appropriately for production
            
        // Legacy endpoint for backward compatibility
        registry.addHandler(orderBookWebSocketHandler, "/ws/orderbook")
            .setAllowedOrigins("*");
    }
}