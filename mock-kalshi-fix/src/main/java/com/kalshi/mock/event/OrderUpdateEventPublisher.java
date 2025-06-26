package com.kalshi.mock.event;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Component
public class OrderUpdateEventPublisher {
    
    private static final Logger logger = LoggerFactory.getLogger(OrderUpdateEventPublisher.class);
    
    private final List<OrderUpdateEventListener> listeners = new CopyOnWriteArrayList<>();
    private final ExecutorService executor = Executors.newFixedThreadPool(4);
    
    public void addListener(OrderUpdateEventListener listener) {
        listeners.add(listener);
        logger.info("Added order update listener: {}", listener.getClass().getSimpleName());
    }
    
    public void removeListener(OrderUpdateEventListener listener) {
        listeners.remove(listener);
        logger.info("Removed order update listener: {}", listener.getClass().getSimpleName());
    }
    
    public void publishOrderUpdate(OrderUpdateEvent event) {
        if (listeners.isEmpty()) {
            log.warn("Zero listeners in OrderUpdateEventPublisher; not publishing order updates to any websockets!");
            return;
        }
        
        // Publish asynchronously to avoid blocking
        executor.submit(() -> {
            for (OrderUpdateEventListener listener : listeners) {
                try {
                    log.info("Notifying Order Update Listener: {} with event: {}", 
                            listener.getClass().getSimpleName(), event.toString());
                    
                    listener.onOrderUpdateEvent(event);
                    
                } catch (Exception e) {
                    logger.error("Error notifying order update listener: {}", 
                               listener.getClass().getSimpleName(), e);
                }
            }
        });
    }
    
    public void shutdown() {
        executor.shutdown();
    }
}