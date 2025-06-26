package com.fbg.api.rest;

import java.util.List;

/**
 * Response containing candlestick data for a market
 */
public class CandlesticksResponse {
    
    private String series_ticker;
    private String ticker;
    private List<Candlestick> candlesticks;
    private Integer period_interval;
    
    public CandlesticksResponse() {
    }
    
    public CandlesticksResponse(String series_ticker, String ticker, 
                               List<Candlestick> candlesticks, Integer period_interval) {
        this.series_ticker = series_ticker;
        this.ticker = ticker;
        this.candlesticks = candlesticks;
        this.period_interval = period_interval;
    }
    
    // Getters and Setters
    public String getSeries_ticker() {
        return series_ticker;
    }
    
    public void setSeries_ticker(String series_ticker) {
        this.series_ticker = series_ticker;
    }
    
    public String getTicker() {
        return ticker;
    }
    
    public void setTicker(String ticker) {
        this.ticker = ticker;
    }
    
    public List<Candlestick> getCandlesticks() {
        return candlesticks;
    }
    
    public void setCandlesticks(List<Candlestick> candlesticks) {
        this.candlesticks = candlesticks;
    }
    
    public Integer getPeriod_interval() {
        return period_interval;
    }
    
    public void setPeriod_interval(Integer period_interval) {
        this.period_interval = period_interval;
    }
}