package com.kalshi.mock.catalog.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Paginated response for Market list
 */
public class MarketListResponse extends CursorResponse {
    
    @JsonProperty("markets")
    private List<MarketResponse> markets;
    
    // Constructors
    public MarketListResponse() {
    }
    
    public MarketListResponse(List<MarketResponse> markets, String cursor) {
        super(cursor);
        this.markets = markets;
    }
    
    // Getters and Setters
    public List<MarketResponse> getMarkets() {
        return markets;
    }
    
    public void setMarkets(List<MarketResponse> markets) {
        this.markets = markets;
    }
}