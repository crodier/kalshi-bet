package com.fbg.api.rest;

/**
 * Represents a candlestick (OHLCV) data point
 */
public class Candlestick {
    
    private String start_ts;
    private String end_ts;
    private Integer open;
    private Integer high;
    private Integer low;
    private Integer close;
    private Long volume;
    
    public Candlestick() {
    }
    
    public Candlestick(String start_ts, String end_ts, Integer open, 
                      Integer high, Integer low, Integer close, Long volume) {
        this.start_ts = start_ts;
        this.end_ts = end_ts;
        this.open = open;
        this.high = high;
        this.low = low;
        this.close = close;
        this.volume = volume;
    }
    
    // Getters and Setters
    public String getStart_ts() {
        return start_ts;
    }
    
    public void setStart_ts(String start_ts) {
        this.start_ts = start_ts;
    }
    
    public String getEnd_ts() {
        return end_ts;
    }
    
    public void setEnd_ts(String end_ts) {
        this.end_ts = end_ts;
    }
    
    public Integer getOpen() {
        return open;
    }
    
    public void setOpen(Integer open) {
        this.open = open;
    }
    
    public Integer getHigh() {
        return high;
    }
    
    public void setHigh(Integer high) {
        this.high = high;
    }
    
    public Integer getLow() {
        return low;
    }
    
    public void setLow(Integer low) {
        this.low = low;
    }
    
    public Integer getClose() {
        return close;
    }
    
    public void setClose(Integer close) {
        this.close = close;
    }
    
    public Long getVolume() {
        return volume;
    }
    
    public void setVolume(Long volume) {
        this.volume = volume;
    }
}