package com.kalshi.marketdata.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class OrderBookStateTest {

    private OrderBookState orderBookState;

    @BeforeEach
    void setUp() {
        orderBookState = new OrderBookState();
        orderBookState.setMarketTicker("TEST-MARKET");
    }

    @Test
    void testApplySnapshot() {
        // Given
        Map<String, Object> snapshotData = createSnapshotData(
                Arrays.asList(Arrays.asList(65, 100), Arrays.asList(64, 200)),
                Arrays.asList(Arrays.asList(35, 150), Arrays.asList(36, 250))
        );

        // When
        orderBookState.applySnapshot(snapshotData, 100L);

        // Then
        assertEquals(100L, orderBookState.getLastSequence());
        assertNotNull(orderBookState.getLastUpdateTimestamp());
        assertEquals(2, orderBookState.getYesBids().size());
        assertEquals(2, orderBookState.getNoBids().size());
        assertEquals(100, orderBookState.getYesBids().get(65));
        assertEquals(200, orderBookState.getYesBids().get(64));
        assertEquals(150, orderBookState.getNoBids().get(35));
        assertEquals(250, orderBookState.getNoBids().get(36));
    }

    @Test
    void testApplySnapshotClearsExistingState() {
        // Given - Initial state
        orderBookState.getYesBids().put(70, 500);
        orderBookState.getNoBids().put(30, 600);
        
        Map<String, Object> snapshotData = createSnapshotData(
                Arrays.asList(Arrays.asList(65, 100)),
                Arrays.asList(Arrays.asList(35, 150))
        );

        // When
        orderBookState.applySnapshot(snapshotData, 101L);

        // Then - Old state should be cleared
        assertEquals(1, orderBookState.getYesBids().size());
        assertEquals(1, orderBookState.getNoBids().size());
        assertFalse(orderBookState.getYesBids().containsKey(70));
        assertFalse(orderBookState.getNoBids().containsKey(30));
    }

    @Test
    void testApplyDeltaAddQuantity() {
        // Given - Initial state
        orderBookState.getYesBids().put(65, 100);
        orderBookState.setLastSequence(100L);

        Map<String, Object> deltaData = createDeltaData("yes", 65, 50);

        // When
        boolean changed = orderBookState.applyDelta(deltaData, 101L);

        // Then
        assertTrue(changed);
        assertEquals(150, orderBookState.getYesBids().get(65));
        assertEquals(101L, orderBookState.getLastSequence());
    }

    @Test
    void testApplyDeltaNewPriceLevel() {
        // Given - Empty book
        orderBookState.setLastSequence(100L);
        Map<String, Object> deltaData = createDeltaData("yes", 66, 200);

        // When
        boolean changed = orderBookState.applyDelta(deltaData, 101L);

        // Then
        assertTrue(changed);
        assertEquals(200, orderBookState.getYesBids().get(66));
    }

    @Test
    void testApplyDeltaRemovePriceLevel() {
        // Given - Initial state
        orderBookState.getYesBids().put(65, 100);
        orderBookState.setLastSequence(100L);

        Map<String, Object> deltaData = createDeltaData("yes", 65, -100);

        // When
        boolean changed = orderBookState.applyDelta(deltaData, 101L);

        // Then
        assertTrue(changed);
        assertFalse(orderBookState.getYesBids().containsKey(65));
    }

    @Test
    void testApplyDeltaSkipsOldSequence() {
        // Given
        orderBookState.setLastSequence(100L);
        orderBookState.getYesBids().put(65, 100);

        Map<String, Object> deltaData = createDeltaData("yes", 65, 50);

        // When - Apply with old sequence
        boolean changed = orderBookState.applyDelta(deltaData, 99L);

        // Then
        assertFalse(changed);
        assertEquals(100, orderBookState.getYesBids().get(65)); // Unchanged
        assertEquals(100L, orderBookState.getLastSequence()); // Unchanged
    }

    @Test
    void testApplyDeltaNoChange() {
        // Given - Try to remove from non-existent price level
        orderBookState.setLastSequence(100L);
        Map<String, Object> deltaData = createDeltaData("yes", 65, -50);

        // When
        boolean changed = orderBookState.applyDelta(deltaData, 101L);

        // Then
        assertFalse(changed);
        assertEquals(100L, orderBookState.getLastSequence()); // Should not update sequence
    }

    @Test
    void testIsSnapshotIdentical() {
        // Given - Set up initial state
        Map<String, Object> snapshotData1 = createSnapshotData(
                Arrays.asList(Arrays.asList(65, 100), Arrays.asList(64, 200)),
                Arrays.asList(Arrays.asList(35, 150))
        );
        orderBookState.applySnapshot(snapshotData1, 100L);

        // When - Check identical snapshot
        Map<String, Object> identicalSnapshot = createSnapshotData(
                Arrays.asList(Arrays.asList(65, 100), Arrays.asList(64, 200)),
                Arrays.asList(Arrays.asList(35, 150))
        );
        boolean isIdentical = orderBookState.isSnapshotIdentical(identicalSnapshot);

        // Then
        assertTrue(isIdentical);
    }

    @Test
    void testIsSnapshotDifferentQuantity() {
        // Given
        Map<String, Object> snapshotData1 = createSnapshotData(
                Arrays.asList(Arrays.asList(65, 100)),
                Arrays.asList(Arrays.asList(35, 150))
        );
        orderBookState.applySnapshot(snapshotData1, 100L);

        // When - Different quantity
        Map<String, Object> differentSnapshot = createSnapshotData(
                Arrays.asList(Arrays.asList(65, 101)), // Different quantity
                Arrays.asList(Arrays.asList(35, 150))
        );
        boolean isIdentical = orderBookState.isSnapshotIdentical(differentSnapshot);

        // Then
        assertFalse(isIdentical);
    }

    @Test
    void testIsSnapshotDifferentPrice() {
        // Given
        Map<String, Object> snapshotData1 = createSnapshotData(
                Arrays.asList(Arrays.asList(65, 100)),
                Arrays.asList(Arrays.asList(35, 150))
        );
        orderBookState.applySnapshot(snapshotData1, 100L);

        // When - Different price level
        Map<String, Object> differentSnapshot = createSnapshotData(
                Arrays.asList(Arrays.asList(66, 100)), // Different price
                Arrays.asList(Arrays.asList(35, 150))
        );
        boolean isIdentical = orderBookState.isSnapshotIdentical(differentSnapshot);

        // Then
        assertFalse(isIdentical);
    }

    @Test
    void testCopy() {
        // Given
        orderBookState.setMarketTicker("ORIG-MARKET");
        orderBookState.setLastSequence(100L);
        orderBookState.setLastUpdateTimestamp(123456789L);
        orderBookState.getYesBids().put(65, 100);
        orderBookState.getNoBids().put(35, 150);

        // When
        OrderBookState copy = orderBookState.copy();

        // Then
        assertEquals(orderBookState.getMarketTicker(), copy.getMarketTicker());
        assertEquals(orderBookState.getLastSequence(), copy.getLastSequence());
        assertEquals(orderBookState.getLastUpdateTimestamp(), copy.getLastUpdateTimestamp());
        assertEquals(orderBookState.getYesBids(), copy.getYesBids());
        assertEquals(orderBookState.getNoBids(), copy.getNoBids());

        // Verify deep copy
        copy.getYesBids().put(66, 200);
        assertFalse(orderBookState.getYesBids().containsKey(66));
    }

    @Test
    void testTreeMapOrdering() {
        // Given - Add prices out of order
        orderBookState.getYesBids().put(65, 100);
        orderBookState.getYesBids().put(67, 300);
        orderBookState.getYesBids().put(66, 200);

        // When
        Integer[] prices = orderBookState.getYesBids().keySet().toArray(new Integer[0]);

        // Then - Should be in natural order
        assertArrayEquals(new Integer[]{65, 66, 67}, prices);
    }

    // Helper methods
    private Map<String, Object> createSnapshotData(Object yesBids, Object noBids) {
        Map<String, Object> snapshotData = new HashMap<>();
        Map<String, Object> data = new HashMap<>();
        data.put("yes", yesBids);
        data.put("no", noBids);
        snapshotData.put("data", data);
        return snapshotData;
    }

    private Map<String, Object> createDeltaData(String side, Integer price, Integer delta) {
        Map<String, Object> deltaData = new HashMap<>();
        Map<String, Object> data = new HashMap<>();
        data.put("side", side);
        data.put("price", price);
        data.put("delta", delta);
        deltaData.put("data", data);
        return deltaData;
    }
}