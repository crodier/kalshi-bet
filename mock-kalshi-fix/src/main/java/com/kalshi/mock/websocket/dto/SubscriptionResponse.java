package com.kalshi.mock.websocket.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public class SubscriptionResponse {
    
    private String type = "subscribed";
    private Integer id;
    private List<Subscription> subscriptions;
    
    public static class Subscription {
        private String sid;
        private String channel;
        @JsonProperty("market_tickers")
        private List<String> marketTickers;
        
        public Subscription() {}
        
        public Subscription(String sid, String channel, List<String> marketTickers) {
            this.sid = sid;
            this.channel = channel;
            this.marketTickers = marketTickers;
        }
        
        // Getters and Setters
        public String getSid() {
            return sid;
        }
        
        public void setSid(String sid) {
            this.sid = sid;
        }
        
        public String getChannel() {
            return channel;
        }
        
        public void setChannel(String channel) {
            this.channel = channel;
        }
        
        public List<String> getMarketTickers() {
            return marketTickers;
        }
        
        public void setMarketTickers(List<String> marketTickers) {
            this.marketTickers = marketTickers;
        }
    }
    
    // Getters and Setters
    public String getType() {
        return type;
    }
    
    public void setType(String type) {
        this.type = type;
    }
    
    public Integer getId() {
        return id;
    }
    
    public void setId(Integer id) {
        this.id = id;
    }
    
    public List<Subscription> getSubscriptions() {
        return subscriptions;
    }
    
    public void setSubscriptions(List<Subscription> subscriptions) {
        this.subscriptions = subscriptions;
    }
}