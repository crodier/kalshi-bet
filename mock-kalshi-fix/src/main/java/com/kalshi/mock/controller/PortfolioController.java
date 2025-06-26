package com.kalshi.mock.controller;

import com.fbg.api.rest.*;
import com.fbg.api.market.KalshiSide;
import com.kalshi.mock.service.PositionsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/trade-api/v2/portfolio")
@Tag(name = "Portfolio", description = "Portfolio management endpoints")
@SecurityRequirement(name = "ApiKeyAuth")
public class PortfolioController {
    
    @Autowired
    private PositionsService positionsService;
    
    // For demo purposes, using a fixed user ID
    private static final String DEMO_USER_ID = "USER-DEMO-001";
    
    @GetMapping("/balance")
    @Operation(summary = "Get account balance", description = "Returns the user's account balance information")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Balance retrieved successfully"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<BalanceResponse> getBalance(
            @RequestHeader(value = "KALSHI-ACCESS-KEY", required = false) String apiKey) {
        
        // Mock balance data
        Balance balance = new Balance(
            1000000L, // total_balance ($10,000.00 in cents)
            950000L,  // available_balance ($9,500.00 in cents)
            950000L,  // clearing_balance
            900000L   // withdrawable_balance ($9,000.00 in cents)
        );
        
        return ResponseEntity.ok(new BalanceResponse(balance));
    }
    
    @GetMapping("/positions")
    @Operation(summary = "Get open positions", description = "Returns the user's open positions")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Positions retrieved successfully"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<PositionsResponse> getPositions(
            @RequestHeader(value = "KALSHI-ACCESS-KEY", required = false) String apiKey) {
        
        // Get positions from the positions service
        List<Position> positions = positionsService.getUserPositions(DEMO_USER_ID);
        
        return ResponseEntity.ok(new PositionsResponse(positions));
    }
    
    @GetMapping("/settlements")
    @Operation(summary = "Get settlement history", description = "Returns the user's settlement history")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Settlements retrieved successfully"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<SettlementsResponse> getSettlements(
            @RequestParam(required = false) String ticker,
            @RequestParam(required = false, defaultValue = "100") Integer limit,
            @RequestHeader(value = "KALSHI-ACCESS-KEY", required = false) String apiKey) {
        
        // Mock settlements data
        List<Settlement> settlements = new ArrayList<>();
        
        // Add some test settlements
        settlements.add(new Settlement(
            "SET-001",
            "MKT-ELECTION-2020",
            "BIDENWIN-20NOV03",
            KalshiSide.yes,
            100,  // quantity
            100,  // price (settled at $1.00)
            3500, // pnl ($35.00 profit)
            System.currentTimeMillis() - 86400000L * 30, // created 30 days ago
            System.currentTimeMillis() - 86400000L * 28  // settled 28 days ago
        ));
        
        return ResponseEntity.ok(new SettlementsResponse(settlements, null));
    }
}