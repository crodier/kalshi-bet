package com.kalshi.mock.model;

import com.fbg.api.market.KalshiSide;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.kalshi.mock.model.ConcurrentOrderBook.OrderBookListener;

/**
 * Comprehensive unit tests for ConcurrentOrderBook focusing on YES-only orders.
 * Tests cover all aspects of order book functionality including thread safety,
 * FIFO priority, cross detection, and event notifications.
 */
@DisplayName("ConcurrentOrderBook YES-Only Tests")
class ConcurrentOrderBookYesOnlyTest {

    private ConcurrentOrderBook orderBook;
    private final String TEST_MARKET = "TEST-MARKET";
    
    @BeforeEach
    void setUp() {
        orderBook = new ConcurrentOrderBook(TEST_MARKET);
    }
    
    // ==================== Helper Methods ====================
    
    private OrderBookEntry createBuyYesOrder(String orderId, int price, int quantity) {
        return new OrderBookEntry(
            orderId,
            "testUser",
            KalshiSide.yes,
            "buy",
            price,
            quantity,
            System.nanoTime()
        );
    }
    
    private OrderBookEntry createSellYesOrder(String orderId, int price, int quantity) {
        return new OrderBookEntry(
            orderId,
            "testUser",
            KalshiSide.yes,
            "sell",
            price,
            quantity,
            System.nanoTime()
        );
    }
    
    private OrderBookEntry createBuyYesOrder(String orderId, String userId, int price, int quantity) {
        return new OrderBookEntry(
            orderId,
            userId,
            KalshiSide.yes,
            "buy",
            price,
            quantity,
            System.nanoTime()
        );
    }
    
    private void assertBestBid(Integer expectedPrice) {
        Map.Entry<Integer, Queue<OrderBookEntry>> bestBid = orderBook.getBestBid();
        if (expectedPrice == null) {
            assertNull(bestBid, "Expected no best bid");
        } else {
            assertNotNull(bestBid, "Expected best bid at price " + expectedPrice);
            assertEquals(expectedPrice, bestBid.getKey(), "Best bid price mismatch");
        }
    }
    
    private void assertBestAsk(Integer expectedPrice) {
        Map.Entry<Integer, Queue<OrderBookEntry>> bestAsk = orderBook.getBestAsk();
        if (expectedPrice == null) {
            assertNull(bestAsk, "Expected no best ask");
        } else {
            assertNotNull(bestAsk, "Expected best ask at price " + expectedPrice);
            assertEquals(expectedPrice, bestAsk.getKey(), "Best ask price mismatch");
        }
    }
    
    private void assertOrderInBook(String orderId) {
        OrderBookEntry order = orderBook.getOrder(orderId);
        assertNotNull(order, "Order " + orderId + " should be in book");
    }
    
    private void assertOrderNotInBook(String orderId) {
        OrderBookEntry order = orderBook.getOrder(orderId);
        assertNull(order, "Order " + orderId + " should not be in book");
    }
    
    private int getSpread() {
        Map.Entry<Integer, Queue<OrderBookEntry>> bestBid = orderBook.getBestBid();
        Map.Entry<Integer, Queue<OrderBookEntry>> bestAsk = orderBook.getBestAsk();
        if (bestBid == null || bestAsk == null) {
            return -1;
        }
        return bestAsk.getKey() - bestBid.getKey();
    }
    
    // ==================== 1. Basic Order Entry Tests ====================
    
    @Test
    @DisplayName("Add Buy YES order successfully")
    void testAddBuyYesOrder() {
        String orderId = UUID.randomUUID().toString();
        OrderBookEntry order = createBuyYesOrder(orderId, 65, 100);
        
        boolean added = orderBook.addOrder(order);
        
        assertTrue(added, "Order should be added successfully");
        assertOrderInBook(orderId);
        assertBestBid(65);
    }
    
    @Test
    @DisplayName("Add Sell YES order successfully")
    void testAddSellYesOrder() {
        String orderId = UUID.randomUUID().toString();
        OrderBookEntry order = createSellYesOrder(orderId, 67, 100);
        
        boolean added = orderBook.addOrder(order);
        
        assertTrue(added, "Order should be added successfully");
        assertOrderInBook(orderId);
        assertBestAsk(67);
    }
    
    @Test
    @DisplayName("Add multiple Buy YES orders at different prices")
    void testAddMultipleBuyYesOrdersDifferentPrices() {
        String orderId1 = UUID.randomUUID().toString();
        String orderId2 = UUID.randomUUID().toString();
        String orderId3 = UUID.randomUUID().toString();
        
        orderBook.addOrder(createBuyYesOrder(orderId1, 65, 100));
        orderBook.addOrder(createBuyYesOrder(orderId2, 70, 100));
        orderBook.addOrder(createBuyYesOrder(orderId3, 60, 100));
        
        assertBestBid(70); // Highest buy price should be best bid
        assertOrderInBook(orderId1);
        assertOrderInBook(orderId2);
        assertOrderInBook(orderId3);
    }
    
    @Test
    @DisplayName("Add multiple Sell YES orders at different prices")
    void testAddMultipleSellYesOrdersDifferentPrices() {
        String orderId1 = UUID.randomUUID().toString();
        String orderId2 = UUID.randomUUID().toString();
        String orderId3 = UUID.randomUUID().toString();
        
        orderBook.addOrder(createSellYesOrder(orderId1, 71, 100));
        orderBook.addOrder(createSellYesOrder(orderId2, 75, 100));
        orderBook.addOrder(createSellYesOrder(orderId3, 73, 100));
        
        assertBestAsk(71); // Lowest sell price should be best ask
        assertOrderInBook(orderId1);
        assertOrderInBook(orderId2);
        assertOrderInBook(orderId3);
    }
    
