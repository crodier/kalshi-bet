package com.kalshi.mock.websocket.service;

import com.kalshi.mock.event.OrderBookEventListener;
import com.kalshi.mock.event.OrderBookEvent;
import com.kalshi.mock.service.OrderBookService;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.HashMap;

@Service
@Slf4j
public class StompBroadcastService implements OrderBookEventListener {
    private static final Logger logger = LoggerFactory.getLogger(StompBroadcastService.class);
    
    @Autowired
    private SimpMessagingTemplate messagingTemplate;
    
    @Autowired
    private OrderBookService orderBookService;
    
    @Override
    public void onOrderBookEvent(OrderBookEvent event) {
        try {
            String market = event.getMarketTicker();
            
            // Convert the order book event to the format expected by the WebSocket clients
            Map<String, Object> message = new HashMap<>();
            message.put("type", event.getType().toString());
            message.put("market", market);
            message.put("timestamp", event.getTimestamp());
            message.put("data", event.getData());
            
            // For snapshot events, the data already contains the orderbook snapshot
            if (event.getType() == OrderBookEvent.EventType.SNAPSHOT && event.getData() instanceof OrderBookEvent.SnapshotData) {
                OrderBookEvent.SnapshotData snapshot = (OrderBookEvent.SnapshotData) event.getData();
                message.put("yes", snapshot.getYesSide());
                message.put("no", snapshot.getNoSide());
            }
            else {
                log.error("Non snapshot events are not yet handled");
            }
            
            // Broadcast to all subscribers of this market's orderbook topic
            String destination = "/topic/orderbook/" + market;
            messagingTemplate.convertAndSend(destination, message);
            
            logger.info("Broadcasted order book event {} for market {} to {}", event.getType(), market, destination);

        } catch (Exception e) {
            logger.error("Error broadcasting order book event for market {}", event.getMarketTicker(), e);
        }
    }
    
    public void broadcastMarketData(String market, Map<String, Object> marketData) {
        try {
            // Broadcast market data updates (trades, last price, etc.)
            String destination = "/topic/market/" + market;
            messagingTemplate.convertAndSend(destination, marketData);
            
            logger.debug("Broadcasted market data update for market {} to {}", market, destination);
        } catch (Exception e) {
            logger.error("Error broadcasting market data for market {}", market, e);
        }
    }
}