package com.kalshi.mock.websocket.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class TickerMessage {
    
    @JsonProperty("market_ticker")
    private String marketTicker;
    
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
    
    private Long volume;
    
    @JsonProperty("open_interest")
    private Long openInterest;
    
    // Constructors
    public TickerMessage() {}
    
    // Getters and Setters
    public String getMarketTicker() {
        return marketTicker;
    }
    
    public void setMarketTicker(String marketTicker) {
        this.marketTicker = marketTicker;
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
    
    public Long getVolume() {
        return volume;
    }
    
    public void setVolume(Long volume) {
        this.volume = volume;
    }
    
    public Long getOpenInterest() {
        return openInterest;
    }
    
    public void setOpenInterest(Long openInterest) {
        this.openInterest = openInterest;
    }
}