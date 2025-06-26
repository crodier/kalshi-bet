package com.kalshi.marketdata.model;

import lombok.Data;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Market information for admin interface
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MarketInfo {
    private String ticker;
    private String title;
    private boolean isActive;
    private Instant lastUpdate;
    private long messageCount;
    private boolean isBootstrapped;
    private Long lastSequence;
    
    public MarketInfo(String ticker) {
        this.ticker = ticker;
        this.title = null;
        this.isActive = false;
        this.lastUpdate = Instant.now();
        this.messageCount = 0;
        this.isBootstrapped = false;
        this.lastSequence = null;
    }
}