package com.kalshi.orderbook.test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fbg.api.kalshi.MarketDataEnvelope;

import java.util.Random;

public class TestDataGenerator {
    
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final Random random = new Random();
    
    public static MarketDataEnvelope createSnapshot(String marketTicker, int bidLevels, int askLevels) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("id", 1);
        payload.put("type", "orderbook_snapshot");
        payload.put("market_ticker", marketTicker);
        
        ObjectNode msg = payload.putObject("msg");
        msg.put("market_ticker", marketTicker);
        msg.put("seq", random.nextLong(1000000));
        msg.put("ts", System.currentTimeMillis());
        
        // Create bid levels
        ArrayNode bids = msg.putArray("bids");
        double bidPrice = 0.50 + random.nextDouble() * 0.20; // Start between 0.50 and 0.70
        for (int i = 0; i < bidLevels; i++) {
            ArrayNode level = bids.addArray();
            level.add(Math.round(bidPrice * 100) / 100.0); // Round to cents
            level.add(random.nextInt(10000) + 1000); // Quantity between 1000 and 11000
            bidPrice -= 0.01; // Decrease by 1 cent for each level
        }
        
        // Create ask levels
        ArrayNode asks = msg.putArray("asks");
        double askPrice = bidPrice + 0.02; // Start 2 cents above best bid
        for (int i = 0; i < askLevels; i++) {
            ArrayNode level = asks.addArray();
            level.add(Math.round(askPrice * 100) / 100.0);
            level.add(random.nextInt(10000) + 1000);
            askPrice += 0.01; // Increase by 1 cent for each level
        }
        
        long now = System.currentTimeMillis();
        return new MarketDataEnvelope(
            payload,
            now - 50, // Received 50ms ago
            now,
            "orderbook_snapshot",
            marketTicker,
            random.nextLong(1000000),
            "test-generator",
            1
        );
    }
    
    public static MarketDataEnvelope createDelta(String marketTicker, int bidChanges, int askChanges) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("id", 1);
        payload.put("type", "orderbook_delta");
        payload.put("market_ticker", marketTicker);
        
        ObjectNode msg = payload.putObject("msg");
        msg.put("market_ticker", marketTicker);
        msg.put("seq", random.nextLong(1000000));
        msg.put("ts", System.currentTimeMillis());
        
        // Create bid changes
        ArrayNode bids = msg.putArray("bids");
        double bidPrice = 0.50 + random.nextDouble() * 0.20;
        for (int i = 0; i < bidChanges; i++) {
            ArrayNode level = bids.addArray();
            level.add(Math.round(bidPrice * 100) / 100.0);
            level.add(random.nextBoolean() ? 0 : random.nextInt(10000) + 1000); // Sometimes remove level
            bidPrice -= 0.01;
        }
        
        // Create ask changes
        ArrayNode asks = msg.putArray("asks");
        double askPrice = bidPrice + 0.02;
        for (int i = 0; i < askChanges; i++) {
            ArrayNode level = asks.addArray();
            level.add(Math.round(askPrice * 100) / 100.0);
            level.add(random.nextBoolean() ? 0 : random.nextInt(10000) + 1000);
            askPrice += 0.01;
        }
        
        long now = System.currentTimeMillis();
        return new MarketDataEnvelope(
            payload,
            now - 30,
            now,
            "orderbook_delta",
            marketTicker,
            random.nextLong(1000000),
            "test-generator",
            1
        );
    }
}