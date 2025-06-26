package com.kalshi.mock.service;

import com.fbg.api.rest.Order;
import com.kalshi.mock.event.OrderUpdateEvent;
import com.kalshi.mock.event.OrderUpdateEventPublisher;
import com.kalshi.mock.websocket.dto.OrderUpdateMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Service for tracking all orders in memory and broadcasting order updates via WebSocket
 */
@Service
public class OrderTrackingService {
    
    private static final Logger logger = LoggerFactory.getLogger(OrderTrackingService.class);
    
    @Autowired
    private PersistenceService persistenceService;
    
    @Autowired
    private OrderUpdateEventPublisher orderUpdateEventPublisher;
    
    // In-memory order storage
    // Map<OrderId, Order>
    private final Map<String, Order> ordersById = new ConcurrentHashMap<>();
    
    // Market-specific order indexes
    // Map<MarketTicker, Set<OrderId>>
    private final Map<String, Set<String>> ordersByMarket = new ConcurrentHashMap<>();
    
    // User-specific order indexes  
    // Map<UserId, Set<OrderId>>
    private final Map<String, Set<String>> ordersByUser = new ConcurrentHashMap<>();
    
    // Status-specific order indexes
    // Map<Status, Set<OrderId>>
    private final Map<String, Set<String>> ordersByStatus = new ConcurrentHashMap<>();
    
    @PostConstruct
    public void initialize() {
        logger.info("Initializing OrderTrackingService - loading all orders from database");
        loadAllOrdersFromDatabase();
        logger.info("OrderTrackingService initialized with {} orders", ordersById.size());
    }
    
    /**
     * Load all orders from database into memory on startup
     */
    private void loadAllOrdersFromDatabase() {
        try {
            // Get all orders from database
            List<Order> allOrders = persistenceService.getAllOrders();
            
            for (Order order : allOrders) {
                addOrderToMemory(order);
            }
            
            logger.info("Loaded {} orders from database into memory", allOrders.size());
            logOrderStatistics();
            
        } catch (Exception e) {
            logger.error("Failed to load orders from database", e);
        }
    }
    
    /**
     * Add a new order to in-memory tracking and publish WebSocket update
     */
    public void trackNewOrder(Order order, String action) {
        addOrderToMemory(order);
        
        // Publish order update event
        OrderUpdateMessage updateMessage = createOrderUpdateMessage(order, action, OrderUpdateEvent.OrderUpdateType.NEW);
        OrderUpdateEvent event = new OrderUpdateEvent(updateMessage, OrderUpdateEvent.OrderUpdateType.NEW);
        orderUpdateEventPublisher.publishOrderUpdate(event);
        
        logger.debug("Tracked new order: {} for market: {}", order.getId(), order.getSymbol());
    }
    
    /**
     * Update an existing order in memory and publish WebSocket update
     */
    public void updateOrder(Order updatedOrder, String action, OrderUpdateEvent.OrderUpdateType updateType) {
        Order existingOrder = ordersById.get(updatedOrder.getId());
        if (existingOrder != null) {
            // Remove from old indexes if market or status changed
            if (!existingOrder.getSymbol().equals(updatedOrder.getSymbol())) {
                removeOrderFromMarketIndex(existingOrder);
                addOrderToMarketIndex(updatedOrder);
            }
            if (!existingOrder.getStatus().equals(updatedOrder.getStatus())) {
                removeOrderFromStatusIndex(existingOrder);
                addOrderToStatusIndex(updatedOrder);
            }
        }
        
        // Update the order in memory
        ordersById.put(updatedOrder.getId(), updatedOrder);
        
        // Ensure it's in all indexes
        addOrderToIndexes(updatedOrder);
        
        // Publish order update event
        OrderUpdateMessage updateMessage = createOrderUpdateMessage(updatedOrder, action, updateType);
        OrderUpdateEvent event = new OrderUpdateEvent(updateMessage, updateType);
        orderUpdateEventPublisher.publishOrderUpdate(event);
        
        logger.debug("Updated order: {} with type: {}", updatedOrder.getId(), updateType);
    }
    
    /**
     * Remove an order from tracking (for canceled orders)
     */
    public void removeOrder(String orderId) {
        Order order = ordersById.remove(orderId);
        if (order != null) {
            removeOrderFromIndexes(order);
            logger.debug("Removed order from tracking: {}", orderId);
        }
    }
    
