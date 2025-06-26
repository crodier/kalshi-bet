package com.kalshi.mock.catalog.model;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Represents a Series in the Kalshi platform.
 * A Series is a collection of related events that share common properties.
 */
public class Series {
    
    private String ticker;
    
    private String frequency;
    
    private String title;
    
    private String category;
    
    private List<String> tags;
    
    private List<String> settlementSources;
    
    private String contractUrl;
    
    private String feeType;
    
    private Double feeMultiplier;
    
    private LocalDateTime createdAt;
    
    private LocalDateTime updatedAt;
    
    // Constructors
    public Series() {
    }
    
    public Series(String ticker, String frequency, String title, String category, 
                  String feeType, Double feeMultiplier) {
        this.ticker = ticker;
        this.frequency = frequency;
        this.title = title;
        this.category = category;
        this.feeType = feeType;
        this.feeMultiplier = feeMultiplier;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }
    
    // Getters and Setters
    public String getTicker() {
        return ticker;
    }
    
    public void setTicker(String ticker) {
        this.ticker = ticker;
    }
    
    public String getFrequency() {
        return frequency;
    }
    
    public void setFrequency(String frequency) {
        this.frequency = frequency;
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
    
    public List<String> getTags() {
        return tags;
    }
    
    public void setTags(List<String> tags) {
        this.tags = tags;
    }
    
    public List<String> getSettlementSources() {
        return settlementSources;
    }
    
    public void setSettlementSources(List<String> settlementSources) {
        this.settlementSources = settlementSources;
    }
    
    public String getContractUrl() {
        return contractUrl;
    }
    
    public void setContractUrl(String contractUrl) {
        this.contractUrl = contractUrl;
    }
    
    public String getFeeType() {
        return feeType;
    }
    
    public void setFeeType(String feeType) {
        this.feeType = feeType;
    }
    
    public Double getFeeMultiplier() {
        return feeMultiplier;
    }
    
    public void setFeeMultiplier(Double feeMultiplier) {
        this.feeMultiplier = feeMultiplier;
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
    
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
    
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }
}