package com.kalshi.mock.controller;

import com.fbg.api.rest.Orderbook;
import com.fbg.api.rest.OrderbookResponse;
import com.kalshi.mock.catalog.service.MarketDataService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/trade-api/v2")
@Tag(name = "Market Data Simple", description = "Simplified market data endpoints")
public class MarketDataSimpleController {
    
    @Autowired
    private MarketDataService marketDataService;
    
    @GetMapping("/orderbook")
    @Operation(summary = "Get orderbook by ticker parameter", 
               description = "Returns the current orderbook for a market specified by ticker query parameter")
    public ResponseEntity<OrderbookResponse> getOrderbookByParam(
            @Parameter(description = "Market ticker", required = true) 
            @RequestParam String ticker,
            @Parameter(description = "Depth of orderbook") 
            @RequestParam(required = false, defaultValue = "10") Integer depth) {
        
        try {
            // Get orderbook from market data service
            Orderbook orderbook = marketDataService.getMarketOrderbook(ticker, depth);
            
            return ResponseEntity.ok(new OrderbookResponse(orderbook));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.status(500).build();
        }
    }
    
    @GetMapping("/trades")
    @Operation(summary = "Get recent trades", 
               description = "Returns recent trades for a market specified by ticker query parameter")
    public ResponseEntity<?> getTradesByParam(
            @Parameter(description = "Market ticker", required = true) 
            @RequestParam String ticker,
            @Parameter(description = "Limit") 
            @RequestParam(required = false, defaultValue = "100") Integer limit) {
        
        try {
            // For now, return empty trades list
            return ResponseEntity.ok().body("{\"trades\":[]}");
        } catch (Exception e) {
            return ResponseEntity.status(500).build();
        }
    }
}