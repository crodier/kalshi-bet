package com.kalshi.mock.catalog.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;

/**
 * Request DTO for filtering Markets
 */
public class MarketRequest extends CursorRequest {
    
    @JsonProperty("event_ticker")
    private String eventTicker;
    
    @JsonProperty("series_ticker")
    private String seriesTicker;
    
    @JsonProperty("status")
    private String status;
    
    @JsonProperty("ticker")
    private String ticker;
    
    @JsonProperty("ticker_starts_with")
    private String tickerStartsWith;
    
    @JsonProperty("min_close_ts")
    private LocalDateTime minCloseTs;
    
    @JsonProperty("max_close_ts")
    private LocalDateTime maxCloseTs;
    
    @JsonProperty("category")
    private String category;
    
    // Constructors
    public MarketRequest() {
    }
    
    // Getters and Setters
    public String getEventTicker() {
        return eventTicker;
    }
    
    public void setEventTicker(String eventTicker) {
        this.eventTicker = eventTicker;
    }
    
    public String getSeriesTicker() {
        return seriesTicker;
    }
    
    public void setSeriesTicker(String seriesTicker) {
        this.seriesTicker = seriesTicker;
    }
    
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
    
    public LocalDateTime getMinCloseTs() {
        return minCloseTs;
    }
    
    public void setMinCloseTs(LocalDateTime minCloseTs) {
        this.minCloseTs = minCloseTs;
    }
    
    public LocalDateTime getMaxCloseTs() {
        return maxCloseTs;
    }
    
    public void setMaxCloseTs(LocalDateTime maxCloseTs) {
        this.maxCloseTs = maxCloseTs;
    }
    
    public String getCategory() {
        return category;
    }
    
    public void setCategory(String category) {
        this.category = category;
    }
}