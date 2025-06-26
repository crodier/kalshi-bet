package com.kalshi.mock.catalog.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/**
 * Request DTO for creating a new Market
 */
public class CreateMarketRequest {
    
    @NotNull
    @Pattern(regexp = "^[A-Z0-9_-]+$", message = "Market ticker must contain only uppercase letters, numbers, underscores, and hyphens")
    @Size(min = 1, max = 100)
    @JsonProperty("ticker")
    private String ticker;
    
    @NotNull
    @JsonProperty("event_ticker")
    private String eventTicker;
    
    @NotNull
    @JsonProperty("market_type")
    private String marketType;
    
    @NotNull
    @Size(min = 1, max = 200)
    @JsonProperty("title")
    private String title;
    
    @JsonProperty("subtitle")
    private String subtitle;
    
    @JsonProperty("yes_subtitle")
    private String yesSubtitle;
    
    @JsonProperty("no_subtitle")
    private String noSubtitle;
    
    @NotNull
    @JsonProperty("open_time")
    private LocalDateTime openTime;
    
    @NotNull
    @JsonProperty("close_time")
    private LocalDateTime closeTime;
    
    @JsonProperty("expected_expiration_time")
    private LocalDateTime expectedExpirationTime;
    
    @JsonProperty("risk_limit_cents")
    private Integer riskLimitCents;
    
    @JsonProperty("strike_type")
    private String strikeType;
    
    @JsonProperty("floor_strike")
    private Double floorStrike;
    
    @JsonProperty("cap_strike")
    private Double capStrike;
    
    @JsonProperty("can_close_early")
    private Boolean canCloseEarly = false;
    
    @JsonProperty("category")
    private String category;
    
    @JsonProperty("rules_primary")
    private String rulesPrimary;
    
    @JsonProperty("rules_secondary")
    private String rulesSecondary;
    
    @JsonProperty("response_price_units")
    private String responsePriceUnits;
    
    @JsonProperty("settlement_source")
    private String settlementSource;
    
    @JsonProperty("custom_strike")
    private String customStrike;
    
    // Getters and Setters
    public String getTicker() {
        return ticker;
    }
    
    public void setTicker(String ticker) {
        this.ticker = ticker;
    }
    
    public String getEventTicker() {
        return eventTicker;
    }
    
    public void setEventTicker(String eventTicker) {
        this.eventTicker = eventTicker;
    }
    
    public String getMarketType() {
        return marketType;
    }
    
    public void setMarketType(String marketType) {
        this.marketType = marketType;
    }
    
    public String getTitle() {
        return title;
    }
    
    public void setTitle(String title) {
        this.title = title;
    }
    
    public String getSubtitle() {
        return subtitle;
    }
    
    public void setSubtitle(String subtitle) {
        this.subtitle = subtitle;
    }
    
    public String getYesSubtitle() {
        return yesSubtitle;
    }
    
    public void setYesSubtitle(String yesSubtitle) {
        this.yesSubtitle = yesSubtitle;
    }
    
    public String getNoSubtitle() {
        return noSubtitle;
    }
    
    public void setNoSubtitle(String noSubtitle) {
        this.noSubtitle = noSubtitle;
    }
    
    public LocalDateTime getOpenTime() {
        return openTime;
    }
    
    public void setOpenTime(LocalDateTime openTime) {
        this.openTime = openTime;
    }
    
    public LocalDateTime getCloseTime() {
        return closeTime;
    }
    
    public void setCloseTime(LocalDateTime closeTime) {
        this.closeTime = closeTime;
    }
    
    public LocalDateTime getExpectedExpirationTime() {
        return expectedExpirationTime;
    }
    
    public void setExpectedExpirationTime(LocalDateTime expectedExpirationTime) {
        this.expectedExpirationTime = expectedExpirationTime;
    }
    
    public Integer getRiskLimitCents() {
        return riskLimitCents;
    }
    
    public void setRiskLimitCents(Integer riskLimitCents) {
        this.riskLimitCents = riskLimitCents;
    }
    
    public String getStrikeType() {
        return strikeType;
    }
    
    public void setStrikeType(String strikeType) {
        this.strikeType = strikeType;
    }
    
    public Double getFloorStrike() {
        return floorStrike;
    }
    
    public void setFloorStrike(Double floorStrike) {
        this.floorStrike = floorStrike;
    }
    
    public Double getCapStrike() {
        return capStrike;
    }
    
    public void setCapStrike(Double capStrike) {
        this.capStrike = capStrike;
    }
    
    public Boolean getCanCloseEarly() {
        return canCloseEarly;
    }
    
    public void setCanCloseEarly(Boolean canCloseEarly) {
        this.canCloseEarly = canCloseEarly;
    }
    
    public String getCategory() {
        return category;
    }
    
    public void setCategory(String category) {
        this.category = category;
    }
    
    public String getRulesPrimary() {
        return rulesPrimary;
    }
    
    public void setRulesPrimary(String rulesPrimary) {
        this.rulesPrimary = rulesPrimary;
    }
    
    public String getRulesSecondary() {
        return rulesSecondary;
    }
    
    public void setRulesSecondary(String rulesSecondary) {
        this.rulesSecondary = rulesSecondary;
    }
    
    public String getResponsePriceUnits() {
        return responsePriceUnits;
    }
    
    public void setResponsePriceUnits(String responsePriceUnits) {
        this.responsePriceUnits = responsePriceUnits;
    }
    
    public String getSettlementSource() {
        return settlementSource;
    }
    
    public void setSettlementSource(String settlementSource) {
        this.settlementSource = settlementSource;
    }
    
    public String getCustomStrike() {
        return customStrike;
    }
    
    public void setCustomStrike(String customStrike) {
        this.customStrike = customStrike;
    }
}