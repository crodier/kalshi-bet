package com.kalshi.mock.integration;

import com.kalshi.mock.service.OrderBookService;
import org.springframework.stereotype.Component;

@Component
public class TestHelper {
    
    private final OrderBookService orderBookService;
    
    public TestHelper(OrderBookService orderBookService) {
        this.orderBookService = orderBookService;
    }
    
    /**
     * Initialize a test market in the order book service
     * Note: The OrderBookService already initializes some test markets including TRUMPWIN-24NOV05
     * This method is kept for future enhancements
     */
    public void initializeTestMarket(String marketTicker) {
        // The OrderBookService already initializes test markets in @PostConstruct
        // including TRUMPWIN-24NOV05, BTCZ-23DEC31-B50000, etc.
        // For now, this is a no-op as the markets are already initialized
    }
}