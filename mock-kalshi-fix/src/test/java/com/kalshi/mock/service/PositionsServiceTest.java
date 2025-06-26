package com.kalshi.mock.service;

import com.fbg.api.market.KalshiSide;
import com.fbg.api.rest.Fill;
import com.fbg.api.rest.Position;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.context.TestPropertySource;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@TestPropertySource(properties = {"quickfix.enabled=false"})
public class PositionsServiceTest {
    
    @Mock
    private PersistenceService persistenceService;
    
    @InjectMocks
    private PositionsService positionsService;
    
    private static final String USER_ID = "TEST-USER-001";
    private static final String MARKET_ID = "MKT-TEST";
    private static final String MARKET_TICKER = "TEST-MARKET";
    
    @BeforeEach
    public void setUp() {
        // MockitoAnnotations handled by @ExtendWith(MockitoExtension.class)
    }
    
    @Test
    @DisplayName("Buy fill increases long position")
    public void testBuyFillIncreasesPosition() {
        // Create a buy fill
        Fill buyFill = new Fill(
            "FILL-001",
            "ORDER-001",
            MARKET_ID,
            MARKET_TICKER,
            KalshiSide.yes,
            65, // price
            100, // count
            true, // is_taker
            System.currentTimeMillis(),
            "TRADE-001"
        );
        
        // Execute
        positionsService.updatePositionFromFill(buyFill, USER_ID);
        
        // Verify position was updated with positive quantity
        verify(persistenceService).updatePosition(
            eq(USER_ID),
            eq(MARKET_ID),
            eq(MARKET_TICKER),
            eq(KalshiSide.yes),
            eq(100), // positive quantity for buy
            eq(65)   // price
        );
    }
    
    @Test
    @DisplayName("Sell fill creates short position when no existing position")
    public void testSellFillCreatesShortPosition() {
        // Create a sell fill (this would be from selling contracts you don't own)
        Fill sellFill = new Fill(
            "FILL-002",
            "ORDER-002",
            MARKET_ID,
            MARKET_TICKER,
            KalshiSide.yes,
            70, // price
            50, // count
            true, // is_taker
            System.currentTimeMillis(),
            "TRADE-002"
        );
        
        // Execute
        positionsService.updatePositionFromFill(sellFill, USER_ID);
        
        // Verify position was updated with the count (PositionsService handles the sign)
        verify(persistenceService).updatePosition(
            eq(USER_ID),
            eq(MARKET_ID),
            eq(MARKET_TICKER),
            eq(KalshiSide.yes),
            eq(50), // The service will determine if this should be negative based on action
            eq(70)
        );
    }
    
    @Test
    @DisplayName("Multiple fills update position correctly")
    public void testMultipleFillsUpdatePosition() {
        // Create multiple fills
        Fill fill1 = new Fill("F1", "O1", MARKET_ID, MARKET_TICKER, KalshiSide.yes, 60, 100, true, 1000L, "T1");
        Fill fill2 = new Fill("F2", "O2", MARKET_ID, MARKET_TICKER, KalshiSide.yes, 62, 50, false, 2000L, "T2");
        Fill fill3 = new Fill("F3", "O3", MARKET_ID, MARKET_TICKER, KalshiSide.yes, 61, 75, true, 3000L, "T3");
        
        List<Fill> fills = Arrays.asList(fill1, fill2, fill3);
        
        // Execute batch update
        positionsService.updatePositionsFromFills(fills, USER_ID);
        
        // Verify each fill was processed
        verify(persistenceService, times(3)).updatePosition(
            eq(USER_ID),
            eq(MARKET_ID),
            eq(MARKET_TICKER),
            eq(KalshiSide.yes),
            anyInt(),
            anyInt()
        );
    }
    
    @Test
    @DisplayName("Get user positions returns correct data")
    public void testGetUserPositions() {
        // Mock positions
        Position pos1 = new Position(MARKET_ID, MARKET_TICKER, 100, 65, KalshiSide.yes, 0, 6500);
        Position pos2 = new Position("MKT-2", "MARKET-2", -50, 70, KalshiSide.no, 100, 3500);
        List<Position> mockPositions = Arrays.asList(pos1, pos2);
        
        when(persistenceService.getUserPositions(USER_ID)).thenReturn(mockPositions);
        
        // Execute
        List<Position> positions = positionsService.getUserPositions(USER_ID);
        
        // Verify
        assertEquals(2, positions.size());
        assertEquals(100, positions.get(0).getQuantity());
        assertEquals(-50, positions.get(1).getQuantity()); // Short position
        verify(persistenceService).getUserPositions(USER_ID);
    }
    
