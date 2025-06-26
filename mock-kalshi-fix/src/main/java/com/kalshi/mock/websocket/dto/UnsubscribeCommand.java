package com.kalshi.mock.websocket.dto;

import java.util.List;

public class UnsubscribeCommand {
    
    private Integer id;
    private String cmd = "unsubscribe";
    private UnsubscribeParams params;
    
    // Inner class for params
    public static class UnsubscribeParams {
        private List<String> sids;
        
        // Getters and Setters
        public List<String> getSids() {
            return sids;
        }
        
        public void setSids(List<String> sids) {
            this.sids = sids;
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
    
    public UnsubscribeParams getParams() {
        return params;
    }
    
    public void setParams(UnsubscribeParams params) {
        this.params = params;
    }
}