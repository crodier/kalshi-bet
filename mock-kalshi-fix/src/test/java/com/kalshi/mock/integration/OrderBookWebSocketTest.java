package com.kalshi.mock.integration;

import com.kalshi.mock.controller.OrderController;
import com.kalshi.mock.dto.KalshiOrderRequest;
import com.kalshi.mock.event.OrderBookEvent;
import com.kalshi.mock.event.OrderBookEventListener;
import com.kalshi.mock.event.OrderBookEventPublisher;
import com.kalshi.mock.service.OrderBookService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@TestPropertySource(properties = {"fix.enabled=false"})
public class OrderBookWebSocketTest {

    @Autowired
    private OrderController orderController;
    
    @Autowired
    private OrderBookService orderBookService;
    
    @Autowired
    private OrderBookEventPublisher eventPublisher;
    
    private TestEventListener testListener;
    
    @BeforeEach
    void setup() {
        testListener = new TestEventListener();
        eventPublisher.addListener(testListener);
    }
    
    @Test
    void testTickerUpdatePublishedOnTrade() throws Exception {
        String marketTicker = "DUMMY_TEST";
        
        // Clear any existing events
        testListener.clear();
        
        // Create orders that will match
        KalshiOrderRequest buyOrder = new KalshiOrderRequest();
        buyOrder.setMarketTicker(marketTicker);
        buyOrder.setSide("yes");
        buyOrder.setAction("buy");
        buyOrder.setType("limit");
        buyOrder.setPrice(50);
        buyOrder.setCount(10);
        
        KalshiOrderRequest sellOrder = new KalshiOrderRequest();
        sellOrder.setMarketTicker(marketTicker);
        sellOrder.setSide("yes");
        sellOrder.setAction("sell");
        sellOrder.setType("limit");
        sellOrder.setPrice(50);
        sellOrder.setCount(10);
        
        // Place buy order
        orderController.createOrder(buyOrder, "USER-TEST-001");
        
        // Place sell order (should trigger trade and ticker update)
        orderController.createOrder(sellOrder, "USER-TEST-002");
        
        // Wait for events
        assertTrue(testListener.waitForTickerEvent(5), "Should receive ticker update event");
        
        // Verify ticker event
        OrderBookEvent tickerEvent = testListener.getLastTickerEvent();
        assertNotNull(tickerEvent);
        assertEquals(OrderBookEvent.EventType.TICKER_UPDATE, tickerEvent.getType());
        assertEquals(marketTicker, tickerEvent.getMarketTicker());
        
        OrderBookEvent.TickerData tickerData = (OrderBookEvent.TickerData) tickerEvent.getData();
        assertEquals(50, tickerData.getLastPrice());
        assertEquals(10, tickerData.getVolume());
    }
    
    @Test
    void testOrderBookSnapshotPublishedOnOrderAdd() throws Exception {
        String marketTicker = "DUMMY_TEST";
        
        // Clear any existing events
        testListener.clear();
        
        // Create a new order
        KalshiOrderRequest order = new KalshiOrderRequest();
        order.setMarketTicker(marketTicker);
        order.setSide("yes");
        order.setAction("buy");
        order.setType("limit");
        order.setPrice(45);
        order.setCount(20);
        
        // Place order
        orderController.createOrder(order, "USER-TEST-003");
        
        // Wait for snapshot event
        assertTrue(testListener.waitForSnapshotEvent(5), "Should receive orderbook snapshot event");
        
        // Verify snapshot event
        OrderBookEvent snapshotEvent = testListener.getLastSnapshotEvent();
        assertNotNull(snapshotEvent);
        assertEquals(OrderBookEvent.EventType.SNAPSHOT, snapshotEvent.getType());
        assertEquals(marketTicker, snapshotEvent.getMarketTicker());
        
        OrderBookEvent.SnapshotData snapshotData = (OrderBookEvent.SnapshotData) snapshotEvent.getData();
        assertNotNull(snapshotData.getYesSide());
        assertFalse(snapshotData.getYesSide().isEmpty(), "Orderbook should contain the new order");
    }
    
    @Test
    void testExistingOrdersLoadedOnStartup() {
        // The OrderBookService should have loaded existing orders on startup
        // Check that DUMMY_TEST market has orders
        com.fbg.api.rest.Orderbook orderbook = orderBookService.getOrderbook("DUMMY_TEST");
        
        assertNotNull(orderbook);
        assertNotNull(orderbook.getYes());
        // Should have existing orders from previous tests or initialization
        assertTrue(!orderbook.getYes().isEmpty() || !orderbook.getNo().isEmpty(), 
            "Orderbook should have loaded existing open orders");
    }
    
    private static class TestEventListener implements OrderBookEventListener {
        private final List<OrderBookEvent> events = new ArrayList<>();
        private final CountDownLatch tickerLatch = new CountDownLatch(1);
        private final CountDownLatch snapshotLatch = new CountDownLatch(1);
        private OrderBookEvent lastTickerEvent;
        private OrderBookEvent lastSnapshotEvent;
        
        @Override
        public void onOrderBookEvent(OrderBookEvent event) {
            events.add(event);
            
            if (event.getType() == OrderBookEvent.EventType.TICKER_UPDATE) {
                lastTickerEvent = event;
                tickerLatch.countDown();
            } else if (event.getType() == OrderBookEvent.EventType.SNAPSHOT) {
                lastSnapshotEvent = event;
                snapshotLatch.countDown();
            }
        }
        
        public boolean waitForTickerEvent(int seconds) throws InterruptedException {
            return tickerLatch.await(seconds, TimeUnit.SECONDS);
        }
        
        public boolean waitForSnapshotEvent(int seconds) throws InterruptedException {
            return snapshotLatch.await(seconds, TimeUnit.SECONDS);
        }
        
        public OrderBookEvent getLastTickerEvent() {
            return lastTickerEvent;
        }
        
        public OrderBookEvent getLastSnapshotEvent() {
            return lastSnapshotEvent;
        }
        
        public void clear() {
            events.clear();
        }
    }
}