    @Test
    @DisplayName("Reject duplicate order ID")
    void testOrderIdUniqueness() {
        String orderId = UUID.randomUUID().toString();
        OrderBookEntry order1 = createBuyYesOrder(orderId, 65, 100);
        OrderBookEntry order2 = createBuyYesOrder(orderId, 70, 200);
        
        boolean added1 = orderBook.addOrder(order1);
        boolean added2 = orderBook.addOrder(order2);
        
        assertTrue(added1, "First order should be added");
        assertFalse(added2, "Duplicate order ID should be rejected");
        
        // Verify original order is unchanged
        OrderBookEntry retrievedOrder = orderBook.getOrder(orderId);
        assertEquals(65, retrievedOrder.getPrice());
        assertEquals(100, retrievedOrder.getQuantity());
    }
    
    // ==================== 2. Price Validation Tests ====================
    
    @Test
    @DisplayName("Reject order with price below 1 cent")
    void testRejectPriceBelowOneCent() {
        assertThrows(IllegalArgumentException.class, () -> {
            createBuyYesOrder(UUID.randomUUID().toString(), 0, 100);
        }, "Should reject price of 0");
        
        assertThrows(IllegalArgumentException.class, () -> {
            createBuyYesOrder(UUID.randomUUID().toString(), -5, 100);
        }, "Should reject negative price");
    }
    
    @Test
    @DisplayName("Reject order with price above 99 cents")
    void testRejectPriceAbove99Cents() {
        assertThrows(IllegalArgumentException.class, () -> {
            createBuyYesOrder(UUID.randomUUID().toString(), 100, 100);
        }, "Should reject price of 100");
        
        assertThrows(IllegalArgumentException.class, () -> {
            createBuyYesOrder(UUID.randomUUID().toString(), 150, 100);
        }, "Should reject price above 99");
    }
    
    @Test
    @DisplayName("Accept boundary prices (1 and 99 cents)")
    void testAcceptBoundaryPrices() {
        String orderId1 = UUID.randomUUID().toString();
        String orderId99 = UUID.randomUUID().toString();
        
        OrderBookEntry order1 = createBuyYesOrder(orderId1, 1, 100);
        OrderBookEntry order99 = createSellYesOrder(orderId99, 99, 100);
        
        assertTrue(orderBook.addOrder(order1), "Should accept price of 1 cent");
        assertTrue(orderBook.addOrder(order99), "Should accept price of 99 cents");
        
        assertOrderInBook(orderId1);
        assertOrderInBook(orderId99);
    }
    
    @Test
    @DisplayName("Validate price range for all valid prices")
    void testAllValidPrices() {
        // Test that all prices from 1 to 99 are accepted
        for (int price = 1; price <= 99; price++) {
            String orderId = UUID.randomUUID().toString();
            OrderBookEntry order = createBuyYesOrder(orderId, price, 1);
            
            assertTrue(orderBook.addOrder(order), 
                "Should accept price of " + price + " cents");
        }
    }
    
    // ==================== 3. FIFO Priority Tests ====================
    
    @Test
    @DisplayName("FIFO ordering at same price for Buy YES")
    void testFifoOrderingAtSamePriceBuy() throws InterruptedException {
        String orderId1 = "order1";
        String orderId2 = "order2";
        String orderId3 = "order3";
        
        orderBook.addOrder(createBuyYesOrder(orderId1, 65, 100));
        Thread.sleep(1); // Ensure different timestamps
        orderBook.addOrder(createBuyYesOrder(orderId2, 65, 100));
        Thread.sleep(1);
        orderBook.addOrder(createBuyYesOrder(orderId3, 65, 100));
        
        Map.Entry<Integer, Queue<OrderBookEntry>> bestBid = orderBook.getBestBid();
        assertNotNull(bestBid);
        
        Queue<OrderBookEntry> priceLevel = bestBid.getValue();
        assertEquals(3, priceLevel.size());
        
        // Verify FIFO order
        OrderBookEntry[] orders = priceLevel.toArray(new OrderBookEntry[0]);
        assertEquals(orderId1, orders[0].getOrderId());
        assertEquals(orderId2, orders[1].getOrderId());
        assertEquals(orderId3, orders[2].getOrderId());
    }
    
    @Test
    @DisplayName("FIFO ordering at same price for Sell YES")
    void testFifoOrderingAtSamePriceSell() throws InterruptedException {
        String orderId1 = "order1";
        String orderId2 = "order2";
        String orderId3 = "order3";
        
        orderBook.addOrder(createSellYesOrder(orderId1, 67, 100));
        Thread.sleep(1);
        orderBook.addOrder(createSellYesOrder(orderId2, 67, 100));
        Thread.sleep(1);
        orderBook.addOrder(createSellYesOrder(orderId3, 67, 100));
        
        Map.Entry<Integer, Queue<OrderBookEntry>> bestAsk = orderBook.getBestAsk();
        assertNotNull(bestAsk);
        
        Queue<OrderBookEntry> priceLevel = bestAsk.getValue();
        assertEquals(3, priceLevel.size());
        
        // Verify FIFO order
        OrderBookEntry[] orders = priceLevel.toArray(new OrderBookEntry[0]);
        assertEquals(orderId1, orders[0].getOrderId());
        assertEquals(orderId2, orders[1].getOrderId());
        assertEquals(orderId3, orders[2].getOrderId());
    }
    
