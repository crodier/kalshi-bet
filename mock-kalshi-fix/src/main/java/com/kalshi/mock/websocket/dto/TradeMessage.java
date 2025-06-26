package com.kalshi.mock.websocket.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class TradeMessage {
    
    @JsonProperty("market_ticker")
    private String marketTicker;
    
    private Integer price;
    private Integer count;
    private String side;  // "yes" or "no"
    
    @JsonProperty("created_time")
    private String createdTime;
    
    @JsonProperty("trade_id")
    private String tradeId;
    
    // Constructors
    public TradeMessage() {}
    
    public TradeMessage(String marketTicker, Integer price, Integer count, String side, String createdTime, String tradeId) {
        this.marketTicker = marketTicker;
        this.price = price;
        this.count = count;
        this.side = side;
        this.createdTime = createdTime;
        this.tradeId = tradeId;
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
    
    public Integer getCount() {
        return count;
    }
    
    public void setCount(Integer count) {
        this.count = count;
    }
    
    public String getSide() {
        return side;
    }
    
    public void setSide(String side) {
        this.side = side;
    }
    
    public String getCreatedTime() {
        return createdTime;
    }
    
    public void setCreatedTime(String createdTime) {
        this.createdTime = createdTime;
    }
    
    public String getTradeId() {
        return tradeId;
    }
    
    public void setTradeId(String tradeId) {
        this.tradeId = tradeId;
    }
}