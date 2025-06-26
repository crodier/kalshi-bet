package com.kalshi.mock.catalog.controller;

import com.kalshi.mock.catalog.dto.*;
import com.kalshi.mock.catalog.model.Market;
import com.kalshi.mock.catalog.service.MarketService;
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
@RequestMapping("/trade-api/v2")
@Tag(name = "Catalog Markets", description = "Market catalog management endpoints")
public class CatalogMarketController {
    
    @Autowired
    private MarketService marketService;
    
    @Autowired
    private CatalogMapper catalogMapper;
    
    @GetMapping("/markets")
    @Operation(summary = "List markets", description = "Returns a filtered and paginated list of markets")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved markets"),
        @ApiResponse(responseCode = "400", description = "Invalid request parameters"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<MarketListResponse> listMarkets(
            @Parameter(description = "Filter by event ticker") @RequestParam(required = false) String event_ticker,
            @Parameter(description = "Filter by series ticker") @RequestParam(required = false) String series_ticker,
            @Parameter(description = "Filter by status") @RequestParam(required = false) String status,
            @Parameter(description = "Filter by tickers (comma-separated)") @RequestParam(required = false) String tickers,
            @Parameter(description = "Minimum close time (ISO 8601)") @RequestParam(required = false) String min_close_ts,
            @Parameter(description = "Maximum close time (ISO 8601)") @RequestParam(required = false) String max_close_ts,
            @Parameter(description = "Maximum number of markets to return") @RequestParam(required = false, defaultValue = "100") Integer limit,
            @Parameter(description = "Cursor for pagination") @RequestParam(required = false) String cursor) {
        
        try {
            // Create filter
            MarketService.MarketFilter filter = new MarketService.MarketFilter();
            filter.setEventTicker(event_ticker);
            filter.setSeriesTicker(series_ticker);
            if (status != null) {
                filter.setStatus(Market.MarketStatus.valueOf(status.toUpperCase()));
            }
            
            // Parse tickers list if provided
            if (tickers != null && !tickers.isEmpty()) {
                filter.setTickers(List.of(tickers.split(",")));
            }
            
            // Parse timestamps
            if (min_close_ts != null) {
                filter.setMinCloseTime(LocalDateTime.parse(min_close_ts));
            }
            if (max_close_ts != null) {
                filter.setMaxCloseTime(LocalDateTime.parse(max_close_ts));
            }
            
            // Decode cursor to offset if provided
            if (cursor != null) {
                int offset = CursorUtil.decodeCursor(cursor);
                // TODO: Use offset in query
            }
            filter.setCursor(cursor);
            filter.setLimit(limit);
            
            List<Market> markets = marketService.getMarkets(filter);
            
            // Convert to response DTOs
            List<MarketResponse> marketResponses = markets.stream()
                .map(catalogMapper::toMarketResponse)
                .toList();
            
            // Generate next cursor if needed
            String nextCursor = null;
            if (markets.size() == limit) {
                // For now, just encode the next offset
                nextCursor = CursorUtil.encodeCursor(markets.size());
            }
            
            return ResponseEntity.ok(new MarketListResponse(marketResponses, nextCursor));
            
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @GetMapping("/markets/{ticker}")
    @Operation(summary = "Get single market", description = "Returns detailed information about a specific market")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Market found"),
        @ApiResponse(responseCode = "404", description = "Market not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<MarketResponse> getMarket(
            @Parameter(description = "Market ticker", required = true) @PathVariable String ticker) {
        
        try {
            Market market = marketService.getMarketByTicker(ticker);
            return ResponseEntity.ok(catalogMapper.toMarketResponse(market));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @PostMapping("/markets")
    @Operation(summary = "Create new market", description = "Create a new market")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Market created successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "404", description = "Event not found"),
        @ApiResponse(responseCode = "409", description = "Market already exists"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @SecurityRequirement(name = "ApiKeyAuth")
    public ResponseEntity<MarketResponse> createMarket(
            @Valid @RequestBody CreateMarketRequest request,
            @RequestHeader(value = "KALSHI-ACCESS-KEY", required = false) String apiKey) {
        
        try {
            // TODO: Add proper admin authentication check
            if (apiKey == null || !apiKey.startsWith("admin-")) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }
            
            // Convert request to Market model
            Market market = catalogMapper.toMarket(request);
            market = marketService.createMarket(market);
            return ResponseEntity.status(HttpStatus.CREATED)
                .body(catalogMapper.toMarketResponse(market));
                
        } catch (IllegalStateException e) {
            // Market already exists
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        } catch (IllegalArgumentException e) {
            // Invalid request or event not found
            if (e.getMessage().contains("Event not found")) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @PutMapping("/markets/{ticker}")
    @Operation(summary = "Update market", description = "Update an existing market")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Market updated successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "404", description = "Market not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @SecurityRequirement(name = "ApiKeyAuth")
    public ResponseEntity<MarketResponse> updateMarket(
            @Parameter(description = "Market ticker", required = true) @PathVariable String ticker,
            @Valid @RequestBody CreateMarketRequest request,
            @RequestHeader(value = "KALSHI-ACCESS-KEY", required = false) String apiKey) {
        
        try {
            // TODO: Add proper admin authentication check
            if (apiKey == null || !apiKey.startsWith("admin-")) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }
            
            // Convert request to Market model
            Market updates = catalogMapper.toMarket(request);
            Market market = marketService.updateMarket(ticker, updates);
            return ResponseEntity.ok(catalogMapper.toMarketResponse(market));
            
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @DeleteMapping("/markets/{ticker}")
    @Operation(summary = "Delete market", description = "Delete a market")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "204", description = "Market deleted successfully"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "404", description = "Market not found"),
        @ApiResponse(responseCode = "409", description = "Market has active orders"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @SecurityRequirement(name = "ApiKeyAuth")
    public ResponseEntity<Void> deleteMarket(
            @Parameter(description = "Market ticker", required = true) @PathVariable String ticker,
            @RequestHeader(value = "KALSHI-ACCESS-KEY", required = false) String apiKey) {
        
        try {
            // TODO: Add proper admin authentication check
            if (apiKey == null || !apiKey.startsWith("admin-")) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }
            
            marketService.deleteMarket(ticker);
            return ResponseEntity.noContent().build();
            
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            // Market has active orders
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}