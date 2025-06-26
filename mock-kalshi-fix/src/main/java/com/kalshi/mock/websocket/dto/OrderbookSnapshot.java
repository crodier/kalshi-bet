package com.kalshi.mock.websocket.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public class OrderbookSnapshot {
    
    @JsonProperty("market_ticker")
    private String marketTicker;
    
    private List<List<Integer>> yes;  // [[price, size], ...]
    private List<List<Integer>> no;   // [[price, size], ...]
    
    // Constructors
    public OrderbookSnapshot() {}
    
    public OrderbookSnapshot(String marketTicker, List<List<Integer>> yes, List<List<Integer>> no) {
        this.marketTicker = marketTicker;
        this.yes = yes;
        this.no = no;
    }
    
    // Getters and Setters
    public String getMarketTicker() {
        return marketTicker;
    }
    
    public void setMarketTicker(String marketTicker) {
        this.marketTicker = marketTicker;
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