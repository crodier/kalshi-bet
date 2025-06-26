package com.kalshi.mock.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fbg.api.rest.Order;
import com.fbg.api.rest.OrderResponse;
import com.fbg.api.rest.OrdersResponse;
import com.kalshi.mock.dto.KalshiOrderRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.springframework.test.context.TestPropertySource;
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
public class OrderControllerIntegrationTest {
    
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
    @DisplayName("Create Order - Success")
    public void testCreateOrder() throws Exception {
        KalshiOrderRequest orderRequest = new KalshiOrderRequest();
        orderRequest.setMarketTicker("TRUMPWIN-24NOV05");
        orderRequest.setSide("yes");
        orderRequest.setAction("buy");
        orderRequest.setType("limit");
        orderRequest.setCount(100);
        orderRequest.setPrice(65);
        orderRequest.setTimeInForce("GTC");
        
        MvcResult result = mockMvc.perform(post("/trade-api/v2/portfolio/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(orderRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.order.id").exists())
                .andExpect(jsonPath("$.order.user_id").value("USER-DEMO-001"))
                .andExpect(jsonPath("$.order.side").value("yes"))
                .andExpect(jsonPath("$.order.symbol").value("TRUMPWIN-24NOV05"))
                .andExpect(jsonPath("$.order.order_type").value("limit"))
                .andExpect(jsonPath("$.order.quantity").value(100))
                .andExpect(jsonPath("$.order.price").value(65))
                .andExpect(jsonPath("$.order.status").value("open"))
                .andReturn();
        
        // Extract order ID for later tests
        String responseBody = result.getResponse().getContentAsString();
        OrderResponse orderResponse = objectMapper.readValue(responseBody, OrderResponse.class);
        String orderId = orderResponse.getOrder().getId();
        assertNotNull(orderId);
        assertTrue(orderId.startsWith("ORD-"));
    }
    
