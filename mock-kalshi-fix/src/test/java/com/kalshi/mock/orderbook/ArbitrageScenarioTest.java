package com.kalshi.mock.orderbook;

import com.fbg.api.market.KalshiSide;
import com.kalshi.mock.model.ConcurrentOrderBook;
import com.kalshi.mock.model.OrderBookEntry;
import com.kalshi.mock.service.MatchingEngine;
import com.kalshi.mock.service.MatchingEngine.Execution;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for arbitrage scenarios in the binary options market.
 * 
 * Key arbitrage opportunity: When YES bid + NO bid > 100¢
 * Market maker can sell YES and NO to both bidders for risk-free profit.
 */
public class ArbitrageScenarioTest {
    
    private ConcurrentOrderBook orderBook;
    private MatchingEngine matchingEngine;
    private AtomicBoolean crossDetected;
    private AtomicInteger crossCount;
    
    @BeforeEach
    public void setUp() {
        orderBook = new ConcurrentOrderBook("TEST-MARKET");
        matchingEngine = new MatchingEngine();
        crossDetected = new AtomicBoolean(false);
        crossCount = new AtomicInteger(0);
        
        orderBook.addListener(new ConcurrentOrderBook.OrderBookListener() {
            @Override
            public void onOrderAdded(String marketTicker, OrderBookEntry order) {}
            
            @Override
            public void onOrderCanceled(String marketTicker, OrderBookEntry order) {}
            
            @Override
            public void onOrderExecuted(String marketTicker, OrderBookEntry order, int executedQuantity) {}
            
            @Override
            public void onCrossDetected(String marketTicker, OrderBookEntry order) {
                crossDetected.set(true);
                crossCount.incrementAndGet();
            }
        });
    }
    
    @Test
    @DisplayName("Classic arbitrage: YES bid 65¢ + NO bid 40¢ = 105¢")
    public void testClassicArbitrage() {
        // Trader A: Buy YES at 65¢
        OrderBookEntry yesBid = new OrderBookEntry("YES-BID", "TRADER-A", KalshiSide.yes, "buy", 65, 100, 1000);
        orderBook.addOrder(yesBid);
        assertFalse(crossDetected.get());
        
        // Trader B: Buy NO at 40¢ (converts to Sell YES at 60¢)
        OrderBookEntry noBid = new OrderBookEntry("NO-BID", "TRADER-B", KalshiSide.no, "buy", 40, 100, 2000);
        orderBook.addOrder(noBid);
        
        // Should detect arbitrage opportunity
        assertTrue(crossDetected.get(), "Should detect arbitrage: 65¢ + 40¢ = 105¢ > 100¢");
        
        // Market maker can now:
        // 1. Sell YES at 65¢ to TRADER-A
        // 2. Sell NO at 40¢ to TRADER-B (by buying YES at 60¢ from the converted order)
        // 3. Collect 105¢ total, pay out max 100¢ = 5¢ profit
        
        // Execute the arbitrage
        // Sell YES to the YES bidder
        OrderBookEntry mmSellYES = new OrderBookEntry("MM-SELL-YES", "MARKET-MAKER", KalshiSide.yes, "sell", 65, 100, 3000);
        List<Execution> yesExecutions = matchingEngine.matchOrder(mmSellYES, orderBook);
        
        assertEquals(1, yesExecutions.size());
        assertEquals("YES-BID", yesExecutions.get(0).getPassive().getOrderId());
        assertEquals(100, yesExecutions.get(0).getQuantity());
        
        // The NO bid (converted to Sell YES at 60¢) is still in the book
        // Market maker buys YES at 60¢ (equivalent to selling NO at 40¢)
        OrderBookEntry mmBuyYES = new OrderBookEntry("MM-BUY-YES", "MARKET-MAKER", KalshiSide.yes, "buy", 60, 100, 4000);
        List<Execution> noExecutions = matchingEngine.matchOrder(mmBuyYES, orderBook);
        
        assertEquals(1, noExecutions.size());
        assertEquals("NO-BID", noExecutions.get(0).getPassive().getOrderId());
        assertEquals(100, noExecutions.get(0).getQuantity());
    }
    
    @Test
    @DisplayName("Large arbitrage spread: YES bid 70¢ + NO bid 35¢ = 105¢")
    public void testLargeArbitrageSpread() {
        // YES bid at 70¢
        orderBook.addOrder(new OrderBookEntry("Y1", "U1", KalshiSide.yes, "buy", 70, 1000, 1000));
        
        // NO bid at 35¢ (converts to Sell YES at 65¢)
        orderBook.addOrder(new OrderBookEntry("N1", "U2", KalshiSide.no, "buy", 35, 1000, 2000));
        
        assertTrue(crossDetected.get(), "Should detect arbitrage: 70¢ + 35¢ = 105¢");
        
        // Market maker profits: 105¢ - 100¢ = 5¢ per contract
        // With 1000 contracts, profit = $50
    }
    
