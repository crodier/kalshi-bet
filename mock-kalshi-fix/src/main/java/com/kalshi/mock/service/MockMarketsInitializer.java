package com.kalshi.mock.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.Map;

/**
 * Initializes mock markets and order books on application startup.
 * Loads markets from database and creates corresponding order books.
 */
@Component
@Order(1) // Run early in startup sequence
public class MockMarketsInitializer implements ApplicationRunner {
    
    private static final Logger logger = LoggerFactory.getLogger(MockMarketsInitializer.class);
    
    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    @Autowired
    private OrderBookService orderBookService;
    
    @Autowired
    private PersistenceService persistenceService;
    
    @Override
    public void run(ApplicationArguments args) throws Exception {
        logger.info("Initializing mock markets and order books...");
        
        try {
            // Load all active markets from database
            String sql = "SELECT ticker FROM markets WHERE status = 'open'";
            List<String> marketTickers = jdbcTemplate.queryForList(sql, String.class);
            
            logger.info("Found {} active markets in database", marketTickers.size());
            
            if (marketTickers.isEmpty()) {
                logger.warn("No active markets found in database. Make sure data.sql has been executed.");
                return;
            }
            
            // Create order book for each market
            for (String ticker : marketTickers) {
                logger.info("Creating order book for market: {}", ticker);
                
                // Create the order book
                orderBookService.createOrderBook(ticker);
                
                // The order book service will automatically load existing orders
                // from the database via loadOpenOrdersForMarket
                
                logger.info("Order book created for market: {}", ticker);
            }
            
            // Log summary
            logger.info("Market initialization complete. Created {} order books.", marketTickers.size());
            
            // Log order book status for each market
            for (String ticker : marketTickers) {
                com.fbg.api.rest.Orderbook orderbook = orderBookService.getOrderbook(ticker);
                
                int buyOrders = orderbook.getYes() != null ? orderbook.getYes().size() : 0;
                int sellOrders = orderbook.getNo() != null ? orderbook.getNo().size() : 0;
                
                logger.info("Market {} orderbook - Buy levels: {}, Sell levels: {}", 
                    ticker, buyOrders, sellOrders);
            }
            
        } catch (Exception e) {
            logger.error("Error initializing markets", e);
            throw e;
        }
    }
    
    /**
     * Verifies database connectivity and table existence on construction
     */
    @PostConstruct
    public void verifyDatabaseSetup() {
        try {
            Long marketCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM markets", Long.class);
            Long eventCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM events", Long.class);
            Long seriesCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM series", Long.class);
            
            logger.info("Database verification - Series: {}, Events: {}, Markets: {}", 
                seriesCount, eventCount, marketCount);
                
            if (marketCount == 0) {
                logger.warn("No markets found in database. The data.sql script may not have run yet.");
            }
        } catch (Exception e) {
            logger.error("Error verifying database setup", e);
        }
    }
}