package com.kalshi.mock.websocket.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;

/**
 * WebSocket message for order state updates
 */
public class OrderUpdateMessage {
    
    @JsonProperty("order_id")
    private String orderId;
    
    @JsonProperty("user_id") 
    private String userId;
    
    @JsonProperty("market_ticker")
    private String marketTicker;
    
    @JsonProperty("side")
    private String side; // "yes" or "no"
    
    @JsonProperty("action")
    private String action; // "buy" or "sell"
    
    @JsonProperty("type")
    private String type; // "limit", "market"
    
    @JsonProperty("original_quantity")
    private int originalQuantity;
    
    @JsonProperty("filled_quantity")
    private int filledQuantity;
    
    @JsonProperty("remaining_quantity")
    private int remainingQuantity;
    
    @JsonProperty("price")
    private int price; // in cents
    
    @JsonProperty("avg_fill_price")
    private Integer avgFillPrice; // in cents, nullable
    
    @JsonProperty("status")
    private String status; // "open", "partially_filled", "filled", "canceled"
    
    @JsonProperty("time_in_force")
    private String timeInForce; // "GTC", "IOC", "FOK"
    
    @JsonProperty("created_time")
    private LocalDateTime createdTime;
    
    @JsonProperty("updated_time")
    private LocalDateTime updatedTime;
    
    @JsonProperty("update_type")
    private String updateType; // "NEW", "UPDATE", "FILL", "CANCEL"
    
    // Default constructor
    public OrderUpdateMessage() {}
    
    // All-args constructor
    public OrderUpdateMessage(String orderId, String userId, String marketTicker, 
                            String side, String action, String type, 
                            int originalQuantity, int filledQuantity, int remainingQuantity,
                            int price, Integer avgFillPrice, String status, 
                            String timeInForce, LocalDateTime createdTime, 
                            LocalDateTime updatedTime, String updateType) {
        this.orderId = orderId;
        this.userId = userId;
        this.marketTicker = marketTicker;
        this.side = side;
        this.action = action;
        this.type = type;
        this.originalQuantity = originalQuantity;
        this.filledQuantity = filledQuantity;
        this.remainingQuantity = remainingQuantity;
        this.price = price;
        this.avgFillPrice = avgFillPrice;
        this.status = status;
        this.timeInForce = timeInForce;
        this.createdTime = createdTime;
        this.updatedTime = updatedTime;
        this.updateType = updateType;
    }
    
    // Getters and setters
    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }
    
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    
    public String getMarketTicker() { return marketTicker; }
    public void setMarketTicker(String marketTicker) { this.marketTicker = marketTicker; }
    
    public String getSide() { return side; }
    public void setSide(String side) { this.side = side; }
    
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    
    public int getOriginalQuantity() { return originalQuantity; }
    public void setOriginalQuantity(int originalQuantity) { this.originalQuantity = originalQuantity; }
    
    public int getFilledQuantity() { return filledQuantity; }
    public void setFilledQuantity(int filledQuantity) { this.filledQuantity = filledQuantity; }
    
    public int getRemainingQuantity() { return remainingQuantity; }
    public void setRemainingQuantity(int remainingQuantity) { this.remainingQuantity = remainingQuantity; }
    
    public int getPrice() { return price; }
    public void setPrice(int price) { this.price = price; }
    
    public Integer getAvgFillPrice() { return avgFillPrice; }
    public void setAvgFillPrice(Integer avgFillPrice) { this.avgFillPrice = avgFillPrice; }
    
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    
    public String getTimeInForce() { return timeInForce; }
    public void setTimeInForce(String timeInForce) { this.timeInForce = timeInForce; }
    
    public LocalDateTime getCreatedTime() { return createdTime; }
    public void setCreatedTime(LocalDateTime createdTime) { this.createdTime = createdTime; }
    
    public LocalDateTime getUpdatedTime() { return updatedTime; }
    public void setUpdatedTime(LocalDateTime updatedTime) { this.updatedTime = updatedTime; }
    
    public String getUpdateType() { return updateType; }
    public void setUpdateType(String updateType) { this.updateType = updateType; }
    
    @Override
    public String toString() {
        return String.format("OrderUpdateMessage{orderId='%s', userId='%s', marketTicker='%s', " +
                           "side='%s', action='%s', status='%s', updateType='%s', " +
                           "originalQuantity=%d, filledQuantity=%d, remainingQuantity=%d, " +
                           "price=%d, avgFillPrice=%s}",
                orderId, userId, marketTicker, side, action, status, updateType,
                originalQuantity, filledQuantity, remainingQuantity, price, avgFillPrice);
    }
}