    @Test
    @DisplayName("FIFO maintained after middle cancellation")
    void testFifoMaintainedAfterMiddleCancellation() throws InterruptedException {
        String orderId1 = "order1";
        String orderId2 = "order2";
        String orderId3 = "order3";
        
        orderBook.addOrder(createBuyYesOrder(orderId1, 65, 100));
        Thread.sleep(1);
        orderBook.addOrder(createBuyYesOrder(orderId2, 65, 100));
        Thread.sleep(1);
        orderBook.addOrder(createBuyYesOrder(orderId3, 65, 100));
        
        // Cancel middle order
        assertTrue(orderBook.cancelOrder(orderId2));
        
        Map.Entry<Integer, Queue<OrderBookEntry>> bestBid = orderBook.getBestBid();
        Queue<OrderBookEntry> priceLevel = bestBid.getValue();
        assertEquals(2, priceLevel.size());
        
        // Verify remaining orders maintain original order
        OrderBookEntry[] orders = priceLevel.toArray(new OrderBookEntry[0]);
        assertEquals(orderId1, orders[0].getOrderId());
        assertEquals(orderId3, orders[1].getOrderId());
    }
    
    @Test
    @DisplayName("Sequence numbers increment properly")
    void testSequenceNumbers() {
        OrderBookEntry order1 = createBuyYesOrder("order1", 65, 100);
        OrderBookEntry order2 = createBuyYesOrder("order2", 65, 100);
        OrderBookEntry order3 = createBuyYesOrder("order3", 65, 100);
        
        orderBook.addOrder(order1);
        orderBook.addOrder(order2);
        orderBook.addOrder(order3);
        
        assertTrue(order2.getSequence() > order1.getSequence(), 
            "Order2 sequence should be greater than order1");
        assertTrue(order3.getSequence() > order2.getSequence(), 
            "Order3 sequence should be greater than order2");
    }
    
    @Test
    @DisplayName("Multiple users at same price maintain time order")
    void testMultipleUsersAtSamePrice() throws InterruptedException {
        String orderId1 = "order1";
        String orderId2 = "order2";
        String orderId3 = "order3";
        
        orderBook.addOrder(createBuyYesOrder(orderId1, "user1", 65, 100));
        Thread.sleep(1);
        orderBook.addOrder(createBuyYesOrder(orderId2, "user2", 65, 100));
        Thread.sleep(1);
        orderBook.addOrder(createBuyYesOrder(orderId3, "user1", 65, 100));
        
        Map.Entry<Integer, Queue<OrderBookEntry>> bestBid = orderBook.getBestBid();
        Queue<OrderBookEntry> priceLevel = bestBid.getValue();
        
        OrderBookEntry[] orders = priceLevel.toArray(new OrderBookEntry[0]);
        assertEquals(orderId1, orders[0].getOrderId());
        assertEquals(orderId2, orders[1].getOrderId());
        assertEquals(orderId3, orders[2].getOrderId());
    }
    
    // ==================== 4. Self-Cross Detection Tests ====================
    
    @Test
    @DisplayName("Cross detected when bid higher than ask")
    void testCrossDetectionBidHigherThanAsk() {
        OrderBookListener listener = mock(OrderBookListener.class);
        orderBook.addListener(listener);
        
        orderBook.addOrder(createBuyYesOrder("buy1", 65, 100));
        orderBook.addOrder(createSellYesOrder("sell1", 64, 100));
        
        verify(listener, times(1)).onCrossDetected(eq(TEST_MARKET), any());
    }
    
    @Test
    @DisplayName("Cross detected when bid equals ask")
    void testCrossDetectionBidEqualsAsk() {
        OrderBookListener listener = mock(OrderBookListener.class);
        orderBook.addListener(listener);
        
        orderBook.addOrder(createBuyYesOrder("buy1", 65, 100));
        orderBook.addOrder(createSellYesOrder("sell1", 65, 100));
        
        verify(listener, times(1)).onCrossDetected(eq(TEST_MARKET), any());
    }
    
    @Test
    @DisplayName("No cross when normal spread")
    void testNoCrossNormalSpread() {
        OrderBookListener listener = mock(OrderBookListener.class);
        orderBook.addListener(listener);
        
        orderBook.addOrder(createBuyYesOrder("buy1", 65, 100));
        orderBook.addOrder(createSellYesOrder("sell1", 66, 100));
        
        verify(listener, never()).onCrossDetected(any(), any());
        assertEquals(1, getSpread(), "Spread should be 1 cent");
    }
    
    @Test
    @DisplayName("Cross detection with empty book")
    void testCrossDetectionEmptyBook() {
        OrderBookListener listener = mock(OrderBookListener.class);
        orderBook.addListener(listener);
        
        // Add first order to empty book - no cross possible
        orderBook.addOrder(createBuyYesOrder("buy1", 65, 100));
        verify(listener, never()).onCrossDetected(any(), any());
        
        // Add crossing order
        orderBook.addOrder(createSellYesOrder("sell1", 64, 100));
        verify(listener, times(1)).onCrossDetected(eq(TEST_MARKET), any());
    }
    
