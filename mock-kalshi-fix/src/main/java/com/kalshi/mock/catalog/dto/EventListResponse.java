package com.kalshi.mock.catalog.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Paginated response for Event list
 */
public class EventListResponse extends CursorResponse {
    
    @JsonProperty("events")
    private List<EventResponse> events;
    
    // Constructors
    public EventListResponse() {
    }
    
    public EventListResponse(List<EventResponse> events, String cursor) {
        super(cursor);
        this.events = events;
    }
    
    // Getters and Setters
    public List<EventResponse> getEvents() {
        return events;
    }
    
    public void setEvents(List<EventResponse> events) {
        this.events = events;
    }
}