package com.fbg.kalshi.websocket.message;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

/**
 * WebSocket message for order updates sent via the internal orders WebSocket.
 */
public class OrderUpdateMessage {
    private final String type;
    private final OrderUpdate msg;
    
    public OrderUpdateMessage(String type,
                            String orderId,
                            String userId,
                            String marketTicker,
                            String side,
                            String action,
                            String orderType,
                            Integer originalQuantity,
                            Integer filledQuantity,
                            Integer remainingQuantity,
                            Integer price,
                            Integer avgFillPrice,
                            String status,
                            String timeInForce,
                            Instant createdTime,
                            Instant updatedTime,
                            String updateType) {
        this.type = type;
        this.msg = new OrderUpdate(
            orderId, userId, marketTicker, side, action, orderType,
            originalQuantity, filledQuantity, remainingQuantity,
            price, avgFillPrice, status, timeInForce,
            createdTime, updatedTime, updateType
        );
    }
    
    public String getType() {
        return type;
    }
    
    public OrderUpdate getMsg() {
        return msg;
    }
    
    public static class OrderUpdate {
        @JsonProperty("order_id")
        private final String orderId;
        
        @JsonProperty("user_id")
        private final String userId;
        
        @JsonProperty("market_ticker")
        private final String marketTicker;
        
        private final String side;
        private final String action;
        
        @JsonProperty("type")
        private final String orderType;
        
        @JsonProperty("original_quantity")
        private final Integer originalQuantity;
        
        @JsonProperty("filled_quantity")
        private final Integer filledQuantity;
        
        @JsonProperty("remaining_quantity")
        private final Integer remainingQuantity;
        
        private final Integer price;
        
        @JsonProperty("avg_fill_price")
        private final Integer avgFillPrice;
        
        private final String status;
        
        @JsonProperty("time_in_force")
        private final String timeInForce;
        
        @JsonProperty("created_time")
        private final Instant createdTime;
        
        @JsonProperty("updated_time")
        private final Instant updatedTime;
        
        @JsonProperty("update_type")
        private final String updateType;
        
        public OrderUpdate(String orderId, String userId, String marketTicker,
                         String side, String action, String orderType,
                         Integer originalQuantity, Integer filledQuantity,
                         Integer remainingQuantity, Integer price, Integer avgFillPrice,
                         String status, String timeInForce,
                         Instant createdTime, Instant updatedTime, String updateType) {
            this.orderId = orderId;
            this.userId = userId;
            this.marketTicker = marketTicker;
            this.side = side;
            this.action = action;
            this.orderType = orderType;
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
        
        // Getters
        public String getOrderId() { return orderId; }
        public String getUserId() { return userId; }
        public String getMarketTicker() { return marketTicker; }
        public String getSide() { return side; }
        public String getAction() { return action; }
        public String getOrderType() { return orderType; }
        public Integer getOriginalQuantity() { return originalQuantity; }
        public Integer getFilledQuantity() { return filledQuantity; }
        public Integer getRemainingQuantity() { return remainingQuantity; }
        public Integer getPrice() { return price; }
        public Integer getAvgFillPrice() { return avgFillPrice; }
        public String getStatus() { return status; }
        public String getTimeInForce() { return timeInForce; }
        public Instant getCreatedTime() { return createdTime; }
        public Instant getUpdatedTime() { return updatedTime; }
        public String getUpdateType() { return updateType; }
    }
}