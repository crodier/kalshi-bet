package com.kalshi.marketdata.config;

import com.kalshi.marketdata.websocket.AdminWebSocketHandler;
import com.kalshi.marketdata.websocket.MarketDataWebSocketHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {
    
    @Autowired
    private MarketDataWebSocketHandler marketDataWebSocketHandler;
    
    @Autowired
    private AdminWebSocketHandler adminWebSocketHandler;
    
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(marketDataWebSocketHandler, "/ws/market-data")
                .setAllowedOrigins("*");
        
        registry.addHandler(adminWebSocketHandler, "/ws/admin")
                .setAllowedOrigins("*");
    }
    
    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(RedisConnectionFactory connectionFactory) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        return container;
    }
}