    @Test
    @DisplayName("Cross event fired with correct details")
    void testCrossEventFired() {
        OrderBookListener listener = mock(OrderBookListener.class);
        orderBook.addListener(listener);
        
        orderBook.addOrder(createBuyYesOrder("buy1", 70, 100));
        OrderBookEntry crossingOrder = createSellYesOrder("sell1", 69, 100);
        orderBook.addOrder(crossingOrder);
        
        ArgumentCaptor<OrderBookEntry> orderCaptor = ArgumentCaptor.forClass(OrderBookEntry.class);
        verify(listener).onCrossDetected(eq(TEST_MARKET), orderCaptor.capture());
        
        assertEquals(crossingOrder.getOrderId(), orderCaptor.getValue().getOrderId());
    }
    
    @Test
    @DisplayName("Multiple cross detection")
    void testMultipleCrossDetection() {
        OrderBookListener listener = mock(OrderBookListener.class);
        orderBook.addListener(listener);
        
        // Setup multiple buy orders
        orderBook.addOrder(createBuyYesOrder("buy1", 70, 100));
        orderBook.addOrder(createBuyYesOrder("buy2", 68, 100));
        orderBook.addOrder(createBuyYesOrder("buy3", 66, 100));
        
        // Add sell order that crosses with highest bid
        orderBook.addOrder(createSellYesOrder("sell1", 69, 100));
        
        verify(listener, times(1)).onCrossDetected(eq(TEST_MARKET), any());
    }
    
    // ==================== 5. Order Cancellation Tests ====================
    
    @Test
    @DisplayName("Cancel existing Buy order")
    void testCancelExistingBuyOrder() {
        String orderId = UUID.randomUUID().toString();
        orderBook.addOrder(createBuyYesOrder(orderId, 65, 100));
        
        boolean canceled = orderBook.cancelOrder(orderId);
        
        assertTrue(canceled, "Should successfully cancel order");
        assertOrderNotInBook(orderId);
        assertBestBid(null); // No more bids
    }
    
    @Test
    @DisplayName("Cancel existing Sell order")
    void testCancelExistingSellOrder() {
        String orderId = UUID.randomUUID().toString();
        orderBook.addOrder(createSellYesOrder(orderId, 67, 100));
        
        boolean canceled = orderBook.cancelOrder(orderId);
        
        assertTrue(canceled, "Should successfully cancel order");
        assertOrderNotInBook(orderId);
        assertBestAsk(null); // No more asks
    }
    
    @Test
    @DisplayName("Cancel non-existent order returns false")
    void testCancelNonExistentOrder() {
        String nonExistentId = UUID.randomUUID().toString();
        
        boolean canceled = orderBook.cancelOrder(nonExistentId);
        
        assertFalse(canceled, "Should return false for non-existent order");
    }
    
    @Test
    @DisplayName("Cancel last order removes price level")
    void testCancelLastOrderRemovesPriceLevel() {
        String orderId1 = "order1";
        String orderId2 = "order2";
        
        orderBook.addOrder(createBuyYesOrder(orderId1, 65, 100));
        orderBook.addOrder(createBuyYesOrder(orderId2, 70, 100));
        
        // Cancel order at 70
        orderBook.cancelOrder(orderId2);
        assertBestBid(65); // Best bid should now be 65
        
        // Cancel last order at 65
        orderBook.cancelOrder(orderId1);
        assertBestBid(null); // No bids left
    }
    
    @Test
    @DisplayName("Cancel order maintains FIFO")
    void testCancelOrderMaintainsFifo() throws InterruptedException {
        String orderId1 = "order1";
        String orderId2 = "order2";
        String orderId3 = "order3";
        String orderId4 = "order4";
        
        orderBook.addOrder(createBuyYesOrder(orderId1, 65, 100));
        Thread.sleep(1);
        orderBook.addOrder(createBuyYesOrder(orderId2, 65, 100));
        Thread.sleep(1);
        orderBook.addOrder(createBuyYesOrder(orderId3, 65, 100));
        Thread.sleep(1);
        orderBook.addOrder(createBuyYesOrder(orderId4, 65, 100));
        
        // Cancel orders 2 and 3
        orderBook.cancelOrder(orderId2);
        orderBook.cancelOrder(orderId3);
        
        Map.Entry<Integer, Queue<OrderBookEntry>> bestBid = orderBook.getBestBid();
        Queue<OrderBookEntry> priceLevel = bestBid.getValue();
        assertEquals(2, priceLevel.size());
        
        OrderBookEntry[] orders = priceLevel.toArray(new OrderBookEntry[0]);
        assertEquals(orderId1, orders[0].getOrderId());
        assertEquals(orderId4, orders[1].getOrderId());
    }
    
    // ==================== 6. Best Bid/Ask Tests ====================
    
    @Test
    @DisplayName("Best bid from multiple levels")
    void testBestBidFromMultipleLevels() {
        orderBook.addOrder(createBuyYesOrder("buy1", 65, 100));
        orderBook.addOrder(createBuyYesOrder("buy2", 70, 100));
        orderBook.addOrder(createBuyYesOrder("buy3", 68, 100));
        
        assertBestBid(70); // Highest price
    }
    
    @Test
    @DisplayName("Best ask from multiple levels")
    void testBestAskFromMultipleLevels() {
        orderBook.addOrder(createSellYesOrder("sell1", 75, 100));
        orderBook.addOrder(createSellYesOrder("sell2", 71, 100));
        orderBook.addOrder(createSellYesOrder("sell3", 73, 100));
        
        assertBestAsk(71); // Lowest price
    }
    
    @Test
    @DisplayName("Best bid/ask on empty book")
    void testBestBidAskEmptyBook() {
        assertBestBid(null);
        assertBestAsk(null);
    }
    
