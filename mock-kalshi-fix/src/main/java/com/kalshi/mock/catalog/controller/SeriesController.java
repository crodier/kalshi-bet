package com.kalshi.mock.catalog.controller;

import com.kalshi.mock.catalog.dto.*;
import com.kalshi.mock.catalog.model.Series;
import com.kalshi.mock.catalog.service.SeriesService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/trade-api/v2/series")
@Tag(name = "Series", description = "Series management endpoints")
public class SeriesController {
    
    @Autowired
    private SeriesService seriesService;
    
    @Autowired
    private CatalogMapper catalogMapper;
    
    @GetMapping
    @Operation(summary = "List all series", description = "Returns a paginated list of all series")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved series"),
        @ApiResponse(responseCode = "400", description = "Invalid request parameters"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<SeriesListResponse> listSeries(
            @Parameter(description = "Filter by status") @RequestParam(required = false) String status,
            @Parameter(description = "Maximum number of series to return") @RequestParam(required = false, defaultValue = "100") Integer limit,
            @Parameter(description = "Cursor for pagination") @RequestParam(required = false) String cursor) {
        
        try {
            // For now, ignore status filter as it's not in the service
            List<Series> series = seriesService.getAllSeries(cursor, limit);
            
            // Convert to response DTOs
            List<SeriesResponse> seriesResponses = series.stream()
                .map(catalogMapper::toSeriesResponse)
                .toList();
            
            // Generate next cursor if needed
            String nextCursor = null;
            if (series.size() == limit) {
                // For now, just encode the next offset
                nextCursor = CursorUtil.encodeCursor(series.size());
            }
            
            return ResponseEntity.ok(new SeriesListResponse(seriesResponses, nextCursor));
            
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @GetMapping("/{series_ticker}")
    @Operation(summary = "Get single series", description = "Returns detailed information about a specific series")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Series found"),
        @ApiResponse(responseCode = "404", description = "Series not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<SeriesResponse> getSeries(
            @Parameter(description = "Series ticker", required = true) @PathVariable String series_ticker) {
        
        try {
            Series series = seriesService.getSeriesByTicker(series_ticker);
            return ResponseEntity.ok(catalogMapper.toSeriesResponse(series));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @PostMapping
    @Operation(summary = "Create new series", description = "Create a new series (admin endpoint)")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Series created successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "409", description = "Series already exists"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @SecurityRequirement(name = "ApiKeyAuth")
    public ResponseEntity<SeriesResponse> createSeries(
            @Valid @RequestBody CreateSeriesRequest request,
            @RequestHeader(value = "KALSHI-ACCESS-KEY", required = false) String apiKey) {
        
        try {
            // TODO: Add proper admin authentication check
            if (apiKey == null || !apiKey.startsWith("admin-")) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }
            
            // Convert request to Series model
            Series series = catalogMapper.toSeries(request);
            series = seriesService.createSeries(series);
            return ResponseEntity.status(HttpStatus.CREATED)
                .body(catalogMapper.toSeriesResponse(series));
                
        } catch (IllegalStateException e) {
            // Series already exists
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @PutMapping("/{series_ticker}")
    @Operation(summary = "Update series", description = "Update an existing series")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Series updated successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "404", description = "Series not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @SecurityRequirement(name = "ApiKeyAuth")
    public ResponseEntity<SeriesResponse> updateSeries(
            @Parameter(description = "Series ticker", required = true) @PathVariable String series_ticker,
            @Valid @RequestBody CreateSeriesRequest request,
            @RequestHeader(value = "KALSHI-ACCESS-KEY", required = false) String apiKey) {
        
        try {
            // TODO: Add proper admin authentication check
            if (apiKey == null || !apiKey.startsWith("admin-")) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }
            
            // Convert request to Series model
            Series updates = catalogMapper.toSeries(request);
            Series series = seriesService.updateSeries(series_ticker, updates);
            return ResponseEntity.ok(catalogMapper.toSeriesResponse(series));
            
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @DeleteMapping("/{series_ticker}")
    @Operation(summary = "Delete series", description = "Delete a series")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "204", description = "Series deleted successfully"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "404", description = "Series not found"),
        @ApiResponse(responseCode = "409", description = "Series has active markets"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @SecurityRequirement(name = "ApiKeyAuth")
    public ResponseEntity<Void> deleteSeries(
            @Parameter(description = "Series ticker", required = true) @PathVariable String series_ticker,
            @RequestHeader(value = "KALSHI-ACCESS-KEY", required = false) String apiKey) {
        
        try {
            // TODO: Add proper admin authentication check
            if (apiKey == null || !apiKey.startsWith("admin-")) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }
            
            seriesService.deleteSeries(series_ticker);
            return ResponseEntity.noContent().build();
            
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            // Series has active markets
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}