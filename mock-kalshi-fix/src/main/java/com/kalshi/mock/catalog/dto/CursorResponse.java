package com.kalshi.mock.catalog.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Base response DTO for cursor-based pagination
 */
public class CursorResponse {
    
    @JsonProperty("cursor")
    private String cursor;
    
    // Constructors
    public CursorResponse() {
    }
    
    public CursorResponse(String cursor) {
        this.cursor = cursor;
    }
    
    // Getters and Setters
    public String getCursor() {
        return cursor;
    }
    
    public void setCursor(String cursor) {
        this.cursor = cursor;
    }
}