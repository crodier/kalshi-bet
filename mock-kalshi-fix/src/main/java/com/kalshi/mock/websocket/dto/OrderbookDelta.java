package com.kalshi.mock.websocket.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class OrderbookDelta {
    
    @JsonProperty("market_ticker")
    private String marketTicker;
    
    private Integer price;
    private Integer delta;  // Positive = increase, Negative = decrease, 0 = remove
    private String side;    // "yes" or "no"
    
    // Constructors
    public OrderbookDelta() {}
    
    public OrderbookDelta(String marketTicker, Integer price, Integer delta, String side) {
        this.marketTicker = marketTicker;
        this.price = price;
        this.delta = delta;
        this.side = side;
    }
    
    // Getters and Setters
    public String getMarketTicker() {
        return marketTicker;
    }
    
    public void setMarketTicker(String marketTicker) {
        this.marketTicker = marketTicker;
    }
    
    public Integer getPrice() {
        return price;
    }
    
    public void setPrice(Integer price) {
        this.price = price;
    }
    
    public Integer getDelta() {
        return delta;
    }
    
    public void setDelta(Integer delta) {
        this.delta = delta;
    }
    
    public String getSide() {
        return side;
    }
    
    public void setSide(String side) {
        this.side = side;
    }
}