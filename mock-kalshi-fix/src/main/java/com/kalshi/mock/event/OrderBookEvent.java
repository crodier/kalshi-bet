package com.kalshi.mock.event;

import java.util.List;

public class OrderBookEvent {
    
    public enum EventType {
        SNAPSHOT,
        DELTA,
        TRADE,
        TICKER_UPDATE
    }
    
    private final EventType type;
    private final String marketTicker;
    private final Object data;
    private final long timestamp;
    
    public OrderBookEvent(EventType type, String marketTicker, Object data) {
        this.type = type;
        this.marketTicker = marketTicker;
        this.data = data;
        this.timestamp = System.currentTimeMillis();
    }
    
    // Getters
    public EventType getType() {
        return type;
    }
    
    public String getMarketTicker() {
        return marketTicker;
    }
    
    public Object getData() {
        return data;
    }
    
    public long getTimestamp() {
        return timestamp;
    }
    
    // Data classes for different event types
    public static class DeltaData {
        private final Integer price;
        private final Integer delta;
        private final String side;
        
        public DeltaData(Integer price, Integer delta, String side) {
            this.price = price;
            this.delta = delta;
            this.side = side;
        }
        
        public Integer getPrice() { return price; }
        public Integer getDelta() { return delta; }
        public String getSide() { return side; }
    }
    
    public static class SnapshotData {
        private final List<List<Integer>> yesSide;
        private final List<List<Integer>> noSide;
        
        public SnapshotData(List<List<Integer>> yesSide, List<List<Integer>> noSide) {
            this.yesSide = yesSide;
            this.noSide = noSide;
        }
        
        public List<List<Integer>> getYesSide() { return yesSide; }
        public List<List<Integer>> getNoSide() { return noSide; }
    }
    
    public static class TradeData {
        private final Integer price;
        private final Integer count;
        private final String side;
        private final String tradeId;
        
        public TradeData(Integer price, Integer count, String side, String tradeId) {
            this.price = price;
            this.count = count;
            this.side = side;
            this.tradeId = tradeId;
        }
        
        public Integer getPrice() { return price; }
        public Integer getCount() { return count; }
        public String getSide() { return side; }
        public String getTradeId() { return tradeId; }
    }
    
    public static class TickerData {
        private final String marketTicker;
        private final Integer lastPrice;
        private final Integer volume;
        private final Integer bestBid;
        private final Integer bestAsk;
        
        public TickerData(String marketTicker, Integer lastPrice, Integer volume, Integer bestBid, Integer bestAsk) {
            this.marketTicker = marketTicker;
            this.lastPrice = lastPrice;
            this.volume = volume;
            this.bestBid = bestBid;
            this.bestAsk = bestAsk;
        }
        
        public String getMarketTicker() { return marketTicker; }
        public Integer getLastPrice() { return lastPrice; }
        public Integer getVolume() { return volume; }
        public Integer getBestBid() { return bestBid; }
        public Integer getBestAsk() { return bestAsk; }
    }
}