    @Test
    @DisplayName("Best bid/ask with single order")
    void testBestBidAskSingleOrder() {
        orderBook.addOrder(createBuyYesOrder("buy1", 65, 100));
        assertBestBid(65);
        assertBestAsk(null);
        
        orderBook.addOrder(createSellYesOrder("sell1", 67, 100));
        assertBestBid(65);
        assertBestAsk(67);
    }
    
    @Test
    @DisplayName("Best bid/ask after cancellations")
    void testBestBidAskAfterCancellations() {
        String buyId1 = "buy1";
        String buyId2 = "buy2";
        String sellId1 = "sell1";
        String sellId2 = "sell2";
        
        orderBook.addOrder(createBuyYesOrder(buyId1, 70, 100));
        orderBook.addOrder(createBuyYesOrder(buyId2, 65, 100));
        orderBook.addOrder(createSellYesOrder(sellId1, 71, 100));
        orderBook.addOrder(createSellYesOrder(sellId2, 75, 100));
        
        assertBestBid(70);
        assertBestAsk(71);
        
        // Cancel best bid
        orderBook.cancelOrder(buyId1);
        assertBestBid(65);
        
        // Cancel best ask
        orderBook.cancelOrder(sellId1);
        assertBestAsk(75);
    }
    
    // ==================== 7. Order Lookup Tests ====================
    
    @Test
    @DisplayName("Get order by ID")
    void testGetOrderById() {
        String orderId = UUID.randomUUID().toString();
        OrderBookEntry originalOrder = createBuyYesOrder(orderId, 65, 100);
        
        orderBook.addOrder(originalOrder);
        OrderBookEntry retrievedOrder = orderBook.getOrder(orderId);
        
        assertNotNull(retrievedOrder);
        assertEquals(orderId, retrievedOrder.getOrderId());
        assertEquals(65, retrievedOrder.getPrice());
        assertEquals(100, retrievedOrder.getQuantity());
        assertEquals("testUser", retrievedOrder.getUserId());
    }
    
    @Test
    @DisplayName("Get non-existent order returns null")
    void testGetNonExistentOrder() {
        OrderBookEntry order = orderBook.getOrder("non-existent-id");
        assertNull(order);
    }
    
    @Test
    @DisplayName("Order details accuracy")
    void testOrderDetailsAccuracy() {
        String orderId = UUID.randomUUID().toString();
        String userId = "specificUser";
        int price = 72;
        int quantity = 250;
        
        OrderBookEntry originalOrder = new OrderBookEntry(
            orderId, userId, KalshiSide.yes, "buy", price, quantity, System.nanoTime()
        );
        
        orderBook.addOrder(originalOrder);
        OrderBookEntry retrieved = orderBook.getOrder(orderId);
        
        assertEquals(orderId, retrieved.getOrderId());
        assertEquals(userId, retrieved.getUserId());
        assertEquals(KalshiSide.yes, retrieved.getSide());
        assertEquals("buy", retrieved.getAction());
        assertEquals(price, retrieved.getPrice());
        assertEquals(quantity, retrieved.getQuantity());
        assertEquals(quantity, retrieved.getOriginalQuantity());
    }
    
    // ==================== 8. Order Book Snapshot Tests ====================
    
    @Test
    @DisplayName("Snapshot with all levels")
    void testSnapshotAllLevels() {
        // Add orders at different price levels
        orderBook.addOrder(createBuyYesOrder("buy1", 65, 100));
        orderBook.addOrder(createBuyYesOrder("buy2", 64, 200));
        orderBook.addOrder(createBuyYesOrder("buy3", 63, 300));
        
        orderBook.addOrder(createSellYesOrder("sell1", 67, 150));
        orderBook.addOrder(createSellYesOrder("sell2", 68, 250));
        orderBook.addOrder(createSellYesOrder("sell3", 69, 350));
        
        var snapshot = orderBook.getOrderbookSnapshot(10);
        assertNotNull(snapshot);
        assertNotNull(snapshot.getYes());
        
        // Verify snapshot contains all levels
        List<List<Integer>> yesBook = snapshot.getYes();
        assertEquals(6, yesBook.size()); // 3 bid levels + 3 ask levels
    }
    
    @Test
    @DisplayName("Snapshot with depth limit")
    void testSnapshotWithDepthLimit() {
        // Add many orders
        for (int i = 60; i <= 70; i++) {
            orderBook.addOrder(createBuyYesOrder("buy" + i, i, 100));
        }
        for (int i = 71; i <= 81; i++) {
            orderBook.addOrder(createSellYesOrder("sell" + i, i, 100));
        }
        
        var snapshot = orderBook.getOrderbookSnapshot(3);
        List<List<Integer>> yesBook = snapshot.getYes();
        
        // Should have at most 3 levels per side = 6 total
        assertTrue(yesBook.size() <= 6, "Snapshot should respect depth limit");
    }
    
    @Test
    @DisplayName("Snapshot of empty book")
    void testSnapshotEmptyBook() {
        var snapshot = orderBook.getOrderbookSnapshot(5);
        assertNotNull(snapshot);
        assertNull(snapshot.getYes()); // Empty book returns null
    }
    
