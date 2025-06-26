package com.kalshi.mock.catalog.service;

import com.kalshi.mock.catalog.model.Market;
import com.kalshi.mock.catalog.model.Market.MarketStatus;
import com.kalshi.mock.catalog.model.Market.MarketType;
import com.kalshi.mock.service.OrderBookService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Service class for managing Market entities in the catalog system.
 * Provides full CRUD operations with complex filtering and integration with OrderBookService.
 */
@Service
public class MarketService {
    
    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    @Autowired
    private OrderBookService orderBookService;
    
    // Market RowMapper
    private final RowMapper<Market> marketRowMapper = new RowMapper<Market>() {
        @Override
        public Market mapRow(ResultSet rs, int rowNum) throws SQLException {
            Market market = new Market();
            market.setTicker(rs.getString("ticker"));
            market.setEventTicker(rs.getString("event_ticker"));
            market.setMarketType(MarketType.valueOf(rs.getString("market_type").toUpperCase()));
            market.setTitle(rs.getString("title"));
            market.setSubtitle(rs.getString("subtitle"));
            market.setYesSubtitle(rs.getString("yes_subtitle"));
            market.setNoSubtitle(rs.getString("no_subtitle"));
            
            // Handle BIGINT timestamps stored as milliseconds
            Long openTimeMillis = rs.getLong("open_time");
            if (openTimeMillis != null && openTimeMillis > 0) {
                market.setOpenTime(LocalDateTime.ofInstant(
                    java.time.Instant.ofEpochMilli(openTimeMillis), 
                    java.time.ZoneId.systemDefault()));
            }
            
            Long closeTimeMillis = rs.getLong("close_time");
            if (closeTimeMillis != null && closeTimeMillis > 0) {
                market.setCloseTime(LocalDateTime.ofInstant(
                    java.time.Instant.ofEpochMilli(closeTimeMillis), 
                    java.time.ZoneId.systemDefault()));
            }
            
            Long expectedExpTimeMillis = rs.getLong("expected_expiration_time");
            if (expectedExpTimeMillis != null && expectedExpTimeMillis > 0) {
                market.setExpectedExpirationTime(LocalDateTime.ofInstant(
                    java.time.Instant.ofEpochMilli(expectedExpTimeMillis), 
                    java.time.ZoneId.systemDefault()));
            }
            
            Long expTimeMillis = rs.getLong("expiration_time");
            if (expTimeMillis != null && expTimeMillis > 0 && !rs.wasNull()) {
                market.setExpirationTime(LocalDateTime.ofInstant(
                    java.time.Instant.ofEpochMilli(expTimeMillis), 
                    java.time.ZoneId.systemDefault()));
            }
            
            market.setStatus(MarketStatus.valueOf(rs.getString("status").toUpperCase()));
            
            // Price information
            market.setYesBid(rs.getBigDecimal("yes_bid"));
            market.setYesAsk(rs.getBigDecimal("yes_ask"));
            market.setNoBid(rs.getBigDecimal("no_bid"));
            market.setNoAsk(rs.getBigDecimal("no_ask"));
            market.setLastPrice(rs.getBigDecimal("last_price"));
            market.setPreviousYesBid(rs.getBigDecimal("previous_yes_bid"));
            market.setPreviousYesAsk(rs.getBigDecimal("previous_yes_ask"));
            market.setPreviousPrice(rs.getBigDecimal("previous_price"));
            
            // Volume information
            market.setVolume(rs.getLong("volume"));
            market.setVolume24h(rs.getLong("volume_24h"));
            market.setLiquidity(rs.getBigDecimal("liquidity"));
            market.setOpenInterest(rs.getLong("open_interest"));
            
            // Financial information
            market.setNotionalValue(rs.getBigDecimal("notional_value"));
            market.setRiskLimitCents(rs.getLong("risk_limit_cents"));
            
            // Market details
            market.setStrikeType(rs.getString("strike_type"));
            market.setFloorStrike(rs.getBigDecimal("floor_strike"));
            market.setCapStrike(rs.getBigDecimal("cap_strike"));
            market.setResult(rs.getString("result"));
            market.setCanCloseEarly(rs.getBoolean("can_close_early"));
            market.setExpirationValue(rs.getString("expiration_value"));
            market.setCategory(rs.getString("category"));
            
            // Rules
            market.setRulesPrimary(rs.getString("rules_primary"));
            market.setRulesSecondary(rs.getString("rules_secondary"));
            
            // Additional metadata
            market.setResponsePriceUnits(rs.getString("response_price_units"));
            market.setSettlementTimerSeconds(rs.getInt("settlement_timer_seconds"));
            market.setSettlementSource(rs.getString("settlement_source"));
            market.setCustomStrike(rs.getString("custom_strike"));
            market.setIsDeactivated(rs.getBoolean("is_deactivated"));
            
            // Handle timestamp fields (if they exist)
            try {
                Timestamp createdAt = rs.getTimestamp("created_at");
                if (createdAt != null) {
                    market.setCreatedAt(createdAt.toLocalDateTime());
                }
            } catch (Exception e) {
                // created_at might not exist in the result set
            }
            
            try {
                Timestamp updatedAt = rs.getTimestamp("updated_at");
                if (updatedAt != null) {
                    market.setUpdatedAt(updatedAt.toLocalDateTime());
                }
            } catch (Exception e) {
                // updated_at might not exist in the result set
            }
            
            return market;
        }
    };
    
