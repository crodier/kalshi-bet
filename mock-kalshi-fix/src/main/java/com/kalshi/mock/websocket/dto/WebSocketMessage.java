package com.kalshi.mock.websocket.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class WebSocketMessage {
    
    private String type;
    private String sid;
    private Long seq;
    private Object msg;
    
    // Constructors
    public WebSocketMessage() {}
    
    public WebSocketMessage(String type, String sid, Long seq, Object msg) {
        this.type = type;
        this.sid = sid;
        this.seq = seq;
        this.msg = msg;
    }
    
    // Getters and Setters
    public String getType() {
        return type;
    }
    
    public void setType(String type) {
        this.type = type;
    }
    
    public String getSid() {
        return sid;
    }
    
    public void setSid(String sid) {
        this.sid = sid;
    }
    
    public Long getSeq() {
        return seq;
    }
    
    public void setSeq(Long seq) {
        this.seq = seq;
    }
    
    public Object getMsg() {
        return msg;
    }
    
    public void setMsg(Object msg) {
        this.msg = msg;
    }
}