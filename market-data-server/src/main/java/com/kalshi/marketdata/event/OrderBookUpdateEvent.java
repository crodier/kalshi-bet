package com.kalshi.marketdata.event;

import org.springframework.context.ApplicationEvent;

import java.util.Map;

/**
 * Event class for order book updates to notify admin WebSocket clients
 */
public class OrderBookUpdateEvent extends ApplicationEvent {
    private final String marketTicker;
    private final Map<String, Object> orderBookData;
    
    public OrderBookUpdateEvent(Object source, String marketTicker, Map<String, Object> orderBookData) {
        super(source);
        this.marketTicker = marketTicker;
        this.orderBookData = orderBookData;
    }
    
    public String getMarketTicker() {
        return marketTicker;
    }
    
    public Map<String, Object> getOrderBookData() {
        return orderBookData;
    }
}