package com.kalshi.mock.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kalshi.mock.dto.KalshiOrderRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.springframework.context.annotation.Import;
import com.kalshi.mock.config.TestDatabaseConfig;
import org.junit.jupiter.api.BeforeEach;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestDatabaseConfig.class)
@TestPropertySource(properties = {
    "kalshi.database.path=:memory:",
    "spring.datasource.driver-class-name=org.sqlite.JDBC",
    "spring.datasource.url=jdbc:sqlite::memory:",
    "spring.jpa.database-platform=org.hibernate.community.dialect.SQLiteDialect",
    "quickfix.enabled=false"
})
public class PortfolioControllerIntegrationTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @Autowired
    private TestHelper testHelper;
    
    @BeforeEach
    public void setUp() {
        // Initialize test market
        testHelper.initializeTestMarket("TRUMPWIN-24NOV05");
    }
    
    @Test
    @DisplayName("Get Balance")
    public void testGetBalance() throws Exception {
        mockMvc.perform(get("/trade-api/v2/portfolio/balance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance.total_balance").exists())
                .andExpect(jsonPath("$.balance.available_balance").exists())
                .andExpect(jsonPath("$.balance.clearing_balance").exists())
                .andExpect(jsonPath("$.balance.withdrawable_balance").exists());
    }
    
    @Test
    @DisplayName("Get Positions - Empty")
    public void testGetPositionsEmpty() throws Exception {
        // Initially should have no positions
        mockMvc.perform(get("/trade-api/v2/portfolio/positions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.positions").isArray());
    }
    
    @Test
    @DisplayName("Get Positions - After Trade")
    public void testGetPositionsAfterTrade() throws Exception {
        // Create two crossing orders to generate a position
        // Buy YES at 65
        KalshiOrderRequest buyOrder = new KalshiOrderRequest();
        buyOrder.setMarketTicker("TRUMPWIN-24NOV05");
        buyOrder.setSide("yes");
        buyOrder.setAction("buy");
        buyOrder.setType("limit");
        buyOrder.setCount(100);
        buyOrder.setPrice(65);
        
        mockMvc.perform(post("/trade-api/v2/portfolio/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(buyOrder)))
                .andExpect(status().isCreated());
        
        // Sell YES at 65 (should match)
        KalshiOrderRequest sellOrder = new KalshiOrderRequest();
        sellOrder.setMarketTicker("TRUMPWIN-24NOV05");
        sellOrder.setSide("yes");
        sellOrder.setAction("sell");
        sellOrder.setType("limit");
        sellOrder.setCount(100);
        sellOrder.setPrice(65);
        
        mockMvc.perform(post("/trade-api/v2/portfolio/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(sellOrder)))
                .andExpect(status().isCreated());
        
        // Check positions - should now have positions from the trade
        mockMvc.perform(get("/trade-api/v2/portfolio/positions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.positions").isArray());
    }
    
    @Test
    @DisplayName("Get Settlements")
    public void testGetSettlements() throws Exception {
        mockMvc.perform(get("/trade-api/v2/portfolio/settlements"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.settlements").isArray());
    }
    
    @Test
    @DisplayName("Get Settlements with Filters")
    public void testGetSettlementsWithFilters() throws Exception {
        mockMvc.perform(get("/trade-api/v2/portfolio/settlements")
                .param("ticker", "BIDENWIN-20NOV03")
                .param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.settlements").isArray());
    }
}