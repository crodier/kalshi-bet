package com.kalshi.mock.websocket.config;

import com.kalshi.mock.event.OrderBookEventPublisher;
import com.kalshi.mock.websocket.service.StompBroadcastService;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;

@Configuration
public class StompBroadcastConfiguration {
    
    @Autowired
    private OrderBookEventPublisher eventPublisher;
    
    @Autowired
    private StompBroadcastService stompBroadcastService;
    
    @PostConstruct
    public void registerStompBroadcastService() {
        eventPublisher.addListener(stompBroadcastService);
    }
}