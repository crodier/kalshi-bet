package com.kalshi.orderbook.dto;

import com.fbg.api.kalshi.PriceLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderBookSnapshot {
    private String marketTicker;
    private PriceLevel bestBid;
    private PriceLevel bestAsk;
    private List<PriceLevel> bids;
    private List<PriceLevel> asks;
    private Long lastUpdateTimestamp;
    private Long lastTimeInternalBookUpdated;
    private Long receivedTimestamp;
    private Long processedTimestamp;
    private Long processingLatency;
    private Integer bidLevels;
    private Integer askLevels;
}