    /**
     * Get all orders for a specific market
     */
    public List<Order> getOrdersForMarket(String marketTicker) {
        Set<String> orderIds = ordersByMarket.getOrDefault(marketTicker, Collections.emptySet());
        return orderIds.stream()
                .map(ordersById::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }
    
    /**
     * Get all orders for a specific user
     */
    public List<Order> getOrdersForUser(String userId) {
        Set<String> orderIds = ordersByUser.getOrDefault(userId, Collections.emptySet());
        return orderIds.stream()
                .map(ordersById::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }
    
    /**
     * Get orders by status
     */
    public List<Order> getOrdersByStatus(String status) {
        Set<String> orderIds = ordersByStatus.getOrDefault(status, Collections.emptySet());
        return orderIds.stream()
                .map(ordersById::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }
    
    /**
     * Get order by ID
     */
    public Order getOrder(String orderId) {
        return ordersById.get(orderId);
    }
    
    /**
     * Get all orders
     */
    public Collection<Order> getAllOrders() {
        return new ArrayList<>(ordersById.values());
    }
    
    /**
     * Get order count statistics
     */
    public Map<String, Integer> getOrderStatistics() {
        Map<String, Integer> stats = new HashMap<>();
        stats.put("total", ordersById.size());
        stats.put("open", ordersByStatus.getOrDefault("open", Collections.emptySet()).size());
        stats.put("filled", ordersByStatus.getOrDefault("filled", Collections.emptySet()).size());
        stats.put("partially_filled", ordersByStatus.getOrDefault("partially_filled", Collections.emptySet()).size());
        stats.put("canceled", ordersByStatus.getOrDefault("canceled", Collections.emptySet()).size());
        return stats;
    }
    
    private void addOrderToMemory(Order order) {
        ordersById.put(order.getId(), order);
        addOrderToIndexes(order);
    }
    
    private void addOrderToIndexes(Order order) {
        addOrderToMarketIndex(order);
        addOrderToUserIndex(order);
        addOrderToStatusIndex(order);
    }
    
    private void addOrderToMarketIndex(Order order) {
        ordersByMarket.computeIfAbsent(order.getSymbol(), k -> ConcurrentHashMap.newKeySet())
                     .add(order.getId());
    }
    
    private void addOrderToUserIndex(Order order) {
        ordersByUser.computeIfAbsent(order.getUser_id(), k -> ConcurrentHashMap.newKeySet())
                    .add(order.getId());
    }
    
    private void addOrderToStatusIndex(Order order) {
        ordersByStatus.computeIfAbsent(order.getStatus(), k -> ConcurrentHashMap.newKeySet())
                      .add(order.getId());
    }
    
    private void removeOrderFromIndexes(Order order) {
        removeOrderFromMarketIndex(order);
        removeOrderFromUserIndex(order);
        removeOrderFromStatusIndex(order);
    }
    
    private void removeOrderFromMarketIndex(Order order) {
        Set<String> marketOrders = ordersByMarket.get(order.getSymbol());
        if (marketOrders != null) {
            marketOrders.remove(order.getId());
            if (marketOrders.isEmpty()) {
                ordersByMarket.remove(order.getSymbol());
            }
        }
    }
    
    private void removeOrderFromUserIndex(Order order) {
        Set<String> userOrders = ordersByUser.get(order.getUser_id());
        if (userOrders != null) {
            userOrders.remove(order.getId());
            if (userOrders.isEmpty()) {
                ordersByUser.remove(order.getUser_id());
            }
        }
    }
    
    private void removeOrderFromStatusIndex(Order order) {
        Set<String> statusOrders = ordersByStatus.get(order.getStatus());
        if (statusOrders != null) {
            statusOrders.remove(order.getId());
            if (statusOrders.isEmpty()) {
                ordersByStatus.remove(order.getStatus());
            }
        }
    }
    
    private OrderUpdateMessage createOrderUpdateMessage(Order order, String action, OrderUpdateEvent.OrderUpdateType updateType) {
        LocalDateTime now = LocalDateTime.now();
        return new OrderUpdateMessage(
            order.getId(),
            order.getUser_id(), 
            order.getSymbol(),
            order.getSide().name(),
            action,
            order.getOrder_type(),
            order.getQuantity(),
            order.getFilled_quantity(),
            order.getRemaining_quantity(),
            order.getPrice() != null ? order.getPrice() : 0,
            order.getAvg_fill_price(),
            order.getStatus(),
            order.getTime_in_force(),
            LocalDateTime.ofInstant(
                java.time.Instant.ofEpochMilli(order.getCreated_time()), 
                java.time.ZoneId.systemDefault()
            ),
            now,
            updateType.name()
        );
    }
    
    private void logOrderStatistics() {
        Map<String, Integer> stats = getOrderStatistics();
        logger.info("Order statistics: Total={}, Open={}, Filled={}, Partially Filled={}, Canceled={}", 
                   stats.get("total"), stats.get("open"), stats.get("filled"), 
                   stats.get("partially_filled"), stats.get("canceled"));
        
        logger.info("Markets with orders: {}", ordersByMarket.keySet().size());
        logger.info("Users with orders: {}", ordersByUser.keySet().size());
    }
}