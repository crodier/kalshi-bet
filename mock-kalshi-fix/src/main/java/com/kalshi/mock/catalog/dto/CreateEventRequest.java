package com.kalshi.mock.catalog.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/**
 * Request DTO for creating a new Event
 */
public class CreateEventRequest {
    
    @NotNull
    @Pattern(regexp = "^[A-Z0-9_]+$", message = "Event ticker must contain only uppercase letters, numbers, and underscores")
    @Size(min = 1, max = 50)
    @JsonProperty("event_ticker")
    private String eventTicker;
    
    @NotNull
    @JsonProperty("series_ticker")
    private String seriesTicker;
    
    @NotNull
    @Size(min = 1, max = 200)
    @JsonProperty("title")
    private String title;
    
    @JsonProperty("sub_title")
    private String subTitle;
    
    @NotNull
    @JsonProperty("category")
    private String category;
    
    @JsonProperty("mutually_exclusive")
    private Boolean mutuallyExclusive = false;
    
    @JsonProperty("yes_sub_title")
    private String yesSubTitle;
    
    @JsonProperty("no_sub_title")
    private String noSubTitle;
    
    @JsonProperty("expected_expiration_time")
    private LocalDateTime expectedExpirationTime;
    
    @JsonProperty("response_price_units")
    private String responsePriceUnits;
    
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
    
    public Boolean getMutuallyExclusive() {
        return mutuallyExclusive;
    }
    
    public void setMutuallyExclusive(Boolean mutuallyExclusive) {
        this.mutuallyExclusive = mutuallyExclusive;
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