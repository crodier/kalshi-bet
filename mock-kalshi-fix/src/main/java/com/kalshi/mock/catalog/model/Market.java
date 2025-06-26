package com.kalshi.mock.catalog.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Represents a Market in the Kalshi platform.
 * A Market belongs to an Event and represents a tradable prediction market.
 */
public class Market {
    
    private String ticker;
    
    private String eventTicker;
    
    private MarketType marketType;
    
    private String title;
    
    private String subtitle;
    
    private String yesSubtitle;
    
    private String noSubtitle;
    
    private LocalDateTime openTime;
    
    private LocalDateTime closeTime;
    
    private LocalDateTime expectedExpirationTime;
    
    private LocalDateTime expirationTime;
    
    private MarketStatus status;
    
    // Price information
    
    private BigDecimal yesBid;
    
    
    private BigDecimal yesAsk;
    
    
    private BigDecimal noBid;
    
    
    private BigDecimal noAsk;
    
    
    private BigDecimal lastPrice;
    
    
    private BigDecimal previousYesBid;
    
    
    private BigDecimal previousYesAsk;
    
    
    private BigDecimal previousPrice;
    
    // Volume information
    
    private Long volume;
    
    
    private Long volume24h;
    
    
    private BigDecimal liquidity;
    
    
    private Long openInterest;
    
    // Financial information
    
    private BigDecimal notionalValue;
    
    
    private Long riskLimitCents;
    
    // Market details
    
    private String strikeType;
    
    
    private BigDecimal floorStrike;
    
    
    private BigDecimal capStrike;
    
    
    private String result;
    
    
    private Boolean canCloseEarly;
    
    
    private String expirationValue;
    
    
    private String category;
    
    // Rules
    
    private String rulesPrimary;
    
    
    private String rulesSecondary;
    
    // Additional metadata
    
    private String responsePriceUnits;
    
    
    private Integer settlementTimerSeconds;
    
    
    private String settlementSource;
    
    
    private String customStrike;
    
    
    private Boolean isDeactivated;
    
    
    private LocalDateTime createdAt;
    
    
    private LocalDateTime updatedAt;
    
    // Constructors
    public Market() {
    }
    
    public Market(String ticker, String eventTicker, MarketType marketType, String title,
                  LocalDateTime openTime, LocalDateTime closeTime, MarketStatus status) {
        this.ticker = ticker;
        this.eventTicker = eventTicker;
        this.marketType = marketType;
        this.title = title;
        this.openTime = openTime;
        this.closeTime = closeTime;
        this.status = status;
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
    
    public String getEventTicker() {
        return eventTicker;
    }
    
    public void setEventTicker(String eventTicker) {
        this.eventTicker = eventTicker;
    }
    
    public MarketType getMarketType() {
        return marketType;
    }
    
    public void setMarketType(MarketType marketType) {
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
    
    public LocalDateTime getExpirationTime() {
        return expirationTime;
    }
    
    public void setExpirationTime(LocalDateTime expirationTime) {
        this.expirationTime = expirationTime;
    }
    
    public MarketStatus getStatus() {
        return status;
    }
    
    public void setStatus(MarketStatus status) {
        this.status = status;
    }
    
    public BigDecimal getYesBid() {
        return yesBid;
    }
    
    public void setYesBid(BigDecimal yesBid) {
        this.yesBid = yesBid;
    }
    
    public BigDecimal getYesAsk() {
        return yesAsk;
    }
    
    public void setYesAsk(BigDecimal yesAsk) {
        this.yesAsk = yesAsk;
    }
    
    public BigDecimal getNoBid() {
        return noBid;
    }
    
    public void setNoBid(BigDecimal noBid) {
        this.noBid = noBid;
    }
    
    public BigDecimal getNoAsk() {
        return noAsk;
    }
    
    public void setNoAsk(BigDecimal noAsk) {
        this.noAsk = noAsk;
    }
    
    public BigDecimal getLastPrice() {
        return lastPrice;
    }
    
    public void setLastPrice(BigDecimal lastPrice) {
        this.lastPrice = lastPrice;
    }
    
    public BigDecimal getPreviousYesBid() {
        return previousYesBid;
    }
    
    public void setPreviousYesBid(BigDecimal previousYesBid) {
        this.previousYesBid = previousYesBid;
    }
    
    public BigDecimal getPreviousYesAsk() {
        return previousYesAsk;
    }
    
    public void setPreviousYesAsk(BigDecimal previousYesAsk) {
        this.previousYesAsk = previousYesAsk;
    }
    
    public BigDecimal getPreviousPrice() {
        return previousPrice;
    }
    
    public void setPreviousPrice(BigDecimal previousPrice) {
        this.previousPrice = previousPrice;
    }
    
    public Long getVolume() {
        return volume;
    }
    
    public void setVolume(Long volume) {
        this.volume = volume;
    }
    
    public Long getVolume24h() {
        return volume24h;
    }
    
    public void setVolume24h(Long volume24h) {
        this.volume24h = volume24h;
    }
    
    public BigDecimal getLiquidity() {
        return liquidity;
    }
    
    public void setLiquidity(BigDecimal liquidity) {
        this.liquidity = liquidity;
    }
    
    public Long getOpenInterest() {
        return openInterest;
    }
    
    public void setOpenInterest(Long openInterest) {
        this.openInterest = openInterest;
    }
    
    public BigDecimal getNotionalValue() {
        return notionalValue;
    }
    
    public void setNotionalValue(BigDecimal notionalValue) {
        this.notionalValue = notionalValue;
    }
    
    public Long getRiskLimitCents() {
        return riskLimitCents;
    }
    
    public void setRiskLimitCents(Long riskLimitCents) {
        this.riskLimitCents = riskLimitCents;
    }
    
    public String getStrikeType() {
        return strikeType;
    }
    
    public void setStrikeType(String strikeType) {
        this.strikeType = strikeType;
    }
    
    public BigDecimal getFloorStrike() {
        return floorStrike;
    }
    
    public void setFloorStrike(BigDecimal floorStrike) {
        this.floorStrike = floorStrike;
    }
    
    public BigDecimal getCapStrike() {
        return capStrike;
    }
    
    public void setCapStrike(BigDecimal capStrike) {
        this.capStrike = capStrike;
    }
    
    public String getResult() {
        return result;
    }
    
    public void setResult(String result) {
        this.result = result;
    }
    
    public Boolean getCanCloseEarly() {
        return canCloseEarly;
    }
    
    public void setCanCloseEarly(Boolean canCloseEarly) {
        this.canCloseEarly = canCloseEarly;
    }
    
    public String getExpirationValue() {
        return expirationValue;
    }
    
    public void setExpirationValue(String expirationValue) {
        this.expirationValue = expirationValue;
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
    
    public Integer getSettlementTimerSeconds() {
        return settlementTimerSeconds;
    }
    
    public void setSettlementTimerSeconds(Integer settlementTimerSeconds) {
        this.settlementTimerSeconds = settlementTimerSeconds;
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
    
    public Boolean getIsDeactivated() {
        return isDeactivated;
    }
    
    public void setIsDeactivated(Boolean isDeactivated) {
        this.isDeactivated = isDeactivated;
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
    
    // Enums
    public enum MarketType {
        BINARY,
        SCALAR
    }
    
    public enum MarketStatus {
        OPEN,
        CLOSED,
        SETTLED,
        FINALIZED
    }
}