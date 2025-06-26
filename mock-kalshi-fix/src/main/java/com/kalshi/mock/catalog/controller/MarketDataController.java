package com.kalshi.mock.catalog.controller;

import com.kalshi.mock.catalog.service.MarketDataService;
import com.kalshi.mock.catalog.service.MarketService;
import com.kalshi.mock.catalog.dto.CursorUtil;
import com.kalshi.mock.dto.OrderbookResponse;
import com.fbg.api.rest.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

@RestController
@RequestMapping("/trade-api/v2")
@Tag(name = "Market Data", description = "Market data endpoints for orderbook, trades, and candlesticks")
public class MarketDataController {
    
    @Autowired
    private MarketDataService marketDataService;
    
    @Autowired
    private MarketService marketService;
    
    @GetMapping("/markets/{ticker}/orderbook")
    @Operation(summary = "Get market orderbook", description = "Returns the current orderbook for a specific market")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Orderbook retrieved successfully"),
        @ApiResponse(responseCode = "404", description = "Market not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<OrderbookResponse> getOrderbook(
            @Parameter(description = "Market ticker", required = true) @PathVariable String ticker,
            @Parameter(description = "Depth of orderbook") @RequestParam(required = false, defaultValue = "5") Integer depth) {
        
        try {
            // Verify market exists
            marketService.getMarketByTicker(ticker);
            
            // Get orderbook from market data service in Kalshi format
            OrderbookResponse.OrderbookData orderbookData = marketDataService.getMarketOrderbookKalshiFormat(ticker, depth);
            
            return ResponseEntity.ok(new OrderbookResponse(orderbookData));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @GetMapping("/markets/trades")
    @Operation(summary = "Get trades", description = "Returns recent trades across markets with optional filters")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Trades retrieved successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request parameters"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<TradesResponse> getTrades(
            @Parameter(description = "Filter by market ticker") @RequestParam(required = false) String ticker,
            @Parameter(description = "Minimum timestamp (ISO 8601)") @RequestParam(required = false) String min_ts,
            @Parameter(description = "Maximum timestamp (ISO 8601)") @RequestParam(required = false) String max_ts,
            @Parameter(description = "Maximum number of trades to return") @RequestParam(required = false, defaultValue = "100") Integer limit,
            @Parameter(description = "Cursor for pagination") @RequestParam(required = false) String cursor) {
        
        try {
            // Parse timestamps if provided
            LocalDateTime minTimestamp = null;
            LocalDateTime maxTimestamp = null;
            
            if (min_ts != null) {
                minTimestamp = parseTimestamp(min_ts);
            }
            if (max_ts != null) {
                maxTimestamp = parseTimestamp(max_ts);
            }
            
            // Convert timestamps to Long epoch millis
            Long minTs = minTimestamp != null ? minTimestamp.toInstant(ZoneOffset.UTC).toEpochMilli() : null;
            Long maxTs = maxTimestamp != null ? maxTimestamp.toInstant(ZoneOffset.UTC).toEpochMilli() : null;
            
            // Get trades from market data service
            List<Trade> trades = marketDataService.getTrades(ticker, minTs, maxTs, cursor, limit);
            
            // Generate next cursor if needed
            String nextCursor = null;
            if (trades.size() == limit) {
                // Use simple offset-based cursor for now
                nextCursor = CursorUtil.encodeCursor(trades.size());
            }
            
            return ResponseEntity.ok(new TradesResponse(trades, nextCursor));
            
        } catch (DateTimeParseException e) {
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @GetMapping("/series/{series_ticker}/markets/{ticker}/candlesticks")
    @Operation(summary = "Get candlesticks", description = "Returns candlestick data for a specific market")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Candlesticks retrieved successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request parameters"),
        @ApiResponse(responseCode = "404", description = "Market or series not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CandlesticksResponse> getCandlesticks(
            @Parameter(description = "Series ticker", required = true) @PathVariable String series_ticker,
            @Parameter(description = "Market ticker", required = true) @PathVariable String ticker,
            @Parameter(description = "Start timestamp (ISO 8601)") @RequestParam(required = false) String start_ts,
            @Parameter(description = "End timestamp (ISO 8601)") @RequestParam(required = false) String end_ts,
            @Parameter(description = "Period in minutes (1, 5, 15, 60, 240, 1440)") @RequestParam(required = false, defaultValue = "60") Integer period_interval) {
        
        try {
            // Validate period interval
            if (!isValidPeriodInterval(period_interval)) {
                return ResponseEntity.badRequest().build();
            }
            
            // Verify market exists and belongs to series
            var market = marketService.getMarketByTicker(ticker);
            // TODO: Add series ticker validation when field is available in Market model
            
            // Parse timestamps
            LocalDateTime startTimestamp = start_ts != null ? parseTimestamp(start_ts) : LocalDateTime.now().minusDays(7);
            LocalDateTime endTimestamp = end_ts != null ? parseTimestamp(end_ts) : LocalDateTime.now();
            
            // Convert period interval to string format expected by service
            String period = period_interval + "m";
            if (period_interval == 1440) {
                period = "1d";
            } else if (period_interval == 240) {
                period = "4h";
            }
            
            // Convert timestamps to epoch millis
            Long startTs = startTimestamp.toInstant(ZoneOffset.UTC).toEpochMilli();
            Long endTs = endTimestamp.toInstant(ZoneOffset.UTC).toEpochMilli();
            
            // Get candlesticks from market data service (with default limit)
            List<MarketDataService.Candlestick> candlesticks = marketDataService.getCandlesticks(
                ticker, period, startTs, endTs, 1000
            );
            
            // Convert to API response format
            List<Candlestick> apiCandlesticks = candlesticks.stream()
                .map(c -> new Candlestick(
                    String.valueOf(c.getPeriodStart()),
                    String.valueOf(c.getPeriodStart() + (period_interval * 60 * 1000)),
                    c.getOpen(),
                    c.getHigh(),
                    c.getLow(),
                    c.getClose(),
                    c.getVolume()
                ))
                .toList();
            
            return ResponseEntity.ok(new CandlesticksResponse(
                series_ticker,
                ticker,
                apiCandlesticks,
                period_interval
            ));
            
        } catch (DateTimeParseException e) {
            return ResponseEntity.badRequest().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    private LocalDateTime parseTimestamp(String timestamp) {
        return LocalDateTime.parse(timestamp, DateTimeFormatter.ISO_DATE_TIME);
    }
    
    private boolean isValidPeriodInterval(Integer period) {
        return period == 1 || period == 5 || period == 15 || 
               period == 60 || period == 240 || period == 1440;
    }
}