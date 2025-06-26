package com.kalshi.mock.websocket.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public class SubscribeCommand {
    
    private Integer id;
    private String cmd = "subscribe";
    private SubscribeParams params;
    
    // Inner class for params
    public static class SubscribeParams {
        private List<String> channels;
        @JsonProperty("market_tickers")
        private List<String> marketTickers;
        
        // Getters and Setters
        public List<String> getChannels() {
            return channels;
        }
        
        public void setChannels(List<String> channels) {
            this.channels = channels;
        }
        
        public List<String> getMarketTickers() {
            return marketTickers;
        }
        
        public void setMarketTickers(List<String> marketTickers) {
            this.marketTickers = marketTickers;
        }
    }
    
    // Getters and Setters
    public Integer getId() {
        return id;
    }
    
    public void setId(Integer id) {
        this.id = id;
    }
    
    public String getCmd() {
        return cmd;
    }
    
    public void setCmd(String cmd) {
        this.cmd = cmd;
    }
    
    public SubscribeParams getParams() {
        return params;
    }
    
    public void setParams(SubscribeParams params) {
        this.params = params;
    }
}