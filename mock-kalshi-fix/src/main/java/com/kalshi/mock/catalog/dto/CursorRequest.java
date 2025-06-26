package com.kalshi.mock.catalog.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Base request DTO for cursor-based pagination
 */
public class CursorRequest {
    
    @JsonProperty("limit")
    private Integer limit = 100;
    
    @JsonProperty("cursor")
    private String cursor;
    
    // Constructors
    public CursorRequest() {
    }
    
    public CursorRequest(Integer limit, String cursor) {
        this.limit = limit;
        this.cursor = cursor;
    }
    
    // Getters and Setters
    public Integer getLimit() {
        return limit;
    }
    
    public void setLimit(Integer limit) {
        this.limit = limit;
    }
    
    public String getCursor() {
        return cursor;
    }
    
    public void setCursor(String cursor) {
        this.cursor = cursor;
    }
}