package com.kalshi.mock.integration;

import com.kalshi.mock.dto.KalshiOrderRequest;
import com.kalshi.mock.controller.OrderController;
import com.fbg.api.rest.OrderResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class CrossingTestIntegration {
    
    @Autowired
    private TestRestTemplate restTemplate;
    
    @Test
    void testBuyYesCrossesWithBuyNo() {
        // First, place a Buy NO @ 30
        KalshiOrderRequest buyNo = new KalshiOrderRequest();
        buyNo.setMarketTicker("TEST-CROSS-MARKET");
        buyNo.setSide("no");
        buyNo.setAction("buy");
        buyNo.setType("limit");
        buyNo.setPrice(30);
        buyNo.setCount(10);
        buyNo.setTimeInForce("GTC");
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<KalshiOrderRequest> request1 = new HttpEntity<>(buyNo, headers);
        
        ResponseEntity<OrderResponse> response1 = restTemplate.postForEntity(
            "/trade-api/v2/portfolio/orders", request1, OrderResponse.class);
        
        System.out.println("Buy NO @ 30 order: " + response1.getBody().getOrder().getStatus());
        
        // Now place Buy YES @ 71 (should cross because 71 + 30 = 101 > 100)
        KalshiOrderRequest buyYes = new KalshiOrderRequest();
        buyYes.setMarketTicker("TEST-CROSS-MARKET");
        buyYes.setSide("yes");
        buyYes.setAction("buy");
        buyYes.setType("limit");
        buyYes.setPrice(71);
        buyYes.setCount(5);
        buyYes.setTimeInForce("GTC");
        
        HttpEntity<KalshiOrderRequest> request2 = new HttpEntity<>(buyYes, headers);
        
        ResponseEntity<OrderResponse> response2 = restTemplate.postForEntity(
            "/trade-api/v2/portfolio/orders", request2, OrderResponse.class);
        
        System.out.println("Buy YES @ 71 order: " + response2.getBody().getOrder().getStatus());
        System.out.println("Filled quantity: " + response2.getBody().getOrder().getFilled_quantity());
        
        // The Buy YES @ 71 should be filled because it crosses with Buy NO @ 30
        assertEquals("filled", response2.getBody().getOrder().getStatus());
        assertEquals(5, response2.getBody().getOrder().getFilled_quantity());
    }
    
    @Test
    void testSellConversionAndCrossing() {
        // Place a Buy YES @ 60
        KalshiOrderRequest buyYes = new KalshiOrderRequest();
        buyYes.setMarketTicker("TEST-SELL-CROSS");
        buyYes.setSide("yes");
        buyYes.setAction("buy");
        buyYes.setType("limit");
        buyYes.setPrice(60);
        buyYes.setCount(10);
        buyYes.setTimeInForce("GTC");
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<KalshiOrderRequest> request1 = new HttpEntity<>(buyYes, headers);
        
        ResponseEntity<OrderResponse> response1 = restTemplate.postForEntity(
            "/trade-api/v2/portfolio/orders", request1, OrderResponse.class);
        
        System.out.println("Buy YES @ 60 order: " + response1.getBody().getOrder().getStatus());
        
        // Now place Sell YES @ 39 (converts to Buy NO @ 61, crosses with Buy YES @ 60)
        KalshiOrderRequest sellYes = new KalshiOrderRequest();
        sellYes.setMarketTicker("TEST-SELL-CROSS");
        sellYes.setSide("yes");
        sellYes.setAction("sell");
        sellYes.setType("limit");
        sellYes.setPrice(39);
        sellYes.setCount(5);
        sellYes.setTimeInForce("GTC");
        
        HttpEntity<KalshiOrderRequest> request2 = new HttpEntity<>(sellYes, headers);
        
        ResponseEntity<OrderResponse> response2 = restTemplate.postForEntity(
            "/trade-api/v2/portfolio/orders", request2, OrderResponse.class);
        
        System.out.println("Sell YES @ 39 order: " + response2.getBody().getOrder().getStatus());
        System.out.println("Filled quantity: " + response2.getBody().getOrder().getFilled_quantity());
        System.out.println("Average fill price: " + response2.getBody().getOrder().getAvg_fill_price());
        
        // Should be filled because Sell YES @ 39 → Buy NO @ 61, and 61 + 60 = 121 > 100
        assertEquals("filled", response2.getBody().getOrder().getStatus());
    }
}