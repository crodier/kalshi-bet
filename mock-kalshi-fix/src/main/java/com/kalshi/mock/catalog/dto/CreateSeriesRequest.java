package com.kalshi.mock.catalog.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Request DTO for creating a new Series
 */
public class CreateSeriesRequest {
    
    @NotNull
    @Pattern(regexp = "^[A-Z0-9_]+$", message = "Ticker must contain only uppercase letters, numbers, and underscores")
    @Size(min = 1, max = 20)
    @JsonProperty("ticker")
    private String ticker;
    
    @NotNull
    @JsonProperty("frequency")
    private String frequency;
    
    @NotNull
    @Size(min = 1, max = 200)
    @JsonProperty("title")
    private String title;
    
    @NotNull
    @JsonProperty("category")
    private String category;
    
    @JsonProperty("tags")
    private List<String> tags;
    
    @JsonProperty("settlement_sources")
    private List<String> settlementSources;
    
    @JsonProperty("contract_url")
    private String contractUrl;
    
    @NotNull
    @JsonProperty("fee_type")
    private String feeType;
    
    @NotNull
    @JsonProperty("fee_multiplier")
    private Double feeMultiplier;
    
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
}