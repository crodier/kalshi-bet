package com.kalshi.mock.websocket.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class FillMessage {
    
    @JsonProperty("order_id")
    private String orderId;
    
    @JsonProperty("market_ticker")
    private String marketTicker;
    
    private Integer price;
    private Integer count;
    private String side;  // "yes" or "no"
    private String action;  // "buy" or "sell"
    
    @JsonProperty("is_taker")
    private Boolean isTaker;
    
    @JsonProperty("created_time")
    private String createdTime;
    
    @JsonProperty("trade_id")
    private String tradeId;
    
    // Constructors
    public FillMessage() {}
    
    // Getters and Setters
    public String getOrderId() {
        return orderId;
    }
    
    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }
    
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
    
    public String getAction() {
        return action;
    }
    
    public void setAction(String action) {
        this.action = action;
    }
    
    public Boolean getIsTaker() {
        return isTaker;
    }
    
    public void setIsTaker(Boolean isTaker) {
        this.isTaker = isTaker;
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