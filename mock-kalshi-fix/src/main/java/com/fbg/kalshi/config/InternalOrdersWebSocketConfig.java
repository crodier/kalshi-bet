package com.fbg.kalshi.config;

import com.fbg.kalshi.websocket.InternalOrdersWebSocketHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * Configuration for the internal orders WebSocket endpoint.
 * This endpoint is separate from the public market data WebSocket.
 */
@Configuration
@EnableWebSocket
public class InternalOrdersWebSocketConfig implements WebSocketConfigurer {
    
    @Autowired
    private InternalOrdersWebSocketHandler internalOrdersWebSocketHandler;
    
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(internalOrdersWebSocketHandler, "/trade-api/ws/internal-orders")
                .setAllowedOrigins("*"); // In production, restrict to specific origins
    }
}