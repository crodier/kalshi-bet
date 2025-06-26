package com.kalshi.mock.catalog.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Request DTO for filtering Series
 */
public class SeriesRequest extends CursorRequest {
    
    @JsonProperty("status")
    private String status;
    
    @JsonProperty("ticker")
    private String ticker;
    
    @JsonProperty("ticker_starts_with")
    private String tickerStartsWith;
    
    // Constructors
    public SeriesRequest() {
    }
    
    // Getters and Setters
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
    }
    
    public String getTicker() {
        return ticker;
    }
    
    public void setTicker(String ticker) {
        this.ticker = ticker;
    }
    
    public String getTickerStartsWith() {
        return tickerStartsWith;
    }
    
    public void setTickerStartsWith(String tickerStartsWith) {
        this.tickerStartsWith = tickerStartsWith;
    }
}