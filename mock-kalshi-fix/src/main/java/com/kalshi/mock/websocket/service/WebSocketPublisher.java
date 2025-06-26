package com.kalshi.mock.websocket.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kalshi.mock.event.OrderBookEvent;
import com.kalshi.mock.event.OrderBookEventListener;
import com.kalshi.mock.event.OrderBookEventPublisher;
import com.kalshi.mock.websocket.dto.*;
import com.kalshi.mock.websocket.handler.KalshiWebSocketHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class WebSocketPublisher implements OrderBookEventListener {
    
    private static final Logger logger = LoggerFactory.getLogger(WebSocketPublisher.class);
    
    @Autowired
    private SubscriptionManager subscriptionManager;
    
    @Autowired
    private KalshiWebSocketHandler webSocketHandler;
    
    @Autowired
    private OrderBookEventPublisher eventPublisher;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    private final AtomicLong sequenceNumber = new AtomicLong(1);
    
    @PostConstruct
    public void init() {
        eventPublisher.addListener(this);
        logger.info("WebSocketPublisher initialized and listening for order book events");
    }
    
    @Override
    public void onOrderBookEvent(OrderBookEvent event) {
        try {
            switch (event.getType()) {
                case SNAPSHOT:
                    handleSnapshotEvent(event);
                    break;
                case DELTA:
                    handleDeltaEvent(event);
                    break;
                case TRADE:
                    handleTradeEvent(event);
                    break;
                case TICKER_UPDATE:
                    handleTickerEvent(event);
                    break;
            }
        } catch (Exception e) {
            logger.error("Error handling order book event", e);
        }
    }
    
    private void handleSnapshotEvent(OrderBookEvent event) throws IOException {
        OrderBookEvent.SnapshotData data = (OrderBookEvent.SnapshotData) event.getData();
        
        // Get subscribers for this market's orderbook_snapshot channel
        Set<String> subscribers = subscriptionManager.getSubscribedSessions(
            event.getMarketTicker(), 
            "orderbook_snapshot"
        );
        
        if (subscribers.isEmpty()) {
            return;
        }
        
        // Create snapshot message
        WebSocketMessage message = new WebSocketMessage();
        message.setType("orderbook_snapshot");
        message.setSeq(sequenceNumber.getAndIncrement());
        
        OrderbookSnapshot snapshot = new OrderbookSnapshot();
        snapshot.setMarketTicker(event.getMarketTicker());
        snapshot.setYes(data.getYesSide());
        snapshot.setNo(data.getNoSide());
        
        message.setMsg(snapshot);
        
        // Send to all subscribers
        String jsonMessage = objectMapper.writeValueAsString(message);
        for (String sessionId : subscribers) {
            try {
                webSocketHandler.sendMessage(sessionId, jsonMessage);
            } catch (IOException e) {
                logger.error("Failed to send snapshot to session: {}", sessionId, e);
            }
        }
    }
    
    private void handleDeltaEvent(OrderBookEvent event) throws IOException {
        OrderBookEvent.DeltaData data = (OrderBookEvent.DeltaData) event.getData();
        
        // Get subscribers for this market's orderbook_delta channel
        Set<String> subscribers = subscriptionManager.getSubscribedSessions(
            event.getMarketTicker(), 
            "orderbook_delta"
        );
        
        if (subscribers.isEmpty()) {
            return;
        }
        
        // Create delta message
        WebSocketMessage message = new WebSocketMessage();
        message.setType("orderbook_delta");
        message.setSeq(sequenceNumber.getAndIncrement());
        
        OrderbookDelta delta = new OrderbookDelta();
        delta.setMarketTicker(event.getMarketTicker());
        delta.setPrice(data.getPrice());
        delta.setDelta(data.getDelta());
        delta.setSide(data.getSide());
        
        message.setMsg(delta);
        
        // Send to all subscribers
        String jsonMessage = objectMapper.writeValueAsString(message);
        for (String sessionId : subscribers) {
            try {
                webSocketHandler.sendMessage(sessionId, jsonMessage);
            } catch (IOException e) {
                logger.error("Failed to send delta to session: {}", sessionId, e);
            }
        }
    }
    
    private void handleTradeEvent(OrderBookEvent event) throws IOException {
        OrderBookEvent.TradeData data = (OrderBookEvent.TradeData) event.getData();
        
        // Get subscribers for this market's trade channel
        Set<String> subscribers = subscriptionManager.getSubscribedSessions(
            event.getMarketTicker(), 
            "trade"
        );
        
        if (subscribers.isEmpty()) {
            return;
        }
        
        // Create trade message
        WebSocketMessage message = new WebSocketMessage();
        message.setType("trade");
        message.setSeq(sequenceNumber.getAndIncrement());
        
        TradeMessage trade = new TradeMessage();
        trade.setMarketTicker(event.getMarketTicker());
        trade.setPrice(data.getPrice());
        trade.setCount(data.getCount());
        trade.setSide(data.getSide());
        trade.setCreatedTime(Instant.now().toString());
        trade.setTradeId(data.getTradeId());
        
        message.setMsg(trade);
        
        // Send to all subscribers
        String jsonMessage = objectMapper.writeValueAsString(message);
        for (String sessionId : subscribers) {
            try {
                webSocketHandler.sendMessage(sessionId, jsonMessage);
            } catch (IOException e) {
                logger.error("Failed to send trade to session: {}", sessionId, e);
            }
        }
    }
    
    private void handleTickerEvent(OrderBookEvent event) throws IOException {
        // Get subscribers for this market's ticker channel
        Set<String> subscribers = subscriptionManager.getSubscribedSessions(
            event.getMarketTicker(), 
            "ticker"
        );
        
        if (subscribers.isEmpty()) {
            return;
        }
        
        // Create ticker message
        WebSocketMessage message = new WebSocketMessage();
        message.setType("ticker");
        message.setSeq(sequenceNumber.getAndIncrement());
        
        // Cast data to appropriate ticker data type
        message.setMsg(event.getData());
        
        // Send to all subscribers
        String jsonMessage = objectMapper.writeValueAsString(message);
        for (String sessionId : subscribers) {
            try {
                webSocketHandler.sendMessage(sessionId, jsonMessage);
            } catch (IOException e) {
                logger.error("Failed to send ticker to session: {}", sessionId, e);
            }
        }
    }
    
    public void sendFillToUser(String userId, FillMessage fill) {
        // Find sessions for this user and send fill messages
        // This would require tracking user ID to session mapping
        logger.info("Fill message for user {}: {}", userId, fill);
    }
}