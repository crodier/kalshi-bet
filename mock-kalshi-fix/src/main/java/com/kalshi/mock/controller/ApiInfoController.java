package com.kalshi.mock.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/trade-api/v2")
@Tag(name = "API Info", description = "API information endpoints")
public class ApiInfoController {
    
    @GetMapping("/api/version")
    @Operation(summary = "Get API version", description = "Returns the current API version information")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Version information retrieved successfully")
    })
    public ResponseEntity<Map<String, Object>> getApiVersion() {
        Map<String, Object> version = new HashMap<>();
        version.put("version", "2.0");
        version.put("name", "Mock Kalshi Trading API");
        version.put("environment", "development");
        version.put("build", "1.0.0-SNAPSHOT");
        
        return ResponseEntity.ok(version);
    }
    
    @GetMapping("/health")
    @Operation(summary = "Health check", description = "Returns the health status of the API")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Service is healthy")
    })
    public ResponseEntity<Map<String, String>> healthCheck() {
        Map<String, String> health = new HashMap<>();
        health.put("status", "UP");
        health.put("timestamp", String.valueOf(System.currentTimeMillis()));
        
        return ResponseEntity.ok(health);
    }
}