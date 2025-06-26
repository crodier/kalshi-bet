package com.kalshi.mock.catalog.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Paginated response for Series list
 */
public class SeriesListResponse extends CursorResponse {
    
    @JsonProperty("series")
    private List<SeriesResponse> series;
    
    // Constructors
    public SeriesListResponse() {
    }
    
    public SeriesListResponse(List<SeriesResponse> series, String cursor) {
        super(cursor);
        this.series = series;
    }
    
    // Getters and Setters
    public List<SeriesResponse> getSeries() {
        return series;
    }
    
    public void setSeries(List<SeriesResponse> series) {
        this.series = series;
    }
}