    /**
     * Create a new market
     */
    @Transactional
    public Market createMarket(Market market) {
        // Validate market doesn't already exist
        if (existsMarket(market.getTicker())) {
            throw new IllegalArgumentException("Market with ticker " + market.getTicker() + " already exists");
        }
        
        // Validate event exists
        String checkEventSql = "SELECT COUNT(*) FROM events WHERE event_ticker = ?";
        Integer eventCount = jdbcTemplate.queryForObject(checkEventSql, Integer.class, market.getEventTicker());
        if (eventCount == null || eventCount == 0) {
            throw new IllegalArgumentException("Event not found: " + market.getEventTicker());
        }
        
        LocalDateTime now = LocalDateTime.now();
        market.setCreatedAt(now);
        market.setUpdatedAt(now);
        
        // Set default values if not provided
        if (market.getVolume() == null) market.setVolume(0L);
        if (market.getVolume24h() == null) market.setVolume24h(0L);
        if (market.getOpenInterest() == null) market.setOpenInterest(0L);
        if (market.getCanCloseEarly() == null) market.setCanCloseEarly(false);
        if (market.getIsDeactivated() == null) market.setIsDeactivated(false);
        
        String sql = """
            INSERT INTO markets (
                ticker, event_ticker, market_type, title, subtitle,
                yes_subtitle, no_subtitle, open_time, close_time,
                expected_expiration_time, expiration_time, status,
                yes_bid, yes_ask, no_bid, no_ask, last_price,
                previous_yes_bid, previous_yes_ask, previous_price,
                volume, volume_24h, liquidity, open_interest,
                notional_value, risk_limit_cents, strike_type,
                floor_strike, cap_strike, result, can_close_early,
                expiration_value, category, rules_primary, rules_secondary,
                response_price_units, settlement_timer_seconds,
                settlement_source, custom_strike, is_deactivated,
                created_at, updated_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;
        
        jdbcTemplate.update(sql,
            market.getTicker(),
            market.getEventTicker(),
            market.getMarketType().name(),
            market.getTitle(),
            market.getSubtitle(),
            market.getYesSubtitle(),
            market.getNoSubtitle(),
            market.getOpenTime() != null ? Timestamp.valueOf(market.getOpenTime()) : null,
            market.getCloseTime() != null ? Timestamp.valueOf(market.getCloseTime()) : null,
            market.getExpectedExpirationTime() != null ? Timestamp.valueOf(market.getExpectedExpirationTime()) : null,
            market.getExpirationTime() != null ? Timestamp.valueOf(market.getExpirationTime()) : null,
            market.getStatus().name(),
            market.getYesBid(),
            market.getYesAsk(),
            market.getNoBid(),
            market.getNoAsk(),
            market.getLastPrice(),
            market.getPreviousYesBid(),
            market.getPreviousYesAsk(),
            market.getPreviousPrice(),
            market.getVolume(),
            market.getVolume24h(),
            market.getLiquidity(),
            market.getOpenInterest(),
            market.getNotionalValue(),
            market.getRiskLimitCents(),
            market.getStrikeType(),
            market.getFloorStrike(),
            market.getCapStrike(),
            market.getResult(),
            market.getCanCloseEarly(),
            market.getExpirationValue(),
            market.getCategory(),
            market.getRulesPrimary(),
            market.getRulesSecondary(),
            market.getResponsePriceUnits(),
            market.getSettlementTimerSeconds(),
            market.getSettlementSource(),
            market.getCustomStrike(),
            market.getIsDeactivated(),
            Timestamp.valueOf(market.getCreatedAt()),
            Timestamp.valueOf(market.getUpdatedAt())
        );
        
        // Initialize order book for the market if it's open
        if (market.getStatus() == MarketStatus.OPEN) {
            initializeOrderBook(market.getTicker());
        }
        
        return market;
    }
    
    /**
     * Get a market by ticker
     */
    public Market getMarketByTicker(String ticker) {
        String sql = "SELECT * FROM markets WHERE ticker = ?";
        try {
            return jdbcTemplate.queryForObject(sql, marketRowMapper, ticker);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }
    
    /**
     * Get all markets with complex filtering
     */
    public List<Market> getMarkets(MarketFilter filter) {
        StringBuilder sql = new StringBuilder("SELECT * FROM markets WHERE 1=1");
        List<Object> params = new ArrayList<>();
        
        // Apply filters
        if (filter.getEventTicker() != null && !filter.getEventTicker().isEmpty()) {
            sql.append(" AND event_ticker = ?");
            params.add(filter.getEventTicker());
        }
        
        if (filter.getSeriesTicker() != null && !filter.getSeriesTicker().isEmpty()) {
            sql.append(" AND event_ticker IN (SELECT event_ticker FROM events WHERE series_ticker = ?)");
            params.add(filter.getSeriesTicker());
        }
        
        if (filter.getStatus() != null) {
            sql.append(" AND status = ?");
            params.add(filter.getStatus().name());
        }
        
        if (filter.getTickers() != null && !filter.getTickers().isEmpty()) {
            String placeholders = String.join(",", Collections.nCopies(filter.getTickers().size(), "?"));
            sql.append(" AND ticker IN (").append(placeholders).append(")");
            params.addAll(filter.getTickers());
        }
        
        if (filter.getMinCloseTime() != null) {
            sql.append(" AND close_time >= ?");
            params.add(Timestamp.valueOf(filter.getMinCloseTime()));
        }
        
        if (filter.getMaxCloseTime() != null) {
            sql.append(" AND close_time <= ?");
            params.add(Timestamp.valueOf(filter.getMaxCloseTime()));
        }
        
        if (filter.getMinOpenTime() != null) {
            sql.append(" AND open_time >= ?");
            params.add(Timestamp.valueOf(filter.getMinOpenTime()));
        }
        
        if (filter.getMaxOpenTime() != null) {
            sql.append(" AND open_time <= ?");
            params.add(Timestamp.valueOf(filter.getMaxOpenTime()));
        }
        
        if (filter.getCursor() != null && !filter.getCursor().isEmpty()) {
            sql.append(" AND ticker > ?");
            params.add(filter.getCursor());
        }
        
        sql.append(" ORDER BY ticker LIMIT ?");
        params.add(filter.getLimit());
        
        return jdbcTemplate.query(sql.toString(), marketRowMapper, params.toArray());
    }
    
    /**
     * Get markets by event
     */
    public List<Market> getMarketsByEvent(String eventTicker) {
        String sql = "SELECT * FROM markets WHERE event_ticker = ? ORDER BY ticker";
        return jdbcTemplate.query(sql, marketRowMapper, eventTicker);
    }
    
    /**
     * Update an existing market
     */
    @Transactional
    public Market updateMarket(String ticker, Market updates) {
        Market existing = getMarketByTicker(ticker);
        if (existing == null) {
            throw new IllegalArgumentException("Market not found: " + ticker);
        }
        
        updates.setUpdatedAt(LocalDateTime.now());
        
        // Build dynamic update query
        List<String> setClauses = new ArrayList<>();
        List<Object> params = new ArrayList<>();
        
        if (updates.getTitle() != null) {
            setClauses.add("title = ?");
            params.add(updates.getTitle());
        }
        
        if (updates.getStatus() != null) {
            setClauses.add("status = ?");
            params.add(updates.getStatus().name());
            
            // Handle status transitions
            handleStatusTransition(existing.getStatus(), updates.getStatus(), ticker);
        }
        
        if (updates.getCloseTime() != null) {
            setClauses.add("close_time = ?");
            params.add(Timestamp.valueOf(updates.getCloseTime()));
        }
        
        if (updates.getResult() != null) {
            setClauses.add("result = ?");
            params.add(updates.getResult());
        }
        
        if (updates.getExpirationTime() != null) {
            setClauses.add("expiration_time = ?");
            params.add(Timestamp.valueOf(updates.getExpirationTime()));
        }
        
        if (updates.getExpirationValue() != null) {
            setClauses.add("expiration_value = ?");
            params.add(updates.getExpirationValue());
        }
        
        // Always update the updated_at timestamp
        setClauses.add("updated_at = ?");
        params.add(Timestamp.valueOf(updates.getUpdatedAt()));
        
        // Add the ticker parameter at the end
        params.add(ticker);
        
        String sql = "UPDATE markets SET " + String.join(", ", setClauses) + " WHERE ticker = ?";
        jdbcTemplate.update(sql, params.toArray());
        
        return getMarketByTicker(ticker);
    }
    
    /**
     * Update market prices (called by order matching)
     */
    @Transactional
    public void updateMarketPrices(String ticker, BigDecimal yesBid, BigDecimal yesAsk, 
                                  BigDecimal noBid, BigDecimal noAsk, BigDecimal lastPrice) {
        Market market = getMarketByTicker(ticker);
        if (market == null) {
            return;
        }
        
        String sql = """
            UPDATE markets SET 
                previous_yes_bid = yes_bid,
                previous_yes_ask = yes_ask,
                previous_price = last_price,
                yes_bid = ?,
                yes_ask = ?,
                no_bid = ?,
                no_ask = ?,
                last_price = ?,
                updated_at = ?
            WHERE ticker = ?
        """;
        
        jdbcTemplate.update(sql,
            yesBid, yesAsk, noBid, noAsk, lastPrice,
            Timestamp.valueOf(LocalDateTime.now()),
            ticker
        );
    }
    
    /**
     * Update market volume (called after trades)
     */
    @Transactional
    public void updateMarketVolume(String ticker, long volumeIncrease) {
        String sql = """
            UPDATE markets SET 
                volume = volume + ?,
                volume_24h = volume_24h + ?,
                updated_at = ?
            WHERE ticker = ?
        """;
        
        jdbcTemplate.update(sql,
            volumeIncrease,
            volumeIncrease, // Simplified - in production would track 24h separately
            Timestamp.valueOf(LocalDateTime.now()),
            ticker
        );
    }
    
    /**
     * Delete a market
     */
    @Transactional
    public void deleteMarket(String ticker) {
        // Check if market has any orders or trades
        String checkOrdersSql = "SELECT COUNT(*) FROM orders WHERE symbol = ?";
        Integer orderCount = jdbcTemplate.queryForObject(checkOrdersSql, Integer.class, ticker);
        
        if (orderCount != null && orderCount > 0) {
            throw new IllegalStateException("Cannot delete market with existing orders. Found " + orderCount + " orders.");
        }
        
        String sql = "DELETE FROM markets WHERE ticker = ?";
        int rowsAffected = jdbcTemplate.update(sql, ticker);
        
        if (rowsAffected == 0) {
            throw new IllegalArgumentException("Market not found: " + ticker);
        }
    }
    
    /**
     * Check if a market exists
     */
    public boolean existsMarket(String ticker) {
        String sql = "SELECT COUNT(*) FROM markets WHERE ticker = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, ticker);
        return count != null && count > 0;
    }
    
    /**
     * Handle market status transitions
     */
    private void handleStatusTransition(MarketStatus oldStatus, MarketStatus newStatus, String ticker) {
        // UNOPENED -> OPEN: Initialize order book
        if (oldStatus == MarketStatus.OPEN && newStatus == MarketStatus.OPEN) {
            initializeOrderBook(ticker);
        }
        
        // Any -> CLOSED: Cancel all open orders
        if (newStatus == MarketStatus.CLOSED && oldStatus != MarketStatus.CLOSED) {
            cancelAllOpenOrders(ticker);
        }
        
        // Any -> SETTLED: Ensure market is closed first
        if (newStatus == MarketStatus.SETTLED && oldStatus != MarketStatus.CLOSED) {
            throw new IllegalStateException("Market must be closed before settling");
        }
    }
    
    /**
     * Initialize order book for a market
     */
    private void initializeOrderBook(String ticker) {
        // OrderBookService will handle the initialization
        // This is called when a market transitions to OPEN status
    }
    
    /**
     * Cancel all open orders for a market
     */
    private void cancelAllOpenOrders(String ticker) {
        String sql = """
            UPDATE orders SET 
                status = 'canceled',
                updated_time = ?
            WHERE symbol = ? 
            AND status IN ('open', 'partially_filled')
        """;
        
        jdbcTemplate.update(sql, System.currentTimeMillis(), ticker);
    }
    
    /**
     * Get market count
     */
    public int getMarketCount() {
        String sql = "SELECT COUNT(*) FROM markets";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class);
        return count != null ? count : 0;
    }
    
    /**
     * Get active market count
     */
    public int getActiveMarketCount() {
        String sql = "SELECT COUNT(*) FROM markets WHERE status = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, MarketStatus.OPEN.name());
        return count != null ? count : 0;
    }
    
    /**
     * Bulk update expired markets
     */
    @Transactional
    public int closeExpiredMarkets() {
        LocalDateTime now = LocalDateTime.now();
        
        // First get markets that need to be closed
        String selectSql = """
            SELECT ticker FROM markets 
            WHERE status = ? 
            AND close_time < ?
        """;
        
        List<String> marketsToClose = jdbcTemplate.queryForList(selectSql, String.class, 
            MarketStatus.OPEN.name(), Timestamp.valueOf(now));
        
        // Cancel orders for each market
        for (String ticker : marketsToClose) {
            cancelAllOpenOrders(ticker);
        }
        
        // Update market status
        String updateSql = """
            UPDATE markets SET 
                status = ?, 
                updated_at = ?
            WHERE status = ? 
            AND close_time < ?
        """;
        
        return jdbcTemplate.update(updateSql,
            MarketStatus.CLOSED.name(),
            Timestamp.valueOf(now),
            MarketStatus.OPEN.name(),
            Timestamp.valueOf(now)
        );
    }
    
    /**
     * Filter class for complex market queries
     */
    public static class MarketFilter {
        private String eventTicker;
        private String seriesTicker;
        private MarketStatus status;
        private List<String> tickers;
        private LocalDateTime minCloseTime;
        private LocalDateTime maxCloseTime;
        private LocalDateTime minOpenTime;
        private LocalDateTime maxOpenTime;
        private String cursor;
        private int limit = 100;
        
        // Getters and setters
        public String getEventTicker() {
            return eventTicker;
        }
        
        public void setEventTicker(String eventTicker) {
            this.eventTicker = eventTicker;
        }
        
        public String getSeriesTicker() {
            return seriesTicker;
        }
        
        public void setSeriesTicker(String seriesTicker) {
            this.seriesTicker = seriesTicker;
        }
        
        public MarketStatus getStatus() {
            return status;
        }
        
        public void setStatus(MarketStatus status) {
            this.status = status;
        }
        
        public List<String> getTickers() {
            return tickers;
        }
        
        public void setTickers(List<String> tickers) {
            this.tickers = tickers;
        }
        
        public LocalDateTime getMinCloseTime() {
            return minCloseTime;
        }
        
        public void setMinCloseTime(LocalDateTime minCloseTime) {
            this.minCloseTime = minCloseTime;
        }
        
        public LocalDateTime getMaxCloseTime() {
            return maxCloseTime;
        }
        
        public void setMaxCloseTime(LocalDateTime maxCloseTime) {
            this.maxCloseTime = maxCloseTime;
        }
        
        public LocalDateTime getMinOpenTime() {
            return minOpenTime;
        }
        
        public void setMinOpenTime(LocalDateTime minOpenTime) {
            this.minOpenTime = minOpenTime;
        }
        
        public LocalDateTime getMaxOpenTime() {
            return maxOpenTime;
        }
        
        public void setMaxOpenTime(LocalDateTime maxOpenTime) {
            this.maxOpenTime = maxOpenTime;
        }
        
        public String getCursor() {
            return cursor;
        }
        
        public void setCursor(String cursor) {
            this.cursor = cursor;
        }
        
        public int getLimit() {
            return limit;
        }
        
        public void setLimit(int limit) {
            this.limit = limit;
        }
    }
}