package com.kalshi.mock.controller;

import com.fbg.api.rest.*;
import com.fbg.api.market.KalshiSide;
import com.kalshi.mock.dto.KalshiOrderRequest;
import com.kalshi.mock.service.OrderBookService;
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
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
@RequestMapping("/trade-api/v2/portfolio")
@Tag(name = "Orders", description = "Order management endpoints")
@SecurityRequirement(name = "ApiKeyAuth")
public class OrderController {
    
    private static final Logger logger = LoggerFactory.getLogger(OrderController.class);
    
    @Autowired
    private OrderBookService orderBookService;
    
    // For demo purposes, using a fixed user ID
    private static final String DEMO_USER_ID = "USER-DEMO-001";
    
    @PostMapping("/orders")
    @Operation(summary = "Create new order", description = "Submit a new order to the market")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Order created successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid order request"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "404", description = "Market not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<OrderResponse> createOrder(
            @Valid @RequestBody KalshiOrderRequest kalshiRequest,
            @RequestHeader(value = "KALSHI-ACCESS-KEY", required = false) String apiKey) {
        
        try {
            // Convert Kalshi request to internal OrderRequest format
            OrderRequest orderRequest = new OrderRequest(
                KalshiSide.valueOf(kalshiRequest.getSide()),
                kalshiRequest.getMarketTicker(),
                kalshiRequest.getType(),
                kalshiRequest.getCount(),
                kalshiRequest.getPrice(),
                kalshiRequest.getTimeInForce(),
                kalshiRequest.getClientOrderId()
            );
            
            // Create order through order book service with action
            Order order = orderBookService.createOrder(
                kalshiRequest.getMarketTicker(),
                orderRequest,
                kalshiRequest.getAction(),
                DEMO_USER_ID
            );
            
            return ResponseEntity.status(HttpStatus.CREATED).body(new OrderResponse(order));
        } catch (IllegalArgumentException e) {
            logger.error("Bad request creating order: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            logger.error("Error creating order", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @GetMapping("/orders")
    @Operation(summary = "Get user orders", description = "Returns a list of user's orders")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Orders retrieved successfully"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<OrdersResponse> getUserOrders(
            @Parameter(description = "Filter by market ticker") @RequestParam(required = false) String ticker,
            @Parameter(description = "Filter by order status") @RequestParam(required = false) String status,
            @Parameter(description = "Maximum number of orders to return") @RequestParam(required = false, defaultValue = "100") Integer limit,
            @RequestHeader(value = "KALSHI-ACCESS-KEY", required = false) String apiKey) {
        
        List<Order> orders = orderBookService.getUserOrders(DEMO_USER_ID);
        
        // Apply filters
        if (ticker != null) {
            orders = orders.stream()
                .filter(o -> o.getSymbol().equals(ticker))
                .toList();
        }
        
        if (status != null) {
            orders = orders.stream()
                .filter(o -> o.getStatus().equals(status))
                .toList();
        }
        
        // Apply limit
        if (limit != null && orders.size() > limit) {
            orders = orders.subList(0, limit);
        }
        
        return ResponseEntity.ok(new OrdersResponse(orders, null));
    }
    
    @GetMapping("/orders/{order_id}")
    @Operation(summary = "Get order by ID", description = "Returns details of a specific order")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Order found"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "404", description = "Order not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<OrderResponse> getOrder(
            @Parameter(description = "Order ID", required = true) @PathVariable String order_id,
            @RequestHeader(value = "KALSHI-ACCESS-KEY", required = false) String apiKey) {
        
        try {
            Order order = orderBookService.getOrder(order_id);
            
            // Verify order belongs to user (in real implementation)
            if (!order.getUser_id().equals(DEMO_USER_ID)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
            
            return ResponseEntity.ok(new OrderResponse(order));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }
    
    @DeleteMapping("/orders/{order_id}")
    @Operation(summary = "Cancel order", description = "Cancel an existing order")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Order canceled successfully"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "404", description = "Order not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<OrderResponse> cancelOrder(
            @Parameter(description = "Order ID", required = true) @PathVariable String order_id,
            @RequestHeader(value = "KALSHI-ACCESS-KEY", required = false) String apiKey) {
        
        try {
            // Verify order exists and belongs to user
            Order existingOrder = orderBookService.getOrder(order_id);
            if (!existingOrder.getUser_id().equals(DEMO_USER_ID)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
            
            Order canceledOrder = orderBookService.cancelOrder(order_id);
            return ResponseEntity.ok(new OrderResponse(canceledOrder));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @PostMapping("/batch_orders")
    @Operation(summary = "Create batch orders", description = "Submit multiple orders in a single request")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Orders created successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid order request"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<BatchOrderResponse> createBatchOrders(
            @Valid @RequestBody List<KalshiOrderRequest> batchRequests,
            @RequestHeader(value = "KALSHI-ACCESS-KEY", required = false) String apiKey) {
        
        List<Order> createdOrders = new java.util.ArrayList<>();
        
        for (KalshiOrderRequest kalshiRequest : batchRequests) {
            try {
                OrderRequest orderRequest = new OrderRequest(
                    KalshiSide.valueOf(kalshiRequest.getSide()),
                    kalshiRequest.getMarketTicker(),
                    kalshiRequest.getType(),
                    kalshiRequest.getCount(),
                    kalshiRequest.getPrice(),
                    kalshiRequest.getTimeInForce(),
                    kalshiRequest.getClientOrderId()
                );
                
                Order order = orderBookService.createOrder(
                    kalshiRequest.getMarketTicker(),
                    orderRequest,
                    kalshiRequest.getAction(),
                    DEMO_USER_ID
                );
                createdOrders.add(order);
            } catch (Exception e) {
                // Log error but continue processing other orders
                System.err.println("Failed to create order: " + e.getMessage());
            }
        }
        
        return ResponseEntity.status(HttpStatus.CREATED).body(new BatchOrderResponse(createdOrders));
    }
    
    @GetMapping("/fills")
    @Operation(summary = "Get user fills", description = "Returns a list of user's fills (executed orders)")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Fills retrieved successfully"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<FillsResponse> getUserFills(
            @Parameter(description = "Filter by market ticker") @RequestParam(required = false) String ticker,
            @Parameter(description = "Filter by order ID") @RequestParam(required = false) String order_id,
            @Parameter(description = "Maximum number of fills to return") @RequestParam(required = false, defaultValue = "100") Integer limit,
            @RequestHeader(value = "KALSHI-ACCESS-KEY", required = false) String apiKey) {
        
        List<Fill> fills = orderBookService.getUserFills(DEMO_USER_ID);
        
        // Apply filters
        if (ticker != null) {
            fills = fills.stream()
                .filter(f -> f.getMarket_ticker().equals(ticker))
                .toList();
        }
        
        if (order_id != null) {
            fills = fills.stream()
                .filter(f -> f.getOrder_id().equals(order_id))
                .toList();
        }
        
        // Apply limit
        if (limit != null && fills.size() > limit) {
            fills = fills.subList(0, limit);
        }
        
        return ResponseEntity.ok(new FillsResponse(fills, null));
    }
}