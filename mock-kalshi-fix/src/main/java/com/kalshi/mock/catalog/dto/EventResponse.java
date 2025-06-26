package com.kalshi.mock.catalog.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Response DTO for Event matching Kalshi's API structure
 */
public class EventResponse {
    
    @JsonProperty("event_ticker")
    private String eventTicker;
    
    @JsonProperty("series_ticker")
    private String seriesTicker;
    
    @JsonProperty("title")
    private String title;
    
    @JsonProperty("sub_title")
    private String subTitle;
    
    @JsonProperty("category")
    private String category;
    
    @JsonProperty("status")
    private String status;
    
    @JsonProperty("mutually_exclusive")
    private Boolean mutuallyExclusive;
    
    @JsonProperty("markets")
    private List<MarketResponse> markets;
    
    @JsonProperty("yes_sub_title")
    private String yesSubTitle;
    
    @JsonProperty("no_sub_title")
    private String noSubTitle;
    
    @JsonProperty("expected_expiration_time")
    private LocalDateTime expectedExpirationTime;
    
    @JsonProperty("response_price_units")
    private String responsePriceUnits;
    
    // Constructors
    public EventResponse() {
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
    
    public String getTitle() {
        return title;
    }
    
    public void setTitle(String title) {
        this.title = title;
    }
    
    public String getSubTitle() {
        return subTitle;
    }
    
    public void setSubTitle(String subTitle) {
        this.subTitle = subTitle;
    }
    
    public String getCategory() {
        return category;
    }
    
    public void setCategory(String category) {
        this.category = category;
    }
    
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
    }
    
    public Boolean getMutuallyExclusive() {
        return mutuallyExclusive;
    }
    
    public void setMutuallyExclusive(Boolean mutuallyExclusive) {
        this.mutuallyExclusive = mutuallyExclusive;
    }
    
    public List<MarketResponse> getMarkets() {
        return markets;
    }
    
    public void setMarkets(List<MarketResponse> markets) {
        this.markets = markets;
    }
    
    public String getYesSubTitle() {
        return yesSubTitle;
    }
    
    public void setYesSubTitle(String yesSubTitle) {
        this.yesSubTitle = yesSubTitle;
    }
    
    public String getNoSubTitle() {
        return noSubTitle;
    }
    
    public void setNoSubTitle(String noSubTitle) {
        this.noSubTitle = noSubTitle;
    }
    
    public LocalDateTime getExpectedExpirationTime() {
        return expectedExpirationTime;
    }
    
    public void setExpectedExpirationTime(LocalDateTime expectedExpirationTime) {
        this.expectedExpirationTime = expectedExpirationTime;
    }
    
    public String getResponsePriceUnits() {
        return responsePriceUnits;
    }
    
    public void setResponsePriceUnits(String responsePriceUnits) {
        this.responsePriceUnits = responsePriceUnits;
    }
}