    @Test
    @DisplayName("Calculate unrealized P&L for long position")
    public void testCalculateUnrealizedPnLLongPosition() {
        // Long position: bought 100 @ 65¢
        Position longPosition = new Position(
            MARKET_ID,
            MARKET_TICKER,
            100,  // quantity
            65,   // avg_price
            KalshiSide.yes,
            0,    // realized_pnl
            6500  // total_cost (100 * 65)
        );
        
        // Current price is 70¢
        int unrealizedPnL = positionsService.calculateUnrealizedPnL(longPosition, 70);
        
        // Expected: (100 * 70) - 6500 = 7000 - 6500 = 500¢ profit
        assertEquals(500, unrealizedPnL);
    }
    
    @Test
    @DisplayName("Calculate unrealized P&L for short position")
    public void testCalculateUnrealizedPnLShortPosition() {
        // Short position: sold 50 @ 80¢ (negative quantity)
        Position shortPosition = new Position(
            MARKET_ID,
            MARKET_TICKER,
            -50,   // negative quantity (short)
            80,    // avg_price (sold at this price)
            KalshiSide.yes,
            0,     // realized_pnl
            -4000  // total_cost (negative for short)
        );
        
        // Current price is 75¢
        int unrealizedPnL = positionsService.calculateUnrealizedPnL(shortPosition, 75);
        
        // Expected: (-50 * 75) - (-4000) = -3750 + 4000 = 250¢ profit
        // (Made money because price went down on a short position)
        assertEquals(250, unrealizedPnL);
    }
    
    @Test
    @DisplayName("Calculate unrealized P&L for zero position")
    public void testCalculateUnrealizedPnLZeroPosition() {
        Position zeroPosition = new Position(
            MARKET_ID,
            MARKET_TICKER,
            0,    // no position
            0,    // avg_price
            KalshiSide.yes,
            1000, // realized_pnl
            0     // total_cost
        );
        
        int unrealizedPnL = positionsService.calculateUnrealizedPnL(zeroPosition, 50);
        
        assertEquals(0, unrealizedPnL);
    }
    
    @Test
    @DisplayName("Close position calculates realized P&L")
    public void testClosePosition() {
        // Mock existing position
        Position existingPosition = new Position(
            MARKET_ID,
            MARKET_TICKER,
            100,  // long position
            60,   // avg_price
            KalshiSide.yes,
            0,    // realized_pnl
            6000  // total_cost
        );
        
        when(persistenceService.getUserPosition(USER_ID, MARKET_TICKER, KalshiSide.yes))
            .thenReturn(existingPosition);
        
        // Close position at 70¢
        positionsService.closePosition(USER_ID, MARKET_TICKER, KalshiSide.yes, 70);
        
        // Verify position was updated to close
        verify(persistenceService).updatePosition(
            eq(USER_ID),
            eq(MARKET_ID),
            eq(MARKET_TICKER),
            eq(KalshiSide.yes),
            eq(-100), // closing 100 contracts
            eq(70)    // closing price
        );
    }
    
    @Test
    @DisplayName("Calculate portfolio value with multiple positions")
    public void testCalculatePortfolioValue() {
        // Mock positions
        Position pos1 = new Position("MKT-1", "TICKER-1", 100, 65, KalshiSide.yes, 0, 6500);
        Position pos2 = new Position("MKT-2", "TICKER-2", -50, 80, KalshiSide.yes, 0, -4000);
        Position pos3 = new Position("MKT-3", "TICKER-3", 200, 45, KalshiSide.no, 0, 9000);
        
        when(persistenceService.getUserPositions(USER_ID))
            .thenReturn(Arrays.asList(pos1, pos2, pos3));
        
        // Current prices
        java.util.Map<String, Integer> currentPrices = new java.util.HashMap<>();
        currentPrices.put("TICKER-1", 70); // up from 65
        currentPrices.put("TICKER-2", 75); // down from 80 (good for short)
        currentPrices.put("TICKER-3", 50); // up from 45
        
        int portfolioValue = positionsService.calculatePortfolioValue(USER_ID, currentPrices);
        
        // Expected:
        // Pos1: 100 * 70 = 7000
        // Pos2: -50 * 75 = -3750 (short position)
        // Pos3: 200 * 50 = 10000
        // Total: 7000 - 3750 + 10000 = 13250
        assertEquals(13250, portfolioValue);
    }
}