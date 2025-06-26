package com.kalshi.mock.catalog.service;

import com.fbg.api.market.KalshiSide;
import com.fbg.api.rest.Orderbook;
import com.fbg.api.rest.Trade;
import com.kalshi.mock.dto.OrderbookResponse;
import com.kalshi.mock.service.OrderBookService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;

/**
 * Service class for retrieving market data including orderbooks, trades, and candlesticks.
 * Integrates with existing OrderBookService and trades data.
 */
@Service
public class MarketDataService {
    
    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    @Autowired
    private OrderBookService orderBookService;
    
    // Trade data for candlestick generation
    private static class TradeData {
        public String tradeId;
        public String marketTicker;
        public int price;
        public int quantity;
        public long timestamp;
        
        public TradeData(String tradeId, String marketTicker, int price, int quantity, long timestamp) {
            this.tradeId = tradeId;
            this.marketTicker = marketTicker;
            this.price = price;
            this.quantity = quantity;
            this.timestamp = timestamp;
        }
    }
    
    // RowMapper for trade data
    private final RowMapper<TradeData> tradeDataRowMapper = new RowMapper<TradeData>() {
        @Override
        public TradeData mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new TradeData(
                rs.getString("id"),
                rs.getString("market_ticker"),
                rs.getInt("price"),
                rs.getInt("quantity"),
                rs.getLong("created_time")
            );
        }
    };
    
    /**
     * Get the current orderbook for a market
     */
    public Orderbook getMarketOrderbook(String marketTicker) {
        return orderBookService.getOrderbook(marketTicker);
    }
    
    /**
     * Get the current orderbook with specified depth
     */
    public Orderbook getMarketOrderbook(String marketTicker, int depth) {
        // The existing OrderBookService uses depth of 10 by default
        // For now, we'll return the same orderbook regardless of depth
        // In a production system, you'd modify OrderBookService to support custom depth
        return orderBookService.getOrderbook(marketTicker);
    }
    
    /**
     * Get the current orderbook in Kalshi format with separated YES and NO sides
     */
    public OrderbookResponse.OrderbookData getMarketOrderbookKalshiFormat(String marketTicker, int depth) {
        return orderBookService.getOrderbookKalshiFormat(marketTicker, depth);
    }
    
    /**
     * Get trades for a market
     */
    public List<Trade> getTrades(String marketTicker, Long minTs, Long maxTs, String cursor, int limit) {
        StringBuilder sql = new StringBuilder("""
            SELECT t.id, t.market_ticker, t.price, t.quantity, t.created_time,
                   ao.side as taker_side, ao.user_id as taker_user_id,
                   po.side as maker_side, po.user_id as maker_user_id
            FROM trades t
            JOIN orders ao ON t.aggressive_order_id = ao.id
            JOIN orders po ON t.passive_order_id = po.id
            WHERE t.market_ticker = ?
        """);
        
        List<Object> params = new ArrayList<>();
        params.add(marketTicker);
        
        if (minTs != null) {
            sql.append(" AND t.created_time >= ?");
            params.add(minTs);
        }
        
        if (maxTs != null) {
            sql.append(" AND t.created_time <= ?");
            params.add(maxTs);
        }
        
        if (cursor != null && !cursor.isEmpty()) {
            // Cursor is the trade ID
            sql.append(" AND t.id > ?");
            params.add(cursor);
        }
        
        sql.append(" ORDER BY t.created_time DESC, t.id DESC LIMIT ?");
        params.add(limit);
        
        return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> {
            String takerSide = rs.getString("taker_side");
            // In Kalshi, trades are reported from the taker's perspective
            // Convert BUY/SELL to yes/no Side enum
            KalshiSide side = "BUY".equals(takerSide) ? KalshiSide.yes : KalshiSide.no;
            
            return new Trade(
                rs.getString("id"),              // trade_id
                rs.getString("market_ticker"),   // ticker
                rs.getString("market_ticker"),   // market_ticker
                rs.getInt("price"),             // price
                rs.getInt("quantity"),          // count
                side,                           // side
                rs.getLong("created_time"),     // created_time
                null,                           // yes_price
                null                            // no_price
            );
        }, params.toArray());
    }
    
    /**
     * Get candlestick data for a market
     */
    public List<Candlestick> getCandlesticks(String marketTicker, String period, 
                                           Long minTs, Long maxTs, int limit) {
        // Validate period
        int periodSeconds = parsePeriod(period);
        
        // Get trades within the time range
        StringBuilder sql = new StringBuilder("""
            SELECT id, market_ticker, price, quantity, created_time
            FROM trades
            WHERE market_ticker = ?
        """);
        
        List<Object> params = new ArrayList<>();
        params.add(marketTicker);
        
        if (minTs != null) {
            sql.append(" AND created_time >= ?");
            params.add(minTs);
        }
        
        if (maxTs != null) {
            sql.append(" AND created_time <= ?");
            params.add(maxTs);
        }
        
        sql.append(" ORDER BY created_time ASC");
        
        List<TradeData> trades = jdbcTemplate.query(sql.toString(), tradeDataRowMapper, params.toArray());
        
        // Generate candlesticks from trades
        return generateCandlesticks(trades, periodSeconds, limit);
    }
    
    /**
     * Parse period string to seconds
     */
    private int parsePeriod(String period) {
        // Period format: "1m", "5m", "1h", "1d"
        if (period == null || period.length() < 2) {
            throw new IllegalArgumentException("Invalid period format");
        }
        
        String unit = period.substring(period.length() - 1);
        int value;
        
        try {
            value = Integer.parseInt(period.substring(0, period.length() - 1));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid period format");
        }
        
        switch (unit) {
            case "m":
                return value * 60;
            case "h":
                return value * 3600;
            case "d":
                return value * 86400;
            default:
                throw new IllegalArgumentException("Invalid period unit. Use 'm' (minutes), 'h' (hours), or 'd' (days)");
        }
    }
    
    /**
     * Generate candlesticks from trade data
     */
    private List<Candlestick> generateCandlesticks(List<TradeData> trades, int periodSeconds, int limit) {
        if (trades.isEmpty()) {
            return new ArrayList<>();
        }
        
        Map<Long, Candlestick> candlestickMap = new LinkedHashMap<>();
        long periodMillis = periodSeconds * 1000L;
        
        for (TradeData trade : trades) {
            // Round down to period start
            long periodStart = (trade.timestamp / periodMillis) * periodMillis;
            
            Candlestick candle = candlestickMap.computeIfAbsent(periodStart, k -> {
                Candlestick c = new Candlestick();
                c.periodStart = periodStart;
                c.open = trade.price;
                c.high = trade.price;
                c.low = trade.price;
                c.close = trade.price;
                c.volume = 0;
                c.count = 0;
                return c;
            });
            
            // Update candlestick
            candle.high = Math.max(candle.high, trade.price);
            candle.low = Math.min(candle.low, trade.price);
            candle.close = trade.price; // Last trade price in period
            candle.volume += trade.quantity;
            candle.count++;
        }
        
        // Convert to list and limit results
        List<Candlestick> candlesticks = new ArrayList<>(candlestickMap.values());
        
        // Sort by period start descending (most recent first)
        candlesticks.sort((a, b) -> Long.compare(b.periodStart, a.periodStart));
        
        // Limit results
        if (candlesticks.size() > limit) {
            candlesticks = candlesticks.subList(0, limit);
        }
        
        return candlesticks;
    }
    
    /**
     * Get market statistics
     */
    public MarketStats getMarketStats(String marketTicker, String period) {
        MarketStats stats = new MarketStats();
        stats.marketTicker = marketTicker;
        
        // Get current market data
        String marketSql = """
            SELECT last_price, previous_price, volume, volume_24h, open_interest
            FROM markets
            WHERE ticker = ?
        """;
        
        jdbcTemplate.queryForObject(marketSql, (rs, rowNum) -> {
            stats.lastPrice = rs.getInt("last_price");
            stats.previousPrice = rs.getInt("previous_price");
            stats.volume = rs.getLong("volume");
            stats.volume24h = rs.getLong("volume_24h");
            stats.openInterest = rs.getLong("open_interest");
            return null;
        }, marketTicker);
        
        // Calculate price change
        if (stats.previousPrice != null && stats.previousPrice > 0) {
            stats.priceChange = stats.lastPrice - stats.previousPrice;
            stats.priceChangePercent = (double) stats.priceChange / stats.previousPrice * 100;
        }
        
        // Get period statistics
        long periodMillis = parsePeriod(period) * 1000L;
        long startTime = System.currentTimeMillis() - periodMillis;
        
        String tradeSql = """
            SELECT MIN(price) as low, MAX(price) as high, 
                   SUM(quantity) as volume, COUNT(*) as trade_count
            FROM trades
            WHERE market_ticker = ? AND created_time >= ?
        """;
        
        jdbcTemplate.queryForObject(tradeSql, (rs, rowNum) -> {
            stats.periodLow = rs.getInt("low");
            stats.periodHigh = rs.getInt("high");
            stats.periodVolume = rs.getLong("volume");
            stats.periodTradeCount = rs.getInt("trade_count");
            return null;
        }, marketTicker, startTime);
        
        return stats;
    }
    
    /**
     * Get trade history summary
     */
    public TradeSummary getTradeSummary(String marketTicker, int days) {
        TradeSummary summary = new TradeSummary();
        summary.marketTicker = marketTicker;
        summary.days = days;
        
        long startTime = System.currentTimeMillis() - (days * 86400000L);
        
        String sql = """
            SELECT 
                COUNT(*) as total_trades,
                SUM(quantity) as total_volume,
                AVG(price) as avg_price,
                MIN(price) as min_price,
                MAX(price) as max_price,
                AVG(quantity) as avg_trade_size
            FROM trades
            WHERE market_ticker = ? AND created_time >= ?
        """;
        
        jdbcTemplate.queryForObject(sql, (rs, rowNum) -> {
            summary.totalTrades = rs.getInt("total_trades");
            summary.totalVolume = rs.getLong("total_volume");
            summary.avgPrice = rs.getDouble("avg_price");
            summary.minPrice = rs.getInt("min_price");
            summary.maxPrice = rs.getInt("max_price");
            summary.avgTradeSize = rs.getDouble("avg_trade_size");
            return null;
        }, marketTicker, startTime);
        
        return summary;
    }
    
    // Data classes for responses
    
    public static class Candlestick {
        public long periodStart;
        public int open;
        public int high;
        public int low;
        public int close;
        public long volume;
        public int count;
        
        // Getters
        public long getPeriodStart() { return periodStart; }
        public int getOpen() { return open; }
        public int getHigh() { return high; }
        public int getLow() { return low; }
        public int getClose() { return close; }
        public long getVolume() { return volume; }
        public int getCount() { return count; }
    }
    
    public static class MarketStats {
        public String marketTicker;
        public Integer lastPrice;
        public Integer previousPrice;
        public Integer priceChange;
        public Double priceChangePercent;
        public Long volume;
        public Long volume24h;
        public Long openInterest;
        public Integer periodLow;
        public Integer periodHigh;
        public Long periodVolume;
        public Integer periodTradeCount;
        
        // Getters
        public String getMarketTicker() { return marketTicker; }
        public Integer getLastPrice() { return lastPrice; }
        public Integer getPreviousPrice() { return previousPrice; }
        public Integer getPriceChange() { return priceChange; }
        public Double getPriceChangePercent() { return priceChangePercent; }
        public Long getVolume() { return volume; }
        public Long getVolume24h() { return volume24h; }
        public Long getOpenInterest() { return openInterest; }
        public Integer getPeriodLow() { return periodLow; }
        public Integer getPeriodHigh() { return periodHigh; }
        public Long getPeriodVolume() { return periodVolume; }
        public Integer getPeriodTradeCount() { return periodTradeCount; }
    }
    
    public static class TradeSummary {
        public String marketTicker;
        public int days;
        public int totalTrades;
        public long totalVolume;
        public double avgPrice;
        public int minPrice;
        public int maxPrice;
        public double avgTradeSize;
        
        // Getters
        public String getMarketTicker() { return marketTicker; }
        public int getDays() { return days; }
        public int getTotalTrades() { return totalTrades; }
        public long getTotalVolume() { return totalVolume; }
        public double getAvgPrice() { return avgPrice; }
        public int getMinPrice() { return minPrice; }
        public int getMaxPrice() { return maxPrice; }
        public double getAvgTradeSize() { return avgTradeSize; }
    }
}