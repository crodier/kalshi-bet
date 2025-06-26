package com.kalshi.mock.catalog.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Response DTO for Market matching Kalshi's API structure
 */
public class MarketResponse {
    
    @JsonProperty("ticker")
    private String ticker;
    
    @JsonProperty("event_ticker")
    private String eventTicker;
    
    @JsonProperty("market_type")
    private String marketType;
    
    @JsonProperty("title")
    private String title;
    
    @JsonProperty("subtitle")
    private String subtitle;
    
    @JsonProperty("yes_sub_title")
    private String yesSubTitle;
    
    @JsonProperty("no_sub_title")
    private String noSubTitle;
    
    @JsonProperty("open_time")
    private LocalDateTime openTime;
    
    @JsonProperty("close_time")
    private LocalDateTime closeTime;
    
    @JsonProperty("expected_expiration_time")
    private LocalDateTime expectedExpirationTime;
    
    @JsonProperty("expiration_time")
    private LocalDateTime expirationTime;
    
    @JsonProperty("status")
    private String status;
    
    @JsonProperty("yes_bid")
    private Integer yesBid;
    
    @JsonProperty("yes_ask")
    private Integer yesAsk;
    
    @JsonProperty("no_bid")
    private Integer noBid;
    
    @JsonProperty("no_ask")
    private Integer noAsk;
    
    @JsonProperty("last_price")
    private Integer lastPrice;
    
    @JsonProperty("previous_yes_bid")
    private Integer previousYesBid;
    
    @JsonProperty("previous_yes_ask")
    private Integer previousYesAsk;
    
    @JsonProperty("previous_price")
    private Integer previousPrice;
    
    @JsonProperty("volume")
    private Long volume;
    
    @JsonProperty("volume_24h")
    private Long volume24h;
    
    @JsonProperty("liquidity")
    private BigDecimal liquidity;
    
    @JsonProperty("open_interest")
    private Long openInterest;
    
    @JsonProperty("notional_value")
    private BigDecimal notionalValue;
    
    @JsonProperty("risk_limit_cents")
    private Long riskLimitCents;
    
    @JsonProperty("strike_type")
    private String strikeType;
    
    @JsonProperty("floor_strike")
    private BigDecimal floorStrike;
    
    @JsonProperty("cap_strike")
    private BigDecimal capStrike;
    
    @JsonProperty("result")
    private String result;
    
    @JsonProperty("can_close_early")
    private Boolean canCloseEarly;
    
    @JsonProperty("expiration_value")
    private String expirationValue;
    
    @JsonProperty("category")
    private String category;
    
    @JsonProperty("rules_primary")
    private String rulesPrimary;
    
    @JsonProperty("rules_secondary")
    private String rulesSecondary;
    
    @JsonProperty("response_price_units")
    private String responsePriceUnits;
    
    @JsonProperty("settlement_timer_seconds")
    private Integer settlementTimerSeconds;
    
    @JsonProperty("settlement_source")
    private String settlementSource;
    
    @JsonProperty("custom_strike")
    private String customStrike;
    
    @JsonProperty("is_deactivated")
    private Boolean isDeactivated;
    
    // Constructors
    public MarketResponse() {
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
    
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
    }
    
    public Integer getYesBid() {
        return yesBid;
    }
    
    public void setYesBid(Integer yesBid) {
        this.yesBid = yesBid;
    }
    
    public Integer getYesAsk() {
        return yesAsk;
    }
    
    public void setYesAsk(Integer yesAsk) {
        this.yesAsk = yesAsk;
    }
    
    public Integer getNoBid() {
        return noBid;
    }
    
    public void setNoBid(Integer noBid) {
        this.noBid = noBid;
    }
    
    public Integer getNoAsk() {
        return noAsk;
    }
    
    public void setNoAsk(Integer noAsk) {
        this.noAsk = noAsk;
    }
    
    public Integer getLastPrice() {
        return lastPrice;
    }
    
    public void setLastPrice(Integer lastPrice) {
        this.lastPrice = lastPrice;
    }
    
    public Integer getPreviousYesBid() {
        return previousYesBid;
    }
    
    public void setPreviousYesBid(Integer previousYesBid) {
        this.previousYesBid = previousYesBid;
    }
    
    public Integer getPreviousYesAsk() {
        return previousYesAsk;
    }
    
    public void setPreviousYesAsk(Integer previousYesAsk) {
        this.previousYesAsk = previousYesAsk;
    }
    
    public Integer getPreviousPrice() {
        return previousPrice;
    }
    
    public void setPreviousPrice(Integer previousPrice) {
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
}