package com.kalshi.mock.catalog.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Request DTO for filtering Events
 */
public class EventRequest extends CursorRequest {
    
    @JsonProperty("with_nested_markets")
    private Boolean withNestedMarkets = false;
    
    @JsonProperty("status")
    private String status;
    
    @JsonProperty("series_ticker")
    private String seriesTicker;
    
    @JsonProperty("ticker")
    private String ticker;
    
    @JsonProperty("ticker_starts_with")
    private String tickerStartsWith;
    
    // Constructors
    public EventRequest() {
    }
    
    // Getters and Setters
    public Boolean getWithNestedMarkets() {
        return withNestedMarkets;
    }
    
    public void setWithNestedMarkets(Boolean withNestedMarkets) {
        this.withNestedMarkets = withNestedMarkets;
    }
    
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
    }
    
    public String getSeriesTicker() {
        return seriesTicker;
    }
    
    public void setSeriesTicker(String seriesTicker) {
        this.seriesTicker = seriesTicker;
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