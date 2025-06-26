package com.kalshi.mock.model;

import com.fbg.api.rest.Orderbook;
import com.fbg.api.market.KalshiSide;
import com.kalshi.mock.dto.OrderbookResponse;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

/**
 * Thread-safe concurrent order book implementation for Kalshi YES/NO markets.
 * 
 * Key features:
 * - All NO orders are internally converted to YES equivalents
 * - Maintains FIFO order priority at each price level
 * - Detects both self-crosses and external crosses
 * - Thread-safe using concurrent collections and read/write locks
 */
@Slf4j
public class ConcurrentOrderBook {
    private final String marketTicker;
    
    // Normalized order books (all converted to YES perspective)
    // Bids are buy orders (sorted high to low)
    private final ConcurrentSkipListMap<Integer, Queue<OrderBookEntry>> bids = 
        new ConcurrentSkipListMap<>(Comparator.reverseOrder());
    
    // Asks are sell orders (sorted low to high)
    private final ConcurrentSkipListMap<Integer, Queue<OrderBookEntry>> asks = 
        new ConcurrentSkipListMap<>();
    
    // Order lookup by orderId
    private final ConcurrentHashMap<String, OrderBookEntry> orderMap = new ConcurrentHashMap<>();
    
    // Lock for complex operations
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    
    // Listeners for order book events
    private final List<OrderBookListener> listeners = new CopyOnWriteArrayList<>();
    
    public ConcurrentOrderBook(String marketTicker) {
        this.marketTicker = marketTicker;
    }
    