    @Test
    @DisplayName("Snapshot aggregation of multiple orders per level")
    void testSnapshotAggregation() {
        // Add multiple orders at same price
        orderBook.addOrder(createBuyYesOrder("buy1", 65, 100));
        orderBook.addOrder(createBuyYesOrder("buy2", 65, 200));
        orderBook.addOrder(createBuyYesOrder("buy3", 65, 300));
        
        var snapshot = orderBook.getOrderbookSnapshot(5);
        List<List<Integer>> yesBook = snapshot.getYes();
        
        // Find the bid level at 65
        boolean found = false;
        for (List<Integer> level : yesBook) {
            if (level.get(0) == 65) {
                assertEquals(600, level.get(1), "Should aggregate quantities");
                found = true;
                break;
            }
        }
        assertTrue(found, "Should find aggregated level at price 65");
    }
    
    @Test
    @DisplayName("Snapshot consistency during concurrent updates")
    void testSnapshotConsistency() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean snapshotValid = new AtomicBoolean(true);
        
        // Thread continuously adding/removing orders
        Thread modifier = new Thread(() -> {
            try {
                latch.await();
                for (int i = 0; i < 100; i++) {
                    String orderId = "order" + i;
                    orderBook.addOrder(createBuyYesOrder(orderId, 60 + (i % 10), 100));
                    if (i > 10) {
                        orderBook.cancelOrder("order" + (i - 10));
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        
        // Thread continuously taking snapshots
        Thread reader = new Thread(() -> {
            try {
                latch.await();
                for (int i = 0; i < 100; i++) {
                    var snapshot = orderBook.getOrderbookSnapshot(10);
                    // Verify snapshot is valid (no exceptions, consistent structure)
                    if (snapshot != null && snapshot.getYes() != null) {
                        for (List<Integer> level : snapshot.getYes()) {
                            if (level.size() != 2 || level.get(0) == null || level.get(1) == null) {
                                snapshotValid.set(false);
                            }
                        }
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        
        modifier.start();
        reader.start();
        latch.countDown();
        
        modifier.join();
        reader.join();
        
        assertTrue(snapshotValid.get(), "All snapshots should be valid");
    }
    
    // ==================== 9. Thread Safety Tests ====================
    
    @Test
    @DisplayName("Concurrent Buy order additions")
    void testConcurrentBuyOrderAdditions() throws InterruptedException {
        int threadCount = 100;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            new Thread(() -> {
                try {
                    startLatch.await();
                    String orderId = "buy-" + threadId;
                    OrderBookEntry order = createBuyYesOrder(orderId, 65, 100);
                    if (orderBook.addOrder(order)) {
                        successCount.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            }).start();
        }
        
        startLatch.countDown();
        doneLatch.await();
        
        assertEquals(threadCount, successCount.get(), "All orders should be added");
        
        // Verify all orders are in the book
        for (int i = 0; i < threadCount; i++) {
            assertOrderInBook("buy-" + i);
        }
    }
    
    @Test
    @DisplayName("Concurrent Sell order additions")
    void testConcurrentSellOrderAdditions() throws InterruptedException {
        int threadCount = 100;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            new Thread(() -> {
                try {
                    startLatch.await();
                    String orderId = "sell-" + threadId;
                    OrderBookEntry order = createSellYesOrder(orderId, 67, 100);
                    if (orderBook.addOrder(order)) {
                        successCount.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            }).start();
        }
        
        startLatch.countDown();
        doneLatch.await();
        
        assertEquals(threadCount, successCount.get(), "All orders should be added");
    }
    
    @Test
    @DisplayName("Concurrent mixed operations")
    void testConcurrentMixedOperations() throws InterruptedException {
        int operationsPerThread = 10;
        int threadCount = 50;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        ConcurrentHashMap<String, Boolean> addedOrders = new ConcurrentHashMap<>();
        
        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            new Thread(() -> {
                try {
                    startLatch.await();
                    ThreadLocalRandom random = ThreadLocalRandom.current();
                    
                    for (int i = 0; i < operationsPerThread; i++) {
                        int operation = random.nextInt(4);
                        
                        switch (operation) {
                            case 0: // Add buy order
                                String buyId = "buy-" + threadId + "-" + i;
                                if (orderBook.addOrder(createBuyYesOrder(buyId, 60 + random.nextInt(10), 100))) {
                                    addedOrders.put(buyId, true);
                                }
                                break;
                                
                            case 1: // Add sell order
                                String sellId = "sell-" + threadId + "-" + i;
                                if (orderBook.addOrder(createSellYesOrder(sellId, 70 + random.nextInt(10), 100))) {
                                    addedOrders.put(sellId, true);
                                }
                                break;
                                
                            case 2: // Cancel random order
                                List<String> orderIds = new ArrayList<>(addedOrders.keySet());
                                if (!orderIds.isEmpty()) {
                                    String toCancel = orderIds.get(random.nextInt(orderIds.size()));
                                    if (orderBook.cancelOrder(toCancel)) {
                                        addedOrders.remove(toCancel);
                                    }
                                }
                                break;
                                
                            case 3: // Get order
                                orderBook.getOrder("buy-" + random.nextInt(threadCount) + "-" + random.nextInt(operationsPerThread));
                                break;
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            }).start();
        }
        
        startLatch.countDown();
        doneLatch.await();
        
        // Verify consistency
        for (String orderId : addedOrders.keySet()) {
            assertOrderInBook(orderId);
        }
    }
    
    @Test
    @DisplayName("Concurrent reads during writes")
    void testConcurrentReadsDuringWrites() throws InterruptedException {
        int duration = 1000; // 1 second
        AtomicBoolean stop = new AtomicBoolean(false);
        AtomicInteger readCount = new AtomicInteger(0);
        AtomicInteger writeCount = new AtomicInteger(0);
        AtomicBoolean error = new AtomicBoolean(false);
        
        // Writer thread
        Thread writer = new Thread(() -> {
            int count = 0;
            while (!stop.get()) {
                try {
                    orderBook.addOrder(createBuyYesOrder("write-" + count, 65, 100));
                    writeCount.incrementAndGet();
                    count++;
                    Thread.sleep(1);
                } catch (Exception e) {
                    error.set(true);
                }
            }
        });
        
        // Reader threads
        List<Thread> readers = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            Thread reader = new Thread(() -> {
                while (!stop.get()) {
                    try {
                        orderBook.getBestBid();
                        orderBook.getBestAsk();
                        orderBook.getOrderbookSnapshot(10);
                        readCount.incrementAndGet();
                        Thread.sleep(1);
                    } catch (Exception e) {
                        error.set(true);
                    }
                }
            });
            readers.add(reader);
        }
        
        writer.start();
        readers.forEach(Thread::start);
        
        Thread.sleep(duration);
        stop.set(true);
        
        writer.join();
        for (Thread reader : readers) {
            reader.join();
        }
        
        assertFalse(error.get(), "No errors during concurrent operations");
        assertTrue(readCount.get() > 0, "Reads should have occurred");
        assertTrue(writeCount.get() > 0, "Writes should have occurred");
    }
    
    @Test
    @DisplayName("Race condition prevention")
    void testRaceConditionPrevention() throws InterruptedException {
        String orderId = "test-order";
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(2);
        AtomicInteger addCount = new AtomicInteger(0);
        AtomicInteger cancelCount = new AtomicInteger(0);
        
        // Thread 1: Try to add order
        Thread adder = new Thread(() -> {
            try {
                startLatch.await();
                if (orderBook.addOrder(createBuyYesOrder(orderId, 65, 100))) {
                    addCount.incrementAndGet();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                doneLatch.countDown();
            }
        });
        
        // Thread 2: Try to add same order
        Thread duplicateAdder = new Thread(() -> {
            try {
                startLatch.await();
                if (orderBook.addOrder(createBuyYesOrder(orderId, 70, 200))) {
                    addCount.incrementAndGet();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                doneLatch.countDown();
            }
        });
        
        adder.start();
        duplicateAdder.start();
        startLatch.countDown();
        doneLatch.await();
        
        // Only one add should succeed
        assertEquals(1, addCount.get(), "Only one order should be added");
        
        // Verify the order in book
        OrderBookEntry order = orderBook.getOrder(orderId);
        assertNotNull(order);
        assertTrue(order.getPrice() == 65 || order.getPrice() == 70);
    }
    
    // ==================== 10. Event Listener Tests ====================
    
    @Test
    @DisplayName("Order added event")
    void testOrderAddedEvent() {
        OrderBookListener listener = mock(OrderBookListener.class);
        orderBook.addListener(listener);
        
        OrderBookEntry order = createBuyYesOrder("test-id", 65, 100);
        orderBook.addOrder(order);
        
        verify(listener).onOrderAdded(TEST_MARKET, order);
    }
    
    @Test
    @DisplayName("Order canceled event")
    void testOrderCanceledEvent() {
        OrderBookListener listener = mock(OrderBookListener.class);
        orderBook.addListener(listener);
        
        String orderId = "test-id";
        OrderBookEntry order = createBuyYesOrder(orderId, 65, 100);
        orderBook.addOrder(order);
        orderBook.cancelOrder(orderId);
        
        verify(listener).onOrderCanceled(TEST_MARKET, order);
    }
    
    @Test
    @DisplayName("Cross detected event")
    void testCrossDetectedEvent() {
        OrderBookListener listener = mock(OrderBookListener.class);
        orderBook.addListener(listener);
        
        orderBook.addOrder(createBuyYesOrder("buy1", 65, 100));
        OrderBookEntry crossingOrder = createSellYesOrder("sell1", 65, 100);
        orderBook.addOrder(crossingOrder);
        
        ArgumentCaptor<OrderBookEntry> captor = ArgumentCaptor.forClass(OrderBookEntry.class);
        verify(listener).onCrossDetected(eq(TEST_MARKET), captor.capture());
        assertEquals(crossingOrder.getOrderId(), captor.getValue().getOrderId());
    }
    
    @Test
    @DisplayName("Multiple listeners receive events")
    void testMultipleListeners() {
        OrderBookListener listener1 = mock(OrderBookListener.class);
        OrderBookListener listener2 = mock(OrderBookListener.class);
        OrderBookListener listener3 = mock(OrderBookListener.class);
        
        orderBook.addListener(listener1);
        orderBook.addListener(listener2);
        orderBook.addListener(listener3);
        
        OrderBookEntry order = createBuyYesOrder("test-id", 65, 100);
        orderBook.addOrder(order);
        
        verify(listener1).onOrderAdded(TEST_MARKET, order);
        verify(listener2).onOrderAdded(TEST_MARKET, order);
        verify(listener3).onOrderAdded(TEST_MARKET, order);
    }
    
    // ==================== 11. Quantity Management Tests ====================
    
    @Test
    @DisplayName("Reduce order quantity")
    void testReduceOrderQuantity() {
        String orderId = "test-order";
        OrderBookEntry order = createBuyYesOrder(orderId, 65, 100);
        orderBook.addOrder(order);
        
        OrderBookEntry retrievedOrder = orderBook.getOrder(orderId);
        retrievedOrder.reduceQuantity(30);
        
        assertEquals(70, retrievedOrder.getQuantity());
        assertEquals(100, retrievedOrder.getOriginalQuantity());
        assertEquals(30, retrievedOrder.getFilledQuantity());
    }
    
    @Test
    @DisplayName("Reduce to zero handling")
    void testReduceToZeroHandling() {
        String orderId = "test-order";
        OrderBookEntry order = createBuyYesOrder(orderId, 65, 100);
        orderBook.addOrder(order);
        
        OrderBookEntry retrievedOrder = orderBook.getOrder(orderId);
        retrievedOrder.reduceQuantity(100);
        
        assertEquals(0, retrievedOrder.getQuantity());
        assertEquals(100, retrievedOrder.getFilledQuantity());
        
        // Order should still be in book (matching engine should remove it)
        assertOrderInBook(orderId);
    }
    
    @Test
    @DisplayName("Reduce more than available throws exception")
    void testReduceMoreThanAvailable() {
        String orderId = "test-order";
        OrderBookEntry order = createBuyYesOrder(orderId, 65, 100);
        orderBook.addOrder(order);
        
        OrderBookEntry retrievedOrder = orderBook.getOrder(orderId);
        
        assertThrows(RuntimeException.class, () -> {
            retrievedOrder.reduceQuantity(150);
        });
        
        // Quantity should remain unchanged
        assertEquals(100, retrievedOrder.getQuantity());
    }
    
    @Test
    @DisplayName("Quantity update event")
    void testQuantityUpdateEvent() {
        OrderBookListener listener = mock(OrderBookListener.class);
        orderBook.addListener(listener);
        
        String orderId = "test-order";
        OrderBookEntry order = createBuyYesOrder(orderId, 65, 100);
        orderBook.addOrder(order);
        
        // Reduce quantity
        order.reduceQuantity(25);
        
        // Notify of execution
        orderBook.notifyOrderExecuted(order, 25);
        
        verify(listener).onOrderExecuted(TEST_MARKET, order, 25);
    }
    
    // ==================== 12. Edge Cases ====================
    
    @Test
    @DisplayName("Duplicate order IDs rejected")
    void testDuplicateOrderId() {
        String orderId = "duplicate-id";
        
        assertTrue(orderBook.addOrder(createBuyYesOrder(orderId, 65, 100)));
        assertFalse(orderBook.addOrder(createBuyYesOrder(orderId, 70, 200)));
        assertFalse(orderBook.addOrder(createSellYesOrder(orderId, 75, 300)));
        
        // Original order should remain unchanged
        OrderBookEntry order = orderBook.getOrder(orderId);
        assertEquals(65, order.getPrice());
        assertEquals(100, order.getQuantity());
    }
    
    @Test
    @DisplayName("Very large order quantities")
    void testMaxIntegerPrice() {
        String orderId = "large-quantity";
        int largeQuantity = 1_000_000;
        
        OrderBookEntry order = createBuyYesOrder(orderId, 50, largeQuantity);
        assertTrue(orderBook.addOrder(order));
        
        OrderBookEntry retrieved = orderBook.getOrder(orderId);
        assertEquals(largeQuantity, retrieved.getQuantity());
    }
    
    @Test
    @DisplayName("High volume stress test")
    void testHighVolumeStressTest() throws InterruptedException {
        int orderCount = 10_000;
        long startTime = System.currentTimeMillis();
        
        // Add orders
        for (int i = 0; i < orderCount; i++) {
            int price = 1 + (i % 99); // Distribute across all valid prices
            String orderId = "stress-" + i;
            
            if (i % 2 == 0) {
                orderBook.addOrder(createBuyYesOrder(orderId, price, 100));
            } else {
                orderBook.addOrder(createSellYesOrder(orderId, price, 100));
            }
        }
        
        long addTime = System.currentTimeMillis() - startTime;
        
        // Take snapshot
        long snapshotStart = System.currentTimeMillis();
        var snapshot = orderBook.getOrderbookSnapshot(100);
        long snapshotTime = System.currentTimeMillis() - snapshotStart;
        
        // Cancel half the orders
        long cancelStart = System.currentTimeMillis();
        for (int i = 0; i < orderCount / 2; i++) {
            orderBook.cancelOrder("stress-" + i);
        }
        long cancelTime = System.currentTimeMillis() - cancelStart;
        
        // Verify performance
        System.out.println("Stress test results:");
        System.out.println("  Add " + orderCount + " orders: " + addTime + "ms");
        System.out.println("  Snapshot: " + snapshotTime + "ms");
        System.out.println("  Cancel " + (orderCount/2) + " orders: " + cancelTime + "ms");
        
        // Basic verification
        for (int i = orderCount / 2; i < orderCount; i++) {
            assertOrderInBook("stress-" + i);
        }
    }
    
    @Test
    @DisplayName("Memory leak prevention")
    void testMemoryLeakPrevention() {
        // Add and remove many orders
        for (int cycle = 0; cycle < 100; cycle++) {
            List<String> orderIds = new ArrayList<>();
            
            // Add 100 orders
            for (int i = 0; i < 100; i++) {
                String orderId = "mem-" + cycle + "-" + i;
                orderIds.add(orderId);
                orderBook.addOrder(createBuyYesOrder(orderId, 50 + (i % 20), 100));
            }
            
            // Cancel all orders
            for (String orderId : orderIds) {
                orderBook.cancelOrder(orderId);
            }
        }
        
        // Verify book is empty
        assertBestBid(null);
        assertBestAsk(null);
        
        // Take snapshot to ensure no lingering references
        var snapshot = orderBook.getOrderbookSnapshot(10);
        assertNull(snapshot.getYes());
    }
}