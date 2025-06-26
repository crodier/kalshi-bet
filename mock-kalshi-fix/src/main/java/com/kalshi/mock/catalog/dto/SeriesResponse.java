package com.kalshi.mock.catalog.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Response DTO for Series matching Kalshi's API structure
 */
public class SeriesResponse {
    
    @JsonProperty("ticker")
    private String ticker;
    
    @JsonProperty("frequency")
    private String frequency;
    
    @JsonProperty("title")
    private String title;
    
    @JsonProperty("category")
    private String category;
    
    @JsonProperty("tags")
    private List<String> tags;
    
    @JsonProperty("settlement_sources")
    private List<String> settlementSources;
    
    @JsonProperty("contract_url")
    private String contractUrl;
    
    @JsonProperty("fee_rate_bps")
    private Integer feeRateBps;
    
    // Constructors
    public SeriesResponse() {
    }
    
    public SeriesResponse(String ticker, String frequency, String title, String category) {
        this.ticker = ticker;
        this.frequency = frequency;
        this.title = title;
        this.category = category;
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
    
    public Integer getFeeRateBps() {
        return feeRateBps;
    }
    
    public void setFeeRateBps(Integer feeRateBps) {
        this.feeRateBps = feeRateBps;
    }
}