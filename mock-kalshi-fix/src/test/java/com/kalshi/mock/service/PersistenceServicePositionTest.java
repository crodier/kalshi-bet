package com.kalshi.mock.service;

import com.fbg.api.market.KalshiSide;
import com.fbg.api.rest.Position;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import com.kalshi.mock.config.TestDatabaseConfig;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Transactional
@Import(TestDatabaseConfig.class)
@TestPropertySource(properties = {
    "kalshi.database.path=:memory:",  // Use in-memory SQLite for tests
    "spring.datasource.driver-class-name=org.sqlite.JDBC",
    "spring.datasource.url=jdbc:sqlite::memory:",
    "spring.jpa.database-platform=org.hibernate.community.dialect.SQLiteDialect",
    "quickfix.enabled=false",
    "logging.level.org.springframework.jdbc=DEBUG"
})
public class PersistenceServicePositionTest {
    
    @Autowired
    private PersistenceService persistenceService;
    
    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    private static final String USER_ID = "TEST-USER-001";
    private static final String MARKET_ID = "MKT-TEST";
    private static final String MARKET_TICKER = "TEST-MARKET";
    
    @BeforeEach
    public void setUp() {
        // Clear positions table before each test
        jdbcTemplate.execute("DELETE FROM positions");
    }
    
    @Test
    @DisplayName("Create new long position from buy")
    public void testCreateNewLongPosition() {
        // Buy 100 contracts at 65¢
        persistenceService.updatePosition(USER_ID, MARKET_ID, MARKET_TICKER, KalshiSide.yes, 100, 65);
        
        // Verify position was created
        Position position = persistenceService.getUserPosition(USER_ID, MARKET_TICKER, KalshiSide.yes);
        assertNotNull(position);
        assertEquals(100, position.getQuantity());
        assertEquals(65, position.getAvg_price());
        assertEquals(6500, position.getTotal_cost());
    }
    
    @Test
    @DisplayName("Create new short position from sell")
    public void testCreateNewShortPosition() {
        // Sell 50 contracts at 80¢ (going short)
        persistenceService.updatePosition(USER_ID, MARKET_ID, MARKET_TICKER, KalshiSide.yes, -50, 80);
        
        // Verify short position was created
        Position position = persistenceService.getUserPosition(USER_ID, MARKET_TICKER, KalshiSide.yes);
        assertNotNull(position);
        assertEquals(-50, position.getQuantity());
        assertEquals(80, position.getAvg_price());
        assertEquals(4000, position.getTotal_cost()); // Absolute value for cost
    }
    
    @Test
    @DisplayName("Increase existing long position")
    public void testIncreaseLongPosition() {
        // Initial buy: 100 @ 60¢
        persistenceService.updatePosition(USER_ID, MARKET_ID, MARKET_TICKER, KalshiSide.yes, 100, 60);
        
        // Additional buy: 50 @ 70¢
        persistenceService.updatePosition(USER_ID, MARKET_ID, MARKET_TICKER, KalshiSide.yes, 50, 70);
        
        // Verify position was updated correctly
        Position position = persistenceService.getUserPosition(USER_ID, MARKET_TICKER, KalshiSide.yes);
        assertEquals(150, position.getQuantity());
        
        // Average price should be: (100*60 + 50*70) / 150 = 9500/150 = 63.33¢
        assertEquals(63, position.getAvg_price()); // Rounded down
        assertEquals(9500, position.getTotal_cost());
    }
    
    @Test
    @DisplayName("Reduce long position by selling")
    public void testReduceLongPosition() {
        // Initial buy: 100 @ 65¢
        persistenceService.updatePosition(USER_ID, MARKET_ID, MARKET_TICKER, KalshiSide.yes, 100, 65);
        
        // Sell 30 @ 75¢ (reducing position)
        persistenceService.updatePosition(USER_ID, MARKET_ID, MARKET_TICKER, KalshiSide.yes, -30, 75);
        
        // Verify position was reduced
        Position position = persistenceService.getUserPosition(USER_ID, MARKET_TICKER, KalshiSide.yes);
        assertEquals(70, position.getQuantity());
        assertEquals(65, position.getAvg_price()); // Avg price stays same when reducing
        assertEquals(4550, position.getTotal_cost()); // 70 * 65
    }
    
    @Test
    @DisplayName("Close entire position")
    public void testClosePosition() {
        // Initial buy: 100 @ 65¢
        persistenceService.updatePosition(USER_ID, MARKET_ID, MARKET_TICKER, KalshiSide.yes, 100, 65);
        
        // Sell all 100 @ 70¢ (closing position)
        persistenceService.updatePosition(USER_ID, MARKET_ID, MARKET_TICKER, KalshiSide.yes, -100, 70);
        
        // Verify position is closed
        Position position = persistenceService.getUserPosition(USER_ID, MARKET_TICKER, KalshiSide.yes);
        assertNotNull(position);
        assertEquals(0, position.getQuantity());
        assertEquals(0, position.getAvg_price());
        assertEquals(0, position.getTotal_cost());
    }
    
