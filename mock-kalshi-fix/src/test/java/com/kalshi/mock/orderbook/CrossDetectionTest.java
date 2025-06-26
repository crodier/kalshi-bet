package com.kalshi.mock.orderbook;

import com.fbg.api.market.KalshiSide;
import com.kalshi.mock.model.ConcurrentOrderBook;
import com.kalshi.mock.model.OrderBookEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for cross detection in the order book.
 * 
 * Two types of crosses:
 * 1. Self-cross: When bid >= ask on the same side (YES or NO)
 * 2. External cross: When YES bid + NO bid > 100¢ (arbitrage opportunity)
 */
public class CrossDetectionTest {
    
    private ConcurrentOrderBook orderBook;
    private AtomicBoolean crossDetected;
    
    @BeforeEach
    public void setUp() {
        orderBook = new ConcurrentOrderBook("TEST-MARKET");
        crossDetected = new AtomicBoolean(false);
        
        // Add listener to detect crosses
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
            }
        });
    }
    
    @Test
    @DisplayName("Self-cross: YES bid 65¢ crosses YES ask 64¢")
    public void testSelfCrossYESBidHigherThanAsk() {
        // Add YES ask at 64¢
        OrderBookEntry ask = new OrderBookEntry(
            "ORDER-1",
            "USER-1",
            KalshiSide.yes,
            "sell",
            64,
            100,
            System.currentTimeMillis()
        );
        orderBook.addOrder(ask);
        assertFalse(crossDetected.get(), "Adding first order should not trigger cross");
        
        // Add YES bid at 65¢ - should cross
        OrderBookEntry bid = new OrderBookEntry(
            "ORDER-2",
            "USER-2",
            KalshiSide.yes,
            "buy",
            65,
            100,
            System.currentTimeMillis() + 1
        );
        orderBook.addOrder(bid);
        assertTrue(crossDetected.get(), "YES bid 65¢ should cross YES ask 64¢");
    }
    
    @Test
    @DisplayName("Self-cross: NO bid 36¢ crosses NO ask 35¢")
    public void testSelfCrossNOBidHigherThanAsk() {
        crossDetected.set(false);
        
        // Add NO ask at 35¢ (converts to Buy YES at 65¢)
        OrderBookEntry noAsk = new OrderBookEntry(
            "ORDER-3",
            "USER-1",
            KalshiSide.no,
            "sell",
            35,
            100,
            System.currentTimeMillis()
        );
        orderBook.addOrder(noAsk);
        assertFalse(crossDetected.get());
        
        // Add NO bid at 36¢ (converts to Sell YES at 64¢)
        // This creates: Buy YES 65¢ vs Sell YES 64¢ - CROSSED!
        OrderBookEntry noBid = new OrderBookEntry(
            "ORDER-4",
            "USER-2",
            KalshiSide.no,
            "buy",
            36,
            100,
            System.currentTimeMillis() + 1
        );
        orderBook.addOrder(noBid);
        assertTrue(crossDetected.get(), "NO bid 36¢ should cross NO ask 35¢ (via YES conversion)");
    }
    
    @Test
    @DisplayName("External cross: YES bid 65¢ + NO bid 40¢ = 105¢ > 100¢")
    public void testExternalCrossArbitrage() {
        crossDetected.set(false);
        
        // Add YES bid at 65¢
        OrderBookEntry yesBid = new OrderBookEntry(
            "ORDER-5",
            "USER-1",
            KalshiSide.yes,
            "buy",
            65,
            100,
            System.currentTimeMillis()
        );
        orderBook.addOrder(yesBid);
        assertFalse(crossDetected.get());
        
        // Add NO bid at 40¢ (converts to Sell YES at 60¢)
        // Now we have YES bid 65¢ + NO bid 40¢ = 105¢ > 100¢
        OrderBookEntry noBid = new OrderBookEntry(
            "ORDER-6",
            "USER-2",
            KalshiSide.no,
            "buy",
            40,
            100,
            System.currentTimeMillis() + 1
        );
        orderBook.addOrder(noBid);
        assertTrue(crossDetected.get(), "YES bid 65¢ + NO bid 40¢ should create arbitrage opportunity");
    }
    
    @Test
    @DisplayName("No cross: Healthy spread YES bid 64¢ vs YES ask 66¢")
    public void testNoCrossHealthySpread() {
        crossDetected.set(false);
        
        // Add YES bid at 64¢
        OrderBookEntry bid = new OrderBookEntry(
            "ORDER-7",
            "USER-1",
            KalshiSide.yes,
            "buy",
            64,
            100,
            System.currentTimeMillis()
        );
        orderBook.addOrder(bid);
        
        // Add YES ask at 66¢
        OrderBookEntry ask = new OrderBookEntry(
            "ORDER-8",
            "USER-2",
            KalshiSide.yes,
            "sell",
            66,
            100,
            System.currentTimeMillis() + 1
        );
        orderBook.addOrder(ask);
        
        assertFalse(crossDetected.get(), "Healthy spread should not trigger cross");
    }
    
    @Test
    @DisplayName("No external cross: YES bid 60¢ + NO bid 39¢ = 99¢ < 100¢")
    public void testNoExternalCross() {
        crossDetected.set(false);
        
        // Add YES bid at 60¢
        OrderBookEntry yesBid = new OrderBookEntry(
            "ORDER-9",
            "USER-1",
            KalshiSide.yes,
            "buy",
            60,
            100,
            System.currentTimeMillis()
        );
        orderBook.addOrder(yesBid);
        
        // Add NO bid at 39¢
        OrderBookEntry noBid = new OrderBookEntry(
            "ORDER-10",
            "USER-2",
            KalshiSide.no,
            "buy",
            39,
            100,
            System.currentTimeMillis() + 1
        );
        orderBook.addOrder(noBid);
        
        assertFalse(crossDetected.get(), "YES bid 60¢ + NO bid 39¢ = 99¢ should not cross");
    }
    
    @Test
    @DisplayName("Edge case: YES bid 50¢ + NO bid 50¢ = 100¢ creates self-cross, not arbitrage")
    public void testExactly100CreatesSelfCross() {
        crossDetected.set(false);
        
        // Add YES bid at 50¢
        OrderBookEntry yesBid = new OrderBookEntry(
            "ORDER-11",
            "USER-1",
            KalshiSide.yes,
            "buy",
            50,
            100,
            System.currentTimeMillis()
        );
        orderBook.addOrder(yesBid);
        
        // Add NO bid at 50¢ (converts to Sell YES at 50¢)
        // This creates: Buy YES 50¢ vs Sell YES 50¢ - SELF CROSS!
        OrderBookEntry noBid = new OrderBookEntry(
            "ORDER-12",
            "USER-2",
            KalshiSide.no,
            "buy",
            50,
            100,
            System.currentTimeMillis() + 1
        );
        orderBook.addOrder(noBid);
        
        assertTrue(crossDetected.get(), "YES bid 50¢ meets YES ask 50¢ (from NO bid) - self cross");
    }
    
    @Test
    @DisplayName("Complex scenario: Multiple orders with cross detection")
    public void testComplexScenarioWithMultipleOrders() {
        crossDetected.set(false);
        
        // Build order book
        // YES bids: 63, 62, 61
        orderBook.addOrder(new OrderBookEntry("O1", "U1", KalshiSide.yes, "buy", 63, 100, 1000));
        orderBook.addOrder(new OrderBookEntry("O2", "U2", KalshiSide.yes, "buy", 62, 200, 1001));
        orderBook.addOrder(new OrderBookEntry("O3", "U3", KalshiSide.yes, "buy", 61, 150, 1002));
        
        // YES asks: 67, 68, 69
        orderBook.addOrder(new OrderBookEntry("O4", "U4", KalshiSide.yes, "sell", 67, 100, 1003));
        orderBook.addOrder(new OrderBookEntry("O5", "U5", KalshiSide.yes, "sell", 68, 200, 1004));
        orderBook.addOrder(new OrderBookEntry("O6", "U6", KalshiSide.yes, "sell", 69, 150, 1005));
        
        assertFalse(crossDetected.get(), "Initial orders should not cross");
        
        // Add NO bid at 38¢ (converts to Sell YES at 62¢)
        // YES bid 63¢ + NO bid 38¢ = 101¢ > 100¢ - ARBITRAGE!
        OrderBookEntry noBid = new OrderBookEntry(
            "O7",
            "U7",
            KalshiSide.no,
            "buy",
            38,
            100,
            1006
        );
        orderBook.addOrder(noBid);
        assertTrue(crossDetected.get(), "Adding NO bid 38¢ should create arbitrage with YES bid 63¢");
    }
}