package com.kalshi.mock.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Response structure for orderbook endpoint that matches Kalshi's API exactly.
 * 
 * Example:
 * {
 *   "orderbook": {
 *     "yes": [[45, 100], [44, 200]],  // Buy YES orders (bids) - sorted high to low
 *     "no": [[55, 150], [56, 300]]    // Buy NO orders - sorted low to high
 *   }
 * }
 * 
 * Each inner array is [price_in_cents, quantity]
 */
public class OrderbookResponse {
    
    @JsonProperty("orderbook")
    private OrderbookData orderbook;
    
    public OrderbookResponse() {}
    
    public OrderbookResponse(OrderbookData orderbook) {
        this.orderbook = orderbook;
    }
    
    public OrderbookData getOrderbook() {
        return orderbook;
    }
    
    public void setOrderbook(OrderbookData orderbook) {
        this.orderbook = orderbook;
    }
    
    public static class OrderbookData {
        @JsonProperty("yes")
        private List<List<Integer>> yes;
        
        @JsonProperty("no")
        private List<List<Integer>> no;
        
        public OrderbookData() {}
        
        public OrderbookData(List<List<Integer>> yes, List<List<Integer>> no) {
            this.yes = yes;
            this.no = no;
        }
        
        public List<List<Integer>> getYes() {
            return yes;
        }
        
        public void setYes(List<List<Integer>> yes) {
            this.yes = yes;
        }
        
        public List<List<Integer>> getNo() {
            return no;
        }
        
        public void setNo(List<List<Integer>> no) {
            this.no = no;
        }
    }
}