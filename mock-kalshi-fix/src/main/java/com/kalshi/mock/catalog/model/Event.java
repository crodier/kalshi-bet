package com.kalshi.mock.catalog.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents an Event in the Kalshi platform.
 * An Event belongs to a Series and contains one or more Markets.
 */
public class Event {
    
    private String eventTicker;
    
    private String seriesTicker;
    
    private String title;
    
    private String category;
    
    private EventStatus status;
    
    private Boolean mutuallyExclusive;
    
    private List<Market> markets = new ArrayList<>();
    
    private LocalDateTime createdAt;
    
    private LocalDateTime updatedAt;
    
    // Additional fields from Kalshi API
    private String subTitle;
    
    private String yesSubTitle;
    
    private String noSubTitle;
    
    private LocalDateTime expectedExpirationTime;
    
    private String responsePriceUnits;
    
    // Constructors
    public Event() {
    }
    
    public Event(String eventTicker, String seriesTicker, String title, String category, 
                 EventStatus status, Boolean mutuallyExclusive) {
        this.eventTicker = eventTicker;
        this.seriesTicker = seriesTicker;
        this.title = title;
        this.category = category;
        this.status = status;
        this.mutuallyExclusive = mutuallyExclusive;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
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
    
    public String getCategory() {
        return category;
    }
    
    public void setCategory(String category) {
        this.category = category;
    }
    
    public EventStatus getStatus() {
        return status;
    }
    
    public void setStatus(EventStatus status) {
        this.status = status;
    }
    
    public Boolean getMutuallyExclusive() {
        return mutuallyExclusive;
    }
    
    public void setMutuallyExclusive(Boolean mutuallyExclusive) {
        this.mutuallyExclusive = mutuallyExclusive;
    }
    
    public List<Market> getMarkets() {
        return markets;
    }
    
    public void setMarkets(List<Market> markets) {
        this.markets = markets;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
    
    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
    
    public String getSubTitle() {
        return subTitle;
    }
    
    public void setSubTitle(String subTitle) {
        this.subTitle = subTitle;
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
    
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
    
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }
    
    // Enum for Event Status
    public enum EventStatus {
        OPEN,
        CLOSED,
        SETTLED,
        PENDING
    }
}