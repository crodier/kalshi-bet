package com.kalshi.marketmaker.model;

import lombok.Data;

@Data
public class MarketState {
    private String marketTicker;
    private int currentBidPrice;
    private int currentAskPrice;
    private int midPrice;
    private long lastUpdateTime;
}