    @Test
    @DisplayName("Flip from long to short position")
    public void testFlipLongToShort() {
        // Initial buy: 100 @ 65¢
        persistenceService.updatePosition(USER_ID, MARKET_ID, MARKET_TICKER, KalshiSide.yes, 100, 65);
        
        // Sell 150 @ 70¢ (flip to short 50)
        persistenceService.updatePosition(USER_ID, MARKET_ID, MARKET_TICKER, KalshiSide.yes, -150, 70);
        
        // Verify short position
        Position position = persistenceService.getUserPosition(USER_ID, MARKET_TICKER, KalshiSide.yes);
        assertEquals(-50, position.getQuantity());
        assertEquals(70, position.getAvg_price()); // New avg price for short
        assertEquals(3500, position.getTotal_cost()); // 50 * 70
    }
    
    @Test
    @DisplayName("Multiple positions for same user different markets")
    public void testMultiplePositionsDifferentMarkets() {
        // Position 1: Long YES
        persistenceService.updatePosition(USER_ID, "MKT-1", "MARKET-1", KalshiSide.yes, 100, 65);
        
        // Position 2: Short YES different market
        persistenceService.updatePosition(USER_ID, "MKT-2", "MARKET-2", KalshiSide.yes, -50, 80);
        
        // Position 3: Long NO
        persistenceService.updatePosition(USER_ID, "MKT-3", "MARKET-3", KalshiSide.no, 200, 30);
        
        // Get all positions
        List<Position> positions = persistenceService.getUserPositions(USER_ID);
        assertEquals(3, positions.size());
        
        // Verify each position
        Position pos1 = persistenceService.getUserPosition(USER_ID, "MARKET-1", KalshiSide.yes);
        assertEquals(100, pos1.getQuantity());
        
        Position pos2 = persistenceService.getUserPosition(USER_ID, "MARKET-2", KalshiSide.yes);
        assertEquals(-50, pos2.getQuantity());
        
        Position pos3 = persistenceService.getUserPosition(USER_ID, "MARKET-3", KalshiSide.no);
        assertEquals(200, pos3.getQuantity());
    }
    
    @Test
    @DisplayName("Same market different sides (YES vs NO)")
    public void testSameMarketDifferentSides() {
        // Long YES position
        persistenceService.updatePosition(USER_ID, MARKET_ID, MARKET_TICKER, KalshiSide.yes, 100, 65);
        
        // Long NO position (same market)
        persistenceService.updatePosition(USER_ID, MARKET_ID, MARKET_TICKER, KalshiSide.no, 50, 35);
        
        // Verify both positions exist independently
        Position yesPosition = persistenceService.getUserPosition(USER_ID, MARKET_TICKER, KalshiSide.yes);
        assertEquals(100, yesPosition.getQuantity());
        assertEquals(65, yesPosition.getAvg_price());
        
        Position noPosition = persistenceService.getUserPosition(USER_ID, MARKET_TICKER, KalshiSide.no);
        assertEquals(50, noPosition.getQuantity());
        assertEquals(35, noPosition.getAvg_price());
        
        // Both should appear in user positions
        List<Position> positions = persistenceService.getUserPositions(USER_ID);
        assertEquals(2, positions.size());
    }
    
    @Test
    @DisplayName("Get positions excludes zero quantity positions")
    public void testGetPositionsExcludesZeroQuantity() {
        // Create position then close it
        persistenceService.updatePosition(USER_ID, MARKET_ID, MARKET_TICKER, KalshiSide.yes, 100, 65);
        persistenceService.updatePosition(USER_ID, MARKET_ID, MARKET_TICKER, KalshiSide.yes, -100, 70);
        
        // Create another active position
        persistenceService.updatePosition(USER_ID, "MKT-2", "MARKET-2", KalshiSide.yes, 50, 60);
        
        // Get positions should only return non-zero
        List<Position> positions = persistenceService.getUserPositions(USER_ID);
        assertEquals(1, positions.size());
        assertEquals("MARKET-2", positions.get(0).getMarket_ticker());
    }
    
    @Test
    @DisplayName("Increase short position")
    public void testIncreaseShortPosition() {
        // Initial short: sell 50 @ 80¢
        persistenceService.updatePosition(USER_ID, MARKET_ID, MARKET_TICKER, KalshiSide.yes, -50, 80);
        
        // Additional short: sell 30 @ 75¢
        persistenceService.updatePosition(USER_ID, MARKET_ID, MARKET_TICKER, KalshiSide.yes, -30, 75);
        
        // Verify position
        Position position = persistenceService.getUserPosition(USER_ID, MARKET_TICKER, KalshiSide.yes);
        assertEquals(-80, position.getQuantity());
        
        // Average price for short: (50*80 + 30*75) / 80 = 6250/80 = 78.125
        assertEquals(78, position.getAvg_price()); // Rounded down
        assertEquals(6250, position.getTotal_cost());
    }
}