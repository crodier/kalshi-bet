package com.kalshi.mock.controller;

import com.fbg.api.rest.Order;
import com.fbg.api.rest.OrdersResponse;
import com.kalshi.mock.service.OrderTrackingService;
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

import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
@RequestMapping("/trade-api/v2/order-tracking")
@Tag(name = "Order Tracking", description = "Order tracking and statistics endpoints")
@SecurityRequirement(name = "ApiKeyAuth")
public class OrderTrackingController {
    
    private static final Logger logger = LoggerFactory.getLogger(OrderTrackingController.class);
    
    @Autowired
    private OrderTrackingService orderTrackingService;
    
    @GetMapping("/market/{market_ticker}")
    @Operation(summary = "Get orders for market", description = "Returns all orders for a specific market")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Orders retrieved successfully"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "404", description = "Market not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<OrdersResponse> getOrdersForMarket(
            @Parameter(description = "Market ticker", required = true) @PathVariable String market_ticker,
            @Parameter(description = "Filter by status") @RequestParam(required = false) String status,
            @Parameter(description = "Maximum number of orders to return") @RequestParam(required = false, defaultValue = "100") Integer limit) {
        
        try {
            List<Order> orders = orderTrackingService.getOrdersForMarket(market_ticker);
            
            // Apply status filter
            if (status != null) {
                orders = orders.stream()
                    .filter(o -> o.getStatus().equals(status))
                    .toList();
            }
            
            // Apply limit
            if (limit != null && orders.size() > limit) {
                orders = orders.subList(0, limit);
            }
            
            logger.info("Retrieved {} orders for market {}", orders.size(), market_ticker);
            return ResponseEntity.ok(new OrdersResponse(orders, null));
        } catch (Exception e) {
            logger.error("Failed to retrieve orders for market {}", market_ticker, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @GetMapping("/statistics")
    @Operation(summary = "Get order statistics", description = "Returns order count statistics")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Statistics retrieved successfully"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<Map<String, Integer>> getOrderStatistics() {
        
        try {
            Map<String, Integer> stats = orderTrackingService.getOrderStatistics();
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            logger.error("Failed to retrieve order statistics", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @GetMapping("/user/{user_id}")
    @Operation(summary = "Get orders for user", description = "Returns all orders for a specific user")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Orders retrieved successfully"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "404", description = "User not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<OrdersResponse> getOrdersForUser(
            @Parameter(description = "User ID", required = true) @PathVariable String user_id,
            @Parameter(description = "Filter by status") @RequestParam(required = false) String status,
            @Parameter(description = "Maximum number of orders to return") @RequestParam(required = false, defaultValue = "100") Integer limit) {
        
        try {
            List<Order> orders = orderTrackingService.getOrdersForUser(user_id);
            
            // Apply status filter
            if (status != null) {
                orders = orders.stream()
                    .filter(o -> o.getStatus().equals(status))
                    .toList();
            }
            
            // Apply limit
            if (limit != null && orders.size() > limit) {
                orders = orders.subList(0, limit);
            }
            
            logger.info("Retrieved {} orders for user {}", orders.size(), user_id);
            return ResponseEntity.ok(new OrdersResponse(orders, null));
        } catch (Exception e) {
            logger.error("Failed to retrieve orders for user {}", user_id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}