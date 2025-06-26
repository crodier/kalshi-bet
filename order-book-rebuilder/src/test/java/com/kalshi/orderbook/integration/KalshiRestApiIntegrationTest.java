package com.kalshi.orderbook.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kalshi.orderbook.model.OrderBook;
import com.kalshi.orderbook.service.OrderBookManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class KalshiRestApiIntegrationTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @Autowired
    private OrderBookManager orderBookManager;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @BeforeEach
    void setUp() {
        // Create test order book
        OrderBook testBook = orderBookManager.getOrCreateOrderBook("SELF_TEST_MARKET");
        
        // Add some test data
        testBook.updateYesSide(55, 1000, System.currentTimeMillis());
        testBook.updateYesSide(54, 800, System.currentTimeMillis());
        testBook.updateYesSide(53, 600, System.currentTimeMillis());
        
        testBook.updateNoSide(45, 1500, System.currentTimeMillis());
        testBook.updateNoSide(46, 1200, System.currentTimeMillis());
        testBook.updateNoSide(47, 900, System.currentTimeMillis());
    }
    
    @Test
    public void testKalshiOrderBookEndpoint() throws Exception {
        mockMvc.perform(get("/trade-api/v2/markets/SELF_TEST_MARKET/orderbook")
                .param("depth", "3"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/json"))
                .andExpect(jsonPath("$.orderbook.market_ticker").value("SELF_TEST_MARKET"))
                .andExpect(jsonPath("$.orderbook.yes").isArray())
                .andExpect(jsonPath("$.orderbook.no").isArray())
                .andExpect(jsonPath("$.orderbook.yes[0][0]").value(55)) // Best yes price
                .andExpect(jsonPath("$.orderbook.yes[0][1]").value(1000)) // Best yes size
                .andExpect(jsonPath("$.orderbook.no[0][0]").value(45)) // Best no price
                .andExpect(jsonPath("$.orderbook.no[0][1]").value(1500)); // Best no size
    }
    
    @Test
    public void testKalshiOrderBookAlternativeEndpoint() throws Exception {
        mockMvc.perform(get("/trade-api/v2/orderbook")
                .param("ticker", "SELF_TEST_MARKET")
                .param("depth", "2"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/json"))
                .andExpect(jsonPath("$.orderbook.market_ticker").value("SELF_TEST_MARKET"))
                .andExpect(jsonPath("$.orderbook.yes").isArray())
                .andExpect(jsonPath("$.orderbook.no").isArray())
                .andExpect(jsonPath("$.orderbook.yes.length()").value(2)) // Only 2 levels requested
                .andExpect(jsonPath("$.orderbook.no.length()").value(2));
    }
    
    @Test
    public void testOrderBookNotFound() throws Exception {
        mockMvc.perform(get("/trade-api/v2/markets/NONEXISTENT_MARKET/orderbook"))
                .andExpect(status().isNotFound());
    }
}