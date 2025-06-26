package com.kalshi.mock.model;

import com.fbg.api.market.KalshiSide;
import lombok.Getter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Represents a single order in the order book
 */
@ToString
@Slf4j
public class OrderBookEntry {
    private static final AtomicLong SEQUENCE_GENERATOR = new AtomicLong(0);

    // Getters
    @Getter
    private final String orderId;
    @Getter
    private final String userId;
    @Getter
    private final KalshiSide side;  // YES or NO
    @Getter
    private final String action; // buy or sell
    @Getter
    private final int price; // in cents
    @Getter
    private volatile int quantity;
    @Getter
    private final int originalQuantity;
    @Getter
    private final long timestamp;
    @Getter
    private final long sequence; // for FIFO ordering
    
    // Normalized values for internal order book representation
    @Getter
    private final int normalizedPrice;
    private final boolean normalizedIsBuy;
    
    public OrderBookEntry(String orderId, String userId, KalshiSide side, String action, 
                         int price, int quantity, long timestamp) {
        // Validate price is between 1 and 99 cents
        if (price < 1 || price > 99) {
            throw new IllegalArgumentException("Price must be between 1 and 99 cents, got: " + price);
        }
        
        this.orderId = orderId;
        this.userId = userId;
        this.side = side;
        this.action = action;
        this.price = price;
        this.quantity = quantity;
        this.originalQuantity = quantity;
        this.timestamp = timestamp;
        this.sequence = SEQUENCE_GENERATOR.incrementAndGet();
        
        // Apply NO/YES conversion for order book normalization
        if (side == KalshiSide.no) {
            if (action.equals("buy")) {
                // Buy NO @ P → Sell YES @ (100 - P)
                this.normalizedPrice = 100 - price;
                this.normalizedIsBuy = false;
            } else {
                // Sell NO @ P → Buy YES @ (100 - P)
                this.normalizedPrice = 100 - price;
                this.normalizedIsBuy = true;
            }
        } else {
            // YES orders remain as-is
            this.normalizedPrice = price;
            this.normalizedIsBuy = action.equals("buy");
        }
    }
    
    public void reduceQuantity(int amount) {
        if (amount > quantity) {
            log.error("Attempted to reduce quantity by " + amount + " from " + quantity + " for order " + orderId + ", ignoring request");
            throw new RuntimeException("Attempted to reduce quantity by " + amount + " from " + quantity + " for order " + orderId + ", ignoring request");
        }

        int newQty = quantity - amount;
        log.info("Reducing quantity for order " + orderId + " by " + amount + " to " + newQty+", due to execution.");

        this.quantity = newQty;
    }

    public boolean isNormalizedBuy() { return normalizedIsBuy; }
    
    public int getFilledQuantity() {
        return originalQuantity - quantity;
    }
}