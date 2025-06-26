package com.kalshi.orderbook.websocket;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class AdminWebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // Enable a simple memory-based message broker
        config.enableSimpleBroker("/topic", "/admin");
        
        // Set application destination prefix for admin messages
        config.setApplicationDestinationPrefixes("/admin", "/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Admin WebSocket endpoint
        registry.addEndpoint("/admin/ws/v2")
                .setAllowedOriginPatterns("*")
                .withSockJS();
        
        // Keep the existing endpoint as well
        registry.addEndpoint("/trade-api/ws/v2")
                .setAllowedOriginPatterns("*")
                .withSockJS();
    }
}