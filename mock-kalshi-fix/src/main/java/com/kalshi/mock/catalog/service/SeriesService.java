package com.kalshi.mock.catalog.service;

import com.kalshi.mock.catalog.model.Series;
import com.kalshi.mock.service.PersistenceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service class for managing Series entities in the catalog system.
 * Provides full CRUD operations with pagination support.
 */
@Service
public class SeriesService {
    
    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    // Series RowMapper
    private final RowMapper<Series> seriesRowMapper = new RowMapper<Series>() {
        @Override
        public Series mapRow(ResultSet rs, int rowNum) throws SQLException {
            Series series = new Series();
            series.setTicker(rs.getString("ticker"));
            series.setFrequency(rs.getString("frequency"));
            series.setTitle(rs.getString("title"));
            series.setCategory(rs.getString("category"));
            series.setContractUrl(rs.getString("contract_url"));
            series.setFeeType(rs.getString("fee_type"));
            series.setFeeMultiplier(rs.getDouble("fee_multiplier"));
            
            Timestamp createdAt = rs.getTimestamp("created_at");
            if (createdAt != null) {
                series.setCreatedAt(createdAt.toLocalDateTime());
            }
            
            Timestamp updatedAt = rs.getTimestamp("updated_at");
            if (updatedAt != null) {
                series.setUpdatedAt(updatedAt.toLocalDateTime());
            }
            
            // Load tags and settlement sources separately
            series.setTags(getSeriesTags(series.getTicker()));
            series.setSettlementSources(getSeriesSettlementSources(series.getTicker()));
            
            return series;
        }
    };
    
