package com.kalshi.mock.websocket.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public class UpdateSubscriptionCommand {
    
    private Integer id;
    private String cmd = "update_subscription";
    private UpdateSubscriptionParams params;
    
    // Inner class for params
    public static class UpdateSubscriptionParams {
        private String sid;
        @JsonProperty("market_tickers")
        private List<String> marketTickers;
        
        // Getters and Setters
        public String getSid() {
            return sid;
        }
        
        public void setSid(String sid) {
            this.sid = sid;
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
    
    public UpdateSubscriptionParams getParams() {
        return params;
    }
    
    public void setParams(UpdateSubscriptionParams params) {
        this.params = params;
    }
}