    /**
     * Add a new order to the book
     */
    public boolean addOrder(OrderBookEntry order) {
        lock.writeLock().lock();
        try {
            // Check if order already exists
            if (orderMap.containsKey(order.getOrderId())) {
                return false;
            }
            
            // Check for crosses before adding
            if (checkForCross(order)) {
                notifyListeners(listener -> listener.onCrossDetected(marketTicker, order));
            }
            
            // Add to appropriate side based on normalized values
            ConcurrentSkipListMap<Integer, Queue<OrderBookEntry>> book = 
                order.isNormalizedBuy() ? bids : asks;
            
            Queue<OrderBookEntry> priceLevel = book.computeIfAbsent(
                order.getNormalizedPrice(), 
                k -> new LinkedBlockingDeque<>()
            );
            
            priceLevel.offer(order);
            orderMap.put(order.getOrderId(), order);
            
            notifyListeners(listener -> listener.onOrderAdded(marketTicker, order));
            return true;
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Cancel an order
     */
    public boolean cancelOrder(String orderId) {
        lock.writeLock().lock();
        try {
            OrderBookEntry order = orderMap.remove(orderId);
            if (order == null) {
                return false;
            }
            
            // Remove from price level
            ConcurrentSkipListMap<Integer, Queue<OrderBookEntry>> book = 
                order.isNormalizedBuy() ? bids : asks;
            
            Queue<OrderBookEntry> priceLevel = book.get(order.getNormalizedPrice());
            if (priceLevel != null) {
                priceLevel.remove(order);
                
                // Clean up empty price levels
                if (priceLevel.isEmpty()) {
                    book.remove(order.getNormalizedPrice());
                }
            }
            
            notifyListeners(listener -> listener.onOrderCanceled(marketTicker, order));
            return true;
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Get order by ID
     */
    public OrderBookEntry getOrder(String orderId) {
        return orderMap.get(orderId);
    }
    
    /**
     * Get best bid (highest buy price)
     */
    public Map.Entry<Integer, Queue<OrderBookEntry>> getBestBid() {
        lock.readLock().lock();
        try {
            return bids.firstEntry();
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Get best ask (lowest sell price)
     */
    public Map.Entry<Integer, Queue<OrderBookEntry>> getBestAsk() {
        lock.readLock().lock();
        try {
            return asks.firstEntry();
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Remove a filled order from the order map
     */
    public void removeFilledOrder(String orderId) {
        orderMap.remove(orderId);
    }
    
    /**
     * Remove empty ask level
     */
    public void removeEmptyAskLevel(int price) {
        asks.remove(price);
    }
    
    /**
     * Remove empty bid level
     */
    public void removeEmptyBidLevel(int price) {
        bids.remove(price);
    }
    
    /**
     * Notify listeners of order execution
     */
    public void notifyOrderExecuted(OrderBookEntry order, int executedQuantity) {
        notifyListeners(listener -> listener.onOrderExecuted(marketTicker, order, executedQuantity));
    }
    
    /**
     * Get orderbook snapshot in Kalshi format
     */
    public Orderbook getOrderbookSnapshot(int depth) {
        lock.readLock().lock();
        try {
            CopyOnWriteArrayList<List<Integer>> yesBids = new CopyOnWriteArrayList<>();
            List<List<Integer>> yesAsks = new CopyOnWriteArrayList<>();
            List<List<Integer>> noBids = new CopyOnWriteArrayList<>();
            List<List<Integer>> noAsks = new CopyOnWriteArrayList<>();
            
            // Aggregate orders by price level and convert back to YES/NO format
            aggregateLevels(bids, true, depth, yesBids, yesAsks, noBids, noAsks);
            aggregateLevels(asks, false, depth, yesBids, yesAsks, noBids, noAsks);
            
            // Combine YES bids and asks into a single list
            List<List<Integer>> yesOrderbook = new CopyOnWriteArrayList<>();
            yesOrderbook.addAll(yesBids);
            yesOrderbook.addAll(yesAsks);
            
            return new Orderbook(
                yesOrderbook.isEmpty() ? null : yesOrderbook,
                null  // No longer returning NO side as per design
            );
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Get orderbook snapshot in proper Kalshi API format with separated YES and NO sides
     * Returns structure matching: {"yes": [[price, qty], ...], "no": [[price, qty], ...]}
     * 
     * In Kalshi's format:
     * - YES array contains Buy YES orders (our bids where side=yes)
     * - NO array contains Buy NO orders (which we store as Sell YES)
     * 
     * Since we normalize everything to YES:
     * - Buy YES @ X → stored as Buy @ X (in bids)
     * - Buy NO @ X → stored as Sell @ (100-X) (in asks)
     */
    public OrderbookResponse.OrderbookData getOrderbookSnapshotKalshiFormat(int depth) {
        lock.readLock().lock();
        try {
            List<List<Integer>> yesSide = new ArrayList<>();
            List<List<Integer>> noSide = new ArrayList<>();
            
            // In buy-only architecture, we need to separate Buy YES and Buy NO orders
            // Both are stored in the order book, but we need to identify them correctly
            
            // Process Buy YES orders (these appear in bids)
            Map<Integer, Integer> yesLevels = new TreeMap<>(Comparator.reverseOrder()); // YES: descending
            Map<Integer, Integer> noLevels = new TreeMap<>(); // NO: ascending (natural order)
            
            // Check bids for Buy YES orders
            for (Map.Entry<Integer, Queue<OrderBookEntry>> level : bids.entrySet()) {
                for (OrderBookEntry order : level.getValue()) {
                    if (order.getSide() == KalshiSide.yes && order.getAction().equals("buy")) {
                        yesLevels.merge(order.getPrice(), order.getQuantity(), Integer::sum);
                    }
                }
            }
            
            // Check asks for Buy NO orders (they appear as Sell YES after normalization)
            for (Map.Entry<Integer, Queue<OrderBookEntry>> level : asks.entrySet()) {
                for (OrderBookEntry order : level.getValue()) {
                    if (order.getSide() == KalshiSide.no && order.getAction().equals("buy")) {
                        // This is a Buy NO order at its original price
                        noLevels.merge(order.getPrice(), order.getQuantity(), Integer::sum);
                    }
                }
            }
            
            // Also check bids for Sell NO orders (they were converted to Buy YES)
            for (Map.Entry<Integer, Queue<OrderBookEntry>> level : bids.entrySet()) {
                for (OrderBookEntry order : level.getValue()) {
                    if (order.getSide() == KalshiSide.no && order.getAction().equals("sell")) {
                        // This was originally Sell NO @ X, converted to Buy YES @ (100-X)
                        // It should appear as Buy YES at the normalized price
                        yesLevels.merge(order.getNormalizedPrice(), order.getQuantity(), Integer::sum);
                    }
                }
            }
            
            // Build YES side (up to depth)
            int count = 0;
            for (Map.Entry<Integer, Integer> entry : yesLevels.entrySet()) {
                if (count >= depth) break;
                yesSide.add(Arrays.asList(entry.getKey(), entry.getValue()));
                count++;
            }
            
            // Build NO side (up to depth)
            count = 0;
            for (Map.Entry<Integer, Integer> entry : noLevels.entrySet()) {
                if (count >= depth) break;
                noSide.add(Arrays.asList(entry.getKey(), entry.getValue()));
                count++;
            }
            
            return new OrderbookResponse.OrderbookData(yesSide, noSide);
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Check for crosses (both self-cross and external cross)
     */
    private boolean checkForCross(OrderBookEntry newOrder) {
        if (newOrder.isNormalizedBuy()) {
            // Check if buy crosses with any ask
            Map.Entry<Integer, Queue<OrderBookEntry>> bestAsk = asks.firstEntry();
            if (bestAsk != null && newOrder.getNormalizedPrice() >= bestAsk.getKey()) {
                return true; // Self-cross detected
            }
        } else {
            // Check if sell crosses with any bid
            Map.Entry<Integer, Queue<OrderBookEntry>> bestBid = bids.firstEntry();
            if (bestBid != null && newOrder.getNormalizedPrice() <= bestBid.getKey()) {
                return true; // Self-cross detected
            }
        }
        
        // Check for external cross after adding any order
        // This needs to check if YES bid + NO bid > 100 (not >= 100)
        return checkExternalCross();
    }
    
    /**
     * Check for external cross where YES bid + NO bid > 100
     */
    private boolean checkExternalCross() {
        Map.Entry<Integer, Queue<OrderBookEntry>> bestBid = bids.firstEntry();
        if (bestBid == null) return false;
        
        // Find best NO bid (which appears as YES ask from NO buy orders)
        for (Map.Entry<Integer, Queue<OrderBookEntry>> askLevel : asks.entrySet()) {
            for (OrderBookEntry order : askLevel.getValue()) {
                if (order.getSide() == KalshiSide.no && order.getAction().equals("buy")) {
                    int noBidPrice = order.getPrice();
                    int yesBidPrice = bestBid.getKey();
                    if (yesBidPrice + noBidPrice > 100) {
                        return true; // External cross detected
                    }
                    break; // Only need to check the best NO bid
                }
            }
        }
        
        return false;
    }
    
    /**
     * Aggregate orders by price level and separate into YES/NO sides
     */
    private void aggregateLevels(
            ConcurrentSkipListMap<Integer, Queue<OrderBookEntry>> book,
            boolean isBidSide,
            int maxLevels,
            List<List<Integer>> yesBids,
            List<List<Integer>> yesAsks,
            List<List<Integer>> noBids,
            List<List<Integer>> noAsks) {
        
        int levelCount = 0;
        for (Map.Entry<Integer, Queue<OrderBookEntry>> level : book.entrySet()) {
            if (levelCount >= maxLevels) break;
            
            // Aggregate all orders at this normalized price level
            int totalQuantity = 0;
            for (OrderBookEntry order : level.getValue()) {
                totalQuantity += order.getQuantity();
            }
            
            // All orders are shown as YES at their normalized price
            int normalizedPrice = level.getKey();
            List<Integer> priceLevel = new CopyOnWriteArrayList<>(Arrays.asList(normalizedPrice, totalQuantity));
            
            // Determine if this level is bid or ask based on the book it came from
            if (isBidSide) {
                yesBids.add(priceLevel);
            } else {
                yesAsks.add(priceLevel);
            }
            
            // We no longer populate NO sides as everything is normalized to YES
            
            levelCount++;
        }
        
        // Sort the lists
        yesBids.sort((a, b) -> b.get(0).compareTo(a.get(0))); // High to low
        yesAsks.sort(Comparator.comparing(a -> a.get(0))); // Low to high
        noBids.sort((a, b) -> b.get(0).compareTo(a.get(0))); // High to low
        noAsks.sort(Comparator.comparing(a -> a.get(0))); // Low to high
    }
    
    // Listener management
    public void addListener(OrderBookListener listener) {
        listeners.add(listener);
    }
    
    public void removeListener(OrderBookListener listener) {
        listeners.remove(listener);
    }
    
    private void notifyListeners(java.util.function.Consumer<OrderBookListener> action) {
        for (OrderBookListener listener : listeners) {
            try {
                action.accept(listener);
                log.info("Notified listener "+listener+" of order book event for market "+marketTicker+" (thread "+Thread.currentThread().getName()+")");
            } catch (Exception e) {
                log.info("Listener notify failed; maybe it is gone? "+e.getMessage());
                // Log error but don't let one listener break others
                // e.printStackTrace();
            }
        }
    }
    
    /**
     * Interface for order book event listeners
     */
    public interface OrderBookListener {
        void onOrderAdded(String marketTicker, OrderBookEntry order);
        void onOrderCanceled(String marketTicker, OrderBookEntry order);
        void onOrderExecuted(String marketTicker, OrderBookEntry order, int executedQuantity);
        void onCrossDetected(String marketTicker, OrderBookEntry order);
    }
}