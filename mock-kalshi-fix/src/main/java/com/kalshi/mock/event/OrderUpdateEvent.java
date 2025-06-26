package com.kalshi.mock.event;

import com.kalshi.mock.websocket.dto.OrderUpdateMessage;

/**
 * Event published when an order state changes
 */
public class OrderUpdateEvent {
    
    public enum OrderUpdateType {
        NEW,        // Order created
        UPDATE,     // Order modified
        FILL,       // Order filled (partial or complete)
        CANCEL      // Order cancelled
    }
    
    private final OrderUpdateMessage orderUpdate;
    private final OrderUpdateType updateType;
    
    public OrderUpdateEvent(OrderUpdateMessage orderUpdate, OrderUpdateType updateType) {
        this.orderUpdate = orderUpdate;
        this.updateType = updateType;
    }
    
    public OrderUpdateMessage getOrderUpdate() {
        return orderUpdate;
    }
    
    public OrderUpdateType getUpdateType() {
        return updateType;
    }
    
    public String getMarketTicker() {
        return orderUpdate.getMarketTicker();
    }
    
    public String getUserId() {
        return orderUpdate.getUserId();
    }
    
    public String getOrderId() {
        return orderUpdate.getOrderId();
    }
    
    @Override
    public String toString() {
        return String.format("OrderUpdateEvent{updateType=%s, orderUpdate=%s}", 
                           updateType, orderUpdate);
    }
}