    /**
     * Create a new series
     */
    @Transactional
    public Series createSeries(Series series) {
        // Validate series doesn't already exist
        if (existsSeries(series.getTicker())) {
            throw new IllegalArgumentException("Series with ticker " + series.getTicker() + " already exists");
        }
        
        LocalDateTime now = LocalDateTime.now();
        series.setCreatedAt(now);
        series.setUpdatedAt(now);
        
        // Insert main series record
        String sql = """
            INSERT INTO series (
                ticker, frequency, title, category, contract_url,
                fee_type, fee_multiplier, created_at, updated_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;
        
        jdbcTemplate.update(sql,
            series.getTicker(),
            series.getFrequency(),
            series.getTitle(),
            series.getCategory(),
            series.getContractUrl(),
            series.getFeeType(),
            series.getFeeMultiplier(),
            Timestamp.valueOf(series.getCreatedAt()),
            Timestamp.valueOf(series.getUpdatedAt())
        );
        
        // Insert tags
        if (series.getTags() != null && !series.getTags().isEmpty()) {
            saveSeriesTags(series.getTicker(), series.getTags());
        }
        
        // Insert settlement sources
        if (series.getSettlementSources() != null && !series.getSettlementSources().isEmpty()) {
            saveSeriesSettlementSources(series.getTicker(), series.getSettlementSources());
        }
        
        return series;
    }
    
    /**
     * Get a series by ticker
     */
    public Series getSeriesByTicker(String ticker) {
        String sql = "SELECT * FROM series WHERE ticker = ?";
        try {
            return jdbcTemplate.queryForObject(sql, seriesRowMapper, ticker);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }
    
    /**
     * Get all series with pagination support
     */
    public List<Series> getAllSeries(String cursor, int limit) {
        String sql;
        List<Series> series;
        
        if (cursor != null && !cursor.isEmpty()) {
            // Decode cursor (assuming it's the last ticker from previous page)
            sql = "SELECT * FROM series WHERE ticker > ? ORDER BY ticker LIMIT ?";
            series = jdbcTemplate.query(sql, seriesRowMapper, cursor, limit);
        } else {
            sql = "SELECT * FROM series ORDER BY ticker LIMIT ?";
            series = jdbcTemplate.query(sql, seriesRowMapper, limit);
        }
        
        return series;
    }
    
    /**
     * Get series by category
     */
    public List<Series> getSeriesByCategory(String category, String cursor, int limit) {
        String sql;
        List<Series> series;
        
        if (cursor != null && !cursor.isEmpty()) {
            sql = "SELECT * FROM series WHERE category = ? AND ticker > ? ORDER BY ticker LIMIT ?";
            series = jdbcTemplate.query(sql, seriesRowMapper, category, cursor, limit);
        } else {
            sql = "SELECT * FROM series WHERE category = ? ORDER BY ticker LIMIT ?";
            series = jdbcTemplate.query(sql, seriesRowMapper, category, limit);
        }
        
        return series;
    }
    
    /**
     * Update an existing series
     */
    @Transactional
    public Series updateSeries(String ticker, Series updates) {
        Series existing = getSeriesByTicker(ticker);
        if (existing == null) {
            throw new IllegalArgumentException("Series not found: " + ticker);
        }
        
        updates.setUpdatedAt(LocalDateTime.now());
        
        String sql = """
            UPDATE series SET 
                frequency = COALESCE(?, frequency),
                title = COALESCE(?, title),
                category = COALESCE(?, category),
                contract_url = COALESCE(?, contract_url),
                fee_type = COALESCE(?, fee_type),
                fee_multiplier = COALESCE(?, fee_multiplier),
                updated_at = ?
            WHERE ticker = ?
        """;
        
        jdbcTemplate.update(sql,
            updates.getFrequency(),
            updates.getTitle(),
            updates.getCategory(),
            updates.getContractUrl(),
            updates.getFeeType(),
            updates.getFeeMultiplier(),
            Timestamp.valueOf(updates.getUpdatedAt()),
            ticker
        );
        
        // Update tags if provided
        if (updates.getTags() != null) {
            deleteSeriesTags(ticker);
            if (!updates.getTags().isEmpty()) {
                saveSeriesTags(ticker, updates.getTags());
            }
        }
        
        // Update settlement sources if provided
        if (updates.getSettlementSources() != null) {
            deleteSeriesSettlementSources(ticker);
            if (!updates.getSettlementSources().isEmpty()) {
                saveSeriesSettlementSources(ticker, updates.getSettlementSources());
            }
        }
        
        return getSeriesByTicker(ticker);
    }
    
    /**
     * Delete a series
     */
    @Transactional
    public void deleteSeries(String ticker) {
        // Check if series has associated events
        String checkSql = "SELECT COUNT(*) FROM events WHERE series_ticker = ?";
        Integer eventCount = jdbcTemplate.queryForObject(checkSql, Integer.class, ticker);
        
        if (eventCount != null && eventCount > 0) {
            throw new IllegalStateException("Cannot delete series with associated events. Found " + eventCount + " events.");
        }
        
        // Delete tags and settlement sources first
        deleteSeriesTags(ticker);
        deleteSeriesSettlementSources(ticker);
        
        // Delete series
        String sql = "DELETE FROM series WHERE ticker = ?";
        int rowsAffected = jdbcTemplate.update(sql, ticker);
        
        if (rowsAffected == 0) {
            throw new IllegalArgumentException("Series not found: " + ticker);
        }
    }
    
    /**
     * Check if a series exists
     */
    public boolean existsSeries(String ticker) {
        String sql = "SELECT COUNT(*) FROM series WHERE ticker = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, ticker);
        return count != null && count > 0;
    }
    
    /**
     * Get series by tags
     */
    public List<Series> getSeriesByTag(String tag, String cursor, int limit) {
        String sql;
        List<String> tickers;
        
        if (cursor != null && !cursor.isEmpty()) {
            sql = """
                SELECT DISTINCT series_ticker FROM series_tags 
                WHERE tag = ? AND series_ticker > ? 
                ORDER BY series_ticker LIMIT ?
            """;
            tickers = jdbcTemplate.queryForList(sql, String.class, tag, cursor, limit);
        } else {
            sql = """
                SELECT DISTINCT series_ticker FROM series_tags 
                WHERE tag = ? 
                ORDER BY series_ticker LIMIT ?
            """;
            tickers = jdbcTemplate.queryForList(sql, String.class, tag, limit);
        }
        
        // Fetch full series objects
        List<Series> series = new ArrayList<>();
        for (String ticker : tickers) {
            Series s = getSeriesByTicker(ticker);
            if (s != null) {
                series.add(s);
            }
        }
        
        return series;
    }
    
    // Helper methods for tags and settlement sources
    
    private List<String> getSeriesTags(String seriesTicker) {
        String sql = "SELECT tag FROM series_tags WHERE series_ticker = ? ORDER BY tag";
        return jdbcTemplate.queryForList(sql, String.class, seriesTicker);
    }
    
    private List<String> getSeriesSettlementSources(String seriesTicker) {
        String sql = "SELECT settlement_source FROM series_settlement_sources WHERE series_ticker = ? ORDER BY settlement_source";
        return jdbcTemplate.queryForList(sql, String.class, seriesTicker);
    }
    
    private void saveSeriesTags(String seriesTicker, List<String> tags) {
        String sql = "INSERT INTO series_tags (series_ticker, tag) VALUES (?, ?)";
        for (String tag : tags) {
            jdbcTemplate.update(sql, seriesTicker, tag);
        }
    }
    
    private void saveSeriesSettlementSources(String seriesTicker, List<String> sources) {
        String sql = "INSERT INTO series_settlement_sources (series_ticker, settlement_source) VALUES (?, ?)";
        for (String source : sources) {
            jdbcTemplate.update(sql, seriesTicker, source);
        }
    }
    
    private void deleteSeriesTags(String seriesTicker) {
        String sql = "DELETE FROM series_tags WHERE series_ticker = ?";
        jdbcTemplate.update(sql, seriesTicker);
    }
    
    private void deleteSeriesSettlementSources(String seriesTicker) {
        String sql = "DELETE FROM series_settlement_sources WHERE series_ticker = ?";
        jdbcTemplate.update(sql, seriesTicker);
    }
    
    /**
     * Get the count of all series (useful for pagination)
     */
    public int getSeriesCount() {
        String sql = "SELECT COUNT(*) FROM series";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class);
        return count != null ? count : 0;
    }
    
    /**
     * Get the count of series by category
     */
    public int getSeriesCountByCategory(String category) {
        String sql = "SELECT COUNT(*) FROM series WHERE category = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, category);
        return count != null ? count : 0;
    }
}