    @Test
    @DisplayName("Create Order - Invalid Price")
    public void testCreateOrderInvalidPrice() throws Exception {
        KalshiOrderRequest orderRequest = new KalshiOrderRequest();
        orderRequest.setMarketTicker("TRUMPWIN-24NOV05");
        orderRequest.setSide("yes");
        orderRequest.setAction("buy");
        orderRequest.setType("limit");
        orderRequest.setCount(100);
        orderRequest.setPrice(150); // Invalid price > 99
        
        mockMvc.perform(post("/trade-api/v2/portfolio/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(orderRequest)))
                .andExpect(status().isBadRequest());
    }
    
    @Test
    @DisplayName("Get User Orders")
    public void testGetUserOrders() throws Exception {
        // First create an order
        KalshiOrderRequest orderRequest = new KalshiOrderRequest();
        orderRequest.setMarketTicker("TRUMPWIN-24NOV05");
        orderRequest.setSide("yes");
        orderRequest.setAction("buy");
        orderRequest.setType("limit");
        orderRequest.setCount(50);
        orderRequest.setPrice(60);
        
        mockMvc.perform(post("/trade-api/v2/portfolio/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(orderRequest)))
                .andExpect(status().isCreated());
        
        // Then get all user orders
        MvcResult result = mockMvc.perform(get("/trade-api/v2/portfolio/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orders").isArray())
                .andExpect(jsonPath("$.orders[0].user_id").value("USER-DEMO-001"))
                .andReturn();
        
        String responseBody = result.getResponse().getContentAsString();
        OrdersResponse ordersResponse = objectMapper.readValue(responseBody, OrdersResponse.class);
        assertFalse(ordersResponse.getOrders().isEmpty());
    }
    
    @Test
    @DisplayName("Cancel Order - Success")
    public void testCancelOrder() throws Exception {
        // First create an order
        KalshiOrderRequest orderRequest = new KalshiOrderRequest();
        orderRequest.setMarketTicker("TRUMPWIN-24NOV05");
        orderRequest.setSide("no");
        orderRequest.setAction("buy");
        orderRequest.setType("limit");
        orderRequest.setCount(100);
        orderRequest.setPrice(30);
        
        MvcResult createResult = mockMvc.perform(post("/trade-api/v2/portfolio/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(orderRequest)))
                .andExpect(status().isCreated())
                .andReturn();
        
        OrderResponse createResponse = objectMapper.readValue(
            createResult.getResponse().getContentAsString(), 
            OrderResponse.class
        );
        String orderId = createResponse.getOrder().getId();
        
        // Then cancel the order
        mockMvc.perform(delete("/trade-api/v2/portfolio/orders/" + orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.order.id").value(orderId))
                .andExpect(jsonPath("$.order.status").value("canceled"));
    }
    
    @Test
    @DisplayName("Cancel Order - Not Found")
    public void testCancelOrderNotFound() throws Exception {
        mockMvc.perform(delete("/trade-api/v2/portfolio/orders/ORD-999999"))
                .andExpect(status().isNotFound());
    }
    
    @Test
    @DisplayName("Get Order by ID")
    public void testGetOrderById() throws Exception {
        // First create an order
        KalshiOrderRequest orderRequest = new KalshiOrderRequest();
        orderRequest.setMarketTicker("BTCZ-23DEC31-B50000");
        orderRequest.setSide("yes");
        orderRequest.setAction("sell");
        orderRequest.setType("limit");
        orderRequest.setCount(25);
        orderRequest.setPrice(75);
        orderRequest.setClientOrderId("TEST-001");
        
        MvcResult createResult = mockMvc.perform(post("/trade-api/v2/portfolio/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(orderRequest)))
                .andExpect(status().isCreated())
                .andReturn();
        
        OrderResponse createResponse = objectMapper.readValue(
            createResult.getResponse().getContentAsString(), 
            OrderResponse.class
        );
        String orderId = createResponse.getOrder().getId();
        
        // Then get the order by ID
        mockMvc.perform(get("/trade-api/v2/portfolio/orders/" + orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.order.id").value(orderId))
                .andExpect(jsonPath("$.order.client_order_id").value("TEST-001"))
                .andExpect(jsonPath("$.order.symbol").value("BTCZ-23DEC31-B50000"))
                .andExpect(jsonPath("$.order.side").value("yes"))
                .andExpect(jsonPath("$.order.price").value(75));
    }
    
    @Test
    @DisplayName("Create Batch Orders")
    public void testCreateBatchOrders() throws Exception {
        KalshiOrderRequest order1 = new KalshiOrderRequest();
        order1.setMarketTicker("TRUMPWIN-24NOV05");
        order1.setSide("yes");
        order1.setAction("buy");
        order1.setType("limit");
        order1.setCount(50);
        order1.setPrice(60);
        
        KalshiOrderRequest order2 = new KalshiOrderRequest();
        order2.setMarketTicker("TRUMPWIN-24NOV05");
        order2.setSide("yes");
        order2.setAction("buy");
        order2.setType("limit");
        order2.setCount(50);
        order2.setPrice(61);
        
        mockMvc.perform(post("/trade-api/v2/portfolio/batch_orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new KalshiOrderRequest[]{order1, order2})))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orders").isArray())
                .andExpect(jsonPath("$.orders.length()").value(2))
                .andExpect(jsonPath("$.orders[0].price").value(60))
                .andExpect(jsonPath("$.orders[1].price").value(61));
    }
    
    @Test
    @DisplayName("Get User Fills")
    public void testGetUserFills() throws Exception {
        // First create two crossing orders to generate fills
        // Buy YES at 70
        KalshiOrderRequest buyOrder = new KalshiOrderRequest();
        buyOrder.setMarketTicker("TRUMPWIN-24NOV05");
        buyOrder.setSide("yes");
        buyOrder.setAction("buy");
        buyOrder.setType("limit");
        buyOrder.setCount(100);
        buyOrder.setPrice(70);
        
        mockMvc.perform(post("/trade-api/v2/portfolio/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(buyOrder)))
                .andExpect(status().isCreated());
        
        // Sell YES at 70 (should match and create fills)
        KalshiOrderRequest sellOrder = new KalshiOrderRequest();
        sellOrder.setMarketTicker("TRUMPWIN-24NOV05");
        sellOrder.setSide("yes");
        sellOrder.setAction("sell");
        sellOrder.setType("limit");
        sellOrder.setCount(100);
        sellOrder.setPrice(70);
        
        mockMvc.perform(post("/trade-api/v2/portfolio/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(sellOrder)))
                .andDo(result -> {
                    if (result.getResponse().getStatus() != 201) {
                        System.out.println("Error response: " + result.getResponse().getContentAsString());
                    }
                })
                .andExpect(status().isCreated());
        
        // Get fills
        mockMvc.perform(get("/trade-api/v2/portfolio/fills"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fills").isArray());
    }
}