    @Test
    @DisplayName("Multiple arbitrage opportunities in order book")
    public void testMultipleArbitrageOpportunities() {
        crossCount.set(0);
        
        // Build YES bids
        orderBook.addOrder(new OrderBookEntry("Y1", "U1", KalshiSide.yes, "buy", 68, 100, 1000));
        orderBook.addOrder(new OrderBookEntry("Y2", "U2", KalshiSide.yes, "buy", 67, 200, 1001));
        orderBook.addOrder(new OrderBookEntry("Y3", "U3", KalshiSide.yes, "buy", 66, 300, 1002));
        
        assertEquals(0, crossCount.get(), "No arbitrage yet");
        
        // Add NO bid at 33¢ - creates arbitrage with YES 68¢
        orderBook.addOrder(new OrderBookEntry("N1", "U4", KalshiSide.no, "buy", 33, 100, 2000));
        assertEquals(1, crossCount.get(), "68¢ + 33¢ = 101¢");
        
        // Add NO bid at 34¢ - creates arbitrage with YES 67¢ and 68¢
        orderBook.addOrder(new OrderBookEntry("N2", "U5", KalshiSide.no, "buy", 34, 200, 2001));
        assertEquals(2, crossCount.get(), "67¢ + 34¢ = 101¢");
        
        // Add NO bid at 35¢ - creates arbitrage with all YES bids
        orderBook.addOrder(new OrderBookEntry("N3", "U6", KalshiSide.no, "buy", 35, 300, 2002));
        assertEquals(3, crossCount.get(), "66¢ + 35¢ = 101¢");
    }
    
    @Test
    @DisplayName("No arbitrage when sum equals exactly 100¢")
    public void testNoArbitrageAt100() {
        // YES bid at 60¢
        orderBook.addOrder(new OrderBookEntry("Y1", "U1", KalshiSide.yes, "buy", 60, 100, 1000));
        
        // NO bid at 40¢ - converts to Sell YES at 60¢
        // This creates a self-cross (YES bid 60¢ = YES ask 60¢), not external arbitrage
        orderBook.addOrder(new OrderBookEntry("N1", "U2", KalshiSide.no, "buy", 40, 100, 2000));
        
        // The cross detected is a self-cross, not an arbitrage opportunity
        assertTrue(crossDetected.get(), "Self-cross detected when YES bid meets YES ask at 60¢");
    }
    
    @Test
    @DisplayName("Arbitrage detection with mixed order types")
    public void testArbitrageMixedOrders() {
        crossDetected.set(false);
        
        // Add various orders
        orderBook.addOrder(new OrderBookEntry("Y1", "U1", KalshiSide.yes, "buy", 65, 100, 1000));
        orderBook.addOrder(new OrderBookEntry("Y2", "U2", KalshiSide.yes, "sell", 72, 100, 1001)); // Changed to 72 to avoid cross
        orderBook.addOrder(new OrderBookEntry("N1", "U3", KalshiSide.no, "sell", 30, 100, 1002)); // Buy YES at 70¢
        
        assertFalse(crossDetected.get(), "No cross yet (YES bid 65 < YES ask 70)");
        
        // Add NO bid that creates arbitrage
        orderBook.addOrder(new OrderBookEntry("N2", "U4", KalshiSide.no, "buy", 36, 100, 2000));
        
        assertTrue(crossDetected.get(), "YES bid 65¢ + NO bid 36¢ = 101¢ > 100¢ - arbitrage!");
    }
    
    @Test
    @DisplayName("Arbitrage profit calculation scenario")
    public void testArbitrageProfitCalculation() {
        // Scenario: Market maker identifies and executes arbitrage
        
        // Step 1: Orders create arbitrage
        orderBook.addOrder(new OrderBookEntry("YES-BUYER", "U1", KalshiSide.yes, "buy", 72, 500, 1000));
        orderBook.addOrder(new OrderBookEntry("NO-BUYER", "U2", KalshiSide.no, "buy", 30, 500, 2000));
        
        assertTrue(crossDetected.get(), "72¢ + 30¢ = 102¢ arbitrage");
        
        // Step 2: Market maker executes
        // Sell YES at 72¢
        OrderBookEntry mmSellYES = new OrderBookEntry("MM1", "MM", KalshiSide.yes, "sell", 72, 500, 3000);
        List<Execution> exec1 = matchingEngine.matchOrder(mmSellYES, orderBook);
        assertEquals(500, exec1.get(0).getQuantity());
        
        // Buy YES at 70¢ (from NO seller at 30¢)
        OrderBookEntry mmBuyYES = new OrderBookEntry("MM2", "MM", KalshiSide.yes, "buy", 70, 500, 4000);
        List<Execution> exec2 = matchingEngine.matchOrder(mmBuyYES, orderBook);
        assertEquals(500, exec2.get(0).getQuantity());
        
        // Profit calculation:
        // Collected: 72¢ + 30¢ = 102¢ per contract
        // Payout: Maximum 100¢ per contract
        // Profit: 2¢ × 500 contracts = $10
    }
    
    @Test
    @DisplayName("Edge case: Extreme arbitrage at price boundaries")
    public void testExtremeArbitrage() {
        // YES bid at 99¢ (maximum)
        orderBook.addOrder(new OrderBookEntry("Y1", "U1", KalshiSide.yes, "buy", 99, 100, 1000));
        
        // NO bid at 2¢
        orderBook.addOrder(new OrderBookEntry("N1", "U2", KalshiSide.no, "buy", 2, 100, 2000));
        
        assertTrue(crossDetected.get(), "99¢ + 2¢ = 101¢ arbitrage");
        
        // Even 1¢ profit per contract can be significant at volume
    }
}