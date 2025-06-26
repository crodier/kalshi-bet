package com.kalshi.marketdata.service;

import com.kalshi.marketdata.model.OrderBookState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class OrderBookManagerTest {

    private OrderBookManager orderBookManager;

    @BeforeEach
    void setUp() {
        orderBookManager = new OrderBookManager();
    }

    @Test
    void testShouldPublishFirstSnapshot() {
        // Given
        Map<String, Object> snapshot = createSnapshot("TEST-MARKET", 100L,
                Arrays.asList(Arrays.asList(65, 100), Arrays.asList(64, 200)),
                Arrays.asList(Arrays.asList(35, 150), Arrays.asList(36, 250)));

        // When
        boolean shouldPublish = orderBookManager.shouldPublishMessage(snapshot);

        // Then
        assertTrue(shouldPublish);
        assertEquals(1, orderBookManager.getTrackedMarketCount());
        assertEquals(1, orderBookManager.getBootstrappedMarketCount());
    }

    @Test
    void testShouldSkipIdenticalSnapshot() {
        // Given
        Map<String, Object> snapshot1 = createSnapshot("TEST-MARKET", 100L,
                Arrays.asList(Arrays.asList(65, 100), Arrays.asList(64, 200)),
                Arrays.asList(Arrays.asList(35, 150), Arrays.asList(36, 250)));
        
        Map<String, Object> snapshot2 = createSnapshot("TEST-MARKET", 101L,
                Arrays.asList(Arrays.asList(65, 100), Arrays.asList(64, 200)),
                Arrays.asList(Arrays.asList(35, 150), Arrays.asList(36, 250)));

        // When
        boolean shouldPublish1 = orderBookManager.shouldPublishMessage(snapshot1);
        boolean shouldPublish2 = orderBookManager.shouldPublishMessage(snapshot2);

        // Then
        assertTrue(shouldPublish1);
        assertFalse(shouldPublish2); // Should skip identical snapshot
    }

    @Test
    void testShouldPublishChangedSnapshot() {
        // Given
        Map<String, Object> snapshot1 = createSnapshot("TEST-MARKET", 100L,
                Arrays.asList(Arrays.asList(65, 100), Arrays.asList(64, 200)),
                Arrays.asList(Arrays.asList(35, 150), Arrays.asList(36, 250)));
        
        Map<String, Object> snapshot2 = createSnapshot("TEST-MARKET", 101L,
                Arrays.asList(Arrays.asList(66, 100), Arrays.asList(65, 200)), // Changed prices
                Arrays.asList(Arrays.asList(35, 150), Arrays.asList(36, 250)));

        // When
        boolean shouldPublish1 = orderBookManager.shouldPublishMessage(snapshot1);
        boolean shouldPublish2 = orderBookManager.shouldPublishMessage(snapshot2);

        // Then
        assertTrue(shouldPublish1);
        assertTrue(shouldPublish2); // Should publish changed snapshot
    }

    @Test
    void testShouldPublishDeltaThatChangesState() {
        // Given - Setup initial state with snapshot
        Map<String, Object> snapshot = createSnapshot("TEST-MARKET", 100L,
                Arrays.asList(Arrays.asList(65, 100), Arrays.asList(64, 200)),
                Arrays.asList(Arrays.asList(35, 150), Arrays.asList(36, 250)));
        orderBookManager.shouldPublishMessage(snapshot);

        // When - Apply delta that changes quantity
        Map<String, Object> delta = createDelta("TEST-MARKET", 101L, "yes", 65, 50);
        boolean shouldPublish = orderBookManager.shouldPublishMessage(delta);

        // Then
        assertTrue(shouldPublish);
    }

    @Test
    void testShouldSkipDeltaWithOldSequence() {
        // Given - Setup initial state
        Map<String, Object> snapshot = createSnapshot("TEST-MARKET", 100L,
                Arrays.asList(Arrays.asList(65, 100), Arrays.asList(64, 200)),
                Arrays.asList(Arrays.asList(35, 150), Arrays.asList(36, 250)));
        orderBookManager.shouldPublishMessage(snapshot);

        // When - Apply delta with old sequence
        Map<String, Object> delta = createDelta("TEST-MARKET", 99L, "yes", 65, 50);
        boolean shouldPublish = orderBookManager.shouldPublishMessage(delta);

        // Then
        assertFalse(shouldPublish);
    }

    @Test
    void testDeltaRemovesPriceLevel() {
        // Given - Setup initial state
        Map<String, Object> snapshot = createSnapshot("TEST-MARKET", 100L,
                Arrays.asList(Arrays.asList(65, 100), Arrays.asList(64, 200)),
                Arrays.asList(Arrays.asList(35, 150), Arrays.asList(36, 250)));
        orderBookManager.shouldPublishMessage(snapshot);

        // When - Apply delta that removes entire quantity
        Map<String, Object> delta = createDelta("TEST-MARKET", 101L, "yes", 65, -100);
        boolean shouldPublish = orderBookManager.shouldPublishMessage(delta);

        // Then
        assertTrue(shouldPublish);
        
        // Verify price level was removed
        OrderBookState state = orderBookManager.getOrderBookState("TEST-MARKET");
        assertFalse(state.getYesBids().containsKey(65));
    }

    @Test
    void testLoadHistoricalState() {
        // Given
        OrderBookState historicalState = new OrderBookState();
        historicalState.setMarketTicker("HIST-MARKET");
        historicalState.setLastSequence(500L);
        historicalState.getYesBids().put(70, 1000);
        historicalState.getNoBids().put(30, 1500);

        // When
        orderBookManager.loadHistoricalState("HIST-MARKET", historicalState);

        // Then
        assertTrue(orderBookManager.isMarketBootstrapped("HIST-MARKET"));
        assertEquals(1, orderBookManager.getTrackedMarketCount());
        assertEquals(1, orderBookManager.getBootstrappedMarketCount());
        
        // Verify state was copied
        OrderBookState loadedState = orderBookManager.getOrderBookState("HIST-MARKET");
        assertEquals(500L, loadedState.getLastSequence());
        assertEquals(1000, loadedState.getYesBids().get(70));
    }

    @Test
    void testBootstrappedMarketSkipsFirstIdenticalSnapshot() {
        // Given - Load historical state
        OrderBookState historicalState = new OrderBookState();
        historicalState.setMarketTicker("BOOT-MARKET");
        historicalState.setLastSequence(500L);
        historicalState.getYesBids().put(70, 1000);
        historicalState.getNoBids().put(30, 1500);
        orderBookManager.loadHistoricalState("BOOT-MARKET", historicalState);

        // When - Receive identical snapshot
        Map<String, Object> snapshot = createSnapshot("BOOT-MARKET", 501L,
                Arrays.asList(Arrays.asList(70, 1000)),
                Arrays.asList(Arrays.asList(30, 1500)));
        boolean shouldPublish = orderBookManager.shouldPublishMessage(snapshot);

        // Then
        assertFalse(shouldPublish); // Should skip because it matches bootstrapped state
    }

    @Test
    void testNonOrderbookMessagesAlwaysPublish() {
        // Given
        Map<String, Object> tickerMessage = new HashMap<>();
        tickerMessage.put("channel", "ticker_v2");
        tickerMessage.put("market_ticker", "TEST-MARKET");
        tickerMessage.put("data", Map.of("price", 50));

        Map<String, Object> tradeMessage = new HashMap<>();
        tradeMessage.put("channel", "trade");
        tradeMessage.put("market_ticker", "TEST-MARKET");
        tradeMessage.put("data", Map.of("price", 65, "size", 10));

        // When
        boolean shouldPublishTicker = orderBookManager.shouldPublishMessage(tickerMessage);
        boolean shouldPublishTrade = orderBookManager.shouldPublishMessage(tradeMessage);

        // Then
        assertTrue(shouldPublishTicker);
        assertTrue(shouldPublishTrade);
    }

    @Test
    void testClearAll() {
        // Given
        OrderBookState state1 = new OrderBookState();
        state1.setMarketTicker("MARKET1");
        state1.setLastSequence(100L);
        orderBookManager.loadHistoricalState("MARKET1", state1);
        
        OrderBookState state2 = new OrderBookState();
        state2.setMarketTicker("MARKET2");
        state2.setLastSequence(200L);
        orderBookManager.loadHistoricalState("MARKET2", state2);

        // When
        orderBookManager.clearAll();

        // Then
        assertEquals(0, orderBookManager.getTrackedMarketCount());
        assertEquals(0, orderBookManager.getBootstrappedMarketCount());
    }

    // Helper methods
    private Map<String, Object> createSnapshot(String marketTicker, Long sequence,
                                              Object yesBids, Object noBids) {
        Map<String, Object> message = new HashMap<>();
        message.put("channel", "orderbook_snapshot");
        message.put("market_ticker", marketTicker);
        message.put("seq", sequence);
        
        Map<String, Object> data = new HashMap<>();
        data.put("yes", yesBids);
        data.put("no", noBids);
        message.put("data", data);
        
        return message;
    }

    private Map<String, Object> createDelta(String marketTicker, Long sequence,
                                          String side, Integer price, Integer delta) {
        Map<String, Object> message = new HashMap<>();
        message.put("channel", "orderbook_delta");
        message.put("market_ticker", marketTicker);
        message.put("seq", sequence);
        
        Map<String, Object> data = new HashMap<>();
        data.put("side", side);
        data.put("price", price);
        data.put("delta", delta);
        message.put("data", data);
        
        return message;
    }
}