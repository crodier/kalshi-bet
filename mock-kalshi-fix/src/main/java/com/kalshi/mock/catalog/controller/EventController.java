package com.kalshi.mock.catalog.controller;

import com.kalshi.mock.catalog.dto.*;
import com.kalshi.mock.catalog.model.Event;
import com.kalshi.mock.catalog.service.EventService;
import java.time.LocalDateTime;
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
@RequestMapping("/trade-api/v2/events")
@Tag(name = "Events", description = "Event management endpoints")
public class EventController {
    
    @Autowired
    private EventService eventService;
    
    @Autowired
    private CatalogMapper catalogMapper;
    
    @GetMapping
    @Operation(summary = "List events", description = "Returns a filtered and paginated list of events")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved events"),
        @ApiResponse(responseCode = "400", description = "Invalid request parameters"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<EventListResponse> listEvents(
            @Parameter(description = "Filter by series ticker") @RequestParam(required = false) String series_ticker,
            @Parameter(description = "Filter by status") @RequestParam(required = false) String status,
            @Parameter(description = "Include markets in response") @RequestParam(required = false, defaultValue = "false") Boolean with_nested_markets,
            @Parameter(description = "Maximum number of events to return") @RequestParam(required = false, defaultValue = "100") Integer limit,
            @Parameter(description = "Cursor for pagination") @RequestParam(required = false) String cursor) {
        
        try {
            // Convert status string to enum if provided
            Event.EventStatus eventStatus = null;
            if (status != null) {
                eventStatus = Event.EventStatus.valueOf(status.toUpperCase());
            }
            
            List<Event> events = eventService.getAllEvents(series_ticker, eventStatus, 
                cursor, limit, with_nested_markets);
            
            // Convert to response DTOs
            List<EventResponse> eventResponses = events.stream()
                .map(event -> catalogMapper.toEventResponse(event, with_nested_markets))
                .toList();
            
            // Generate next cursor if needed
            String nextCursor = null;
            if (events.size() == limit) {
                // For now, just encode the next offset
                nextCursor = CursorUtil.encodeCursor(events.size());
            }
            
            return ResponseEntity.ok(new EventListResponse(eventResponses, nextCursor));
            
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @GetMapping("/{event_ticker}")
    @Operation(summary = "Get single event", description = "Returns detailed information about a specific event")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Event found"),
        @ApiResponse(responseCode = "404", description = "Event not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<EventResponse> getEvent(
            @Parameter(description = "Event ticker", required = true) @PathVariable String event_ticker,
            @Parameter(description = "Include markets in response") @RequestParam(required = false, defaultValue = "false") Boolean with_nested_markets) {
        
        try {
            Event event = eventService.getEventByTicker(event_ticker, with_nested_markets);
            return ResponseEntity.ok(catalogMapper.toEventResponse(event, with_nested_markets));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @PostMapping
    @Operation(summary = "Create new event", description = "Create a new event")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Event created successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "404", description = "Series not found"),
        @ApiResponse(responseCode = "409", description = "Event already exists"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @SecurityRequirement(name = "ApiKeyAuth")
    public ResponseEntity<EventResponse> createEvent(
            @Valid @RequestBody CreateEventRequest request,
            @RequestHeader(value = "KALSHI-ACCESS-KEY", required = false) String apiKey) {
        
        try {
            // TODO: Add proper admin authentication check
            if (apiKey == null || !apiKey.startsWith("admin-")) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }
            
            // Convert request to Event model
            Event event = catalogMapper.toEvent(request);
            event = eventService.createEvent(event);
            return ResponseEntity.status(HttpStatus.CREATED)
                .body(catalogMapper.toEventResponse(event, false));
                
        } catch (IllegalStateException e) {
            // Event already exists
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        } catch (IllegalArgumentException e) {
            // Invalid request or series not found
            if (e.getMessage().contains("Series not found")) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @PutMapping("/{event_ticker}")
    @Operation(summary = "Update event", description = "Update an existing event")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Event updated successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "404", description = "Event not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @SecurityRequirement(name = "ApiKeyAuth")
    public ResponseEntity<EventResponse> updateEvent(
            @Parameter(description = "Event ticker", required = true) @PathVariable String event_ticker,
            @Valid @RequestBody CreateEventRequest request,
            @RequestHeader(value = "KALSHI-ACCESS-KEY", required = false) String apiKey) {
        
        try {
            // TODO: Add proper admin authentication check
            if (apiKey == null || !apiKey.startsWith("admin-")) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }
            
            // Convert request to Event model  
            Event updates = catalogMapper.toEvent(request);
            Event event = eventService.updateEvent(event_ticker, updates);
            return ResponseEntity.ok(catalogMapper.toEventResponse(event, false));
            
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @DeleteMapping("/{event_ticker}")
    @Operation(summary = "Delete event", description = "Delete an event")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "204", description = "Event deleted successfully"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "404", description = "Event not found"),
        @ApiResponse(responseCode = "409", description = "Event has active markets"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @SecurityRequirement(name = "ApiKeyAuth")
    public ResponseEntity<Void> deleteEvent(
            @Parameter(description = "Event ticker", required = true) @PathVariable String event_ticker,
            @RequestHeader(value = "KALSHI-ACCESS-KEY", required = false) String apiKey) {
        
        try {
            // TODO: Add proper admin authentication check
            if (apiKey == null || !apiKey.startsWith("admin-")) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }
            
            eventService.deleteEvent(event_ticker);
            return ResponseEntity.noContent().build();
            
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            // Event has active markets
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}