package com.kalshi.mock.catalog.service;

import com.kalshi.mock.catalog.model.Event;
import com.kalshi.mock.catalog.model.Event.EventStatus;
import com.kalshi.mock.catalog.model.Market;
import org.springframework.beans.factory.annotation.Autowired;
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

/**
 * Service class for managing Event entities in the catalog system.
 * Provides full CRUD operations with filtering and optional nested market loading.
 */
@Service
public class EventService {
    
    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    @Autowired
    private MarketService marketService;
    
    // Event RowMapper
    private final RowMapper<Event> eventRowMapper = new RowMapper<Event>() {
        @Override
        public Event mapRow(ResultSet rs, int rowNum) throws SQLException {
            Event event = new Event();
            event.setEventTicker(rs.getString("event_ticker"));
            event.setSeriesTicker(rs.getString("series_ticker"));
            event.setTitle(rs.getString("title"));
            event.setCategory(rs.getString("category"));
            event.setStatus(EventStatus.valueOf(rs.getString("status")));
            event.setMutuallyExclusive(rs.getBoolean("mutually_exclusive"));
            event.setSubTitle(rs.getString("sub_title"));
            event.setYesSubTitle(rs.getString("yes_sub_title"));
            event.setNoSubTitle(rs.getString("no_sub_title"));
            event.setResponsePriceUnits(rs.getString("response_price_units"));
            
            Timestamp expectedExpTime = rs.getTimestamp("expected_expiration_time");
            if (expectedExpTime != null) {
                event.setExpectedExpirationTime(expectedExpTime.toLocalDateTime());
            }
            
            Timestamp createdAt = rs.getTimestamp("created_at");
            if (createdAt != null) {
                event.setCreatedAt(createdAt.toLocalDateTime());
            }
            
            Timestamp updatedAt = rs.getTimestamp("updated_at");
            if (updatedAt != null) {
                event.setUpdatedAt(updatedAt.toLocalDateTime());
            }
            
            // Markets are loaded separately if requested
            event.setMarkets(new ArrayList<>());
            
            return event;
        }
    };
    
    /**
     * Create a new event
     */
    @Transactional
    public Event createEvent(Event event) {
        // Validate event doesn't already exist
        if (existsEvent(event.getEventTicker())) {
            throw new IllegalArgumentException("Event with ticker " + event.getEventTicker() + " already exists");
        }
        
        // Validate series exists
        String checkSeriesSql = "SELECT COUNT(*) FROM series WHERE ticker = ?";
        Integer seriesCount = jdbcTemplate.queryForObject(checkSeriesSql, Integer.class, event.getSeriesTicker());
        if (seriesCount == null || seriesCount == 0) {
            throw new IllegalArgumentException("Series not found: " + event.getSeriesTicker());
        }
        
        LocalDateTime now = LocalDateTime.now();
        event.setCreatedAt(now);
        event.setUpdatedAt(now);
        
        String sql = """
            INSERT INTO events (
                event_ticker, series_ticker, title, category, status,
                mutually_exclusive, sub_title, yes_sub_title, no_sub_title,
                expected_expiration_time, response_price_units,
                created_at, updated_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;
        
        jdbcTemplate.update(sql,
            event.getEventTicker(),
            event.getSeriesTicker(),
            event.getTitle(),
            event.getCategory(),
            event.getStatus().name(),
            event.getMutuallyExclusive(),
            event.getSubTitle(),
            event.getYesSubTitle(),
            event.getNoSubTitle(),
            event.getExpectedExpirationTime() != null ? 
                Timestamp.valueOf(event.getExpectedExpirationTime()) : null,
            event.getResponsePriceUnits(),
            Timestamp.valueOf(event.getCreatedAt()),
            Timestamp.valueOf(event.getUpdatedAt())
        );
        
        return event;
    }
    
    /**
     * Get an event by ticker
     */
    public Event getEventByTicker(String eventTicker, boolean includeMarkets) {
        String sql = "SELECT * FROM events WHERE event_ticker = ?";
        try {
            Event event = jdbcTemplate.queryForObject(sql, eventRowMapper, eventTicker);
            
            if (includeMarkets && event != null) {
                List<Market> markets = marketService.getMarketsByEvent(eventTicker);
                event.setMarkets(markets);
            }
            
            return event;
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }
    
    /**
     * Get all events with optional filtering
     */
    public List<Event> getAllEvents(String seriesTicker, EventStatus status, 
                                   String cursor, int limit, boolean includeMarkets) {
        StringBuilder sql = new StringBuilder("SELECT * FROM events WHERE 1=1");
        List<Object> params = new ArrayList<>();
        
        // Add filters
        if (seriesTicker != null && !seriesTicker.isEmpty()) {
            sql.append(" AND series_ticker = ?");
            params.add(seriesTicker);
        }
        
        if (status != null) {
            sql.append(" AND status = ?");
            params.add(status.name());
        }
        
        if (cursor != null && !cursor.isEmpty()) {
            sql.append(" AND event_ticker > ?");
            params.add(cursor);
        }
        
        sql.append(" ORDER BY event_ticker LIMIT ?");
        params.add(limit);
        
        List<Event> events = jdbcTemplate.query(sql.toString(), eventRowMapper, params.toArray());
        
        // Load markets if requested
        if (includeMarkets) {
            for (Event event : events) {
                List<Market> markets = marketService.getMarketsByEvent(event.getEventTicker());
                event.setMarkets(markets);
            }
        }
        
        return events;
    }
    
    /**
     * Update an existing event
     */
    @Transactional
    public Event updateEvent(String eventTicker, Event updates) {
        Event existing = getEventByTicker(eventTicker, false);
        if (existing == null) {
            throw new IllegalArgumentException("Event not found: " + eventTicker);
        }
        
        updates.setUpdatedAt(LocalDateTime.now());
        
        String sql = """
            UPDATE events SET 
                series_ticker = COALESCE(?, series_ticker),
                title = COALESCE(?, title),
                category = COALESCE(?, category),
                status = COALESCE(?, status),
                mutually_exclusive = COALESCE(?, mutually_exclusive),
                sub_title = COALESCE(?, sub_title),
                yes_sub_title = COALESCE(?, yes_sub_title),
                no_sub_title = COALESCE(?, no_sub_title),
                expected_expiration_time = COALESCE(?, expected_expiration_time),
                response_price_units = COALESCE(?, response_price_units),
                updated_at = ?
            WHERE event_ticker = ?
        """;
        
        jdbcTemplate.update(sql,
            updates.getSeriesTicker(),
            updates.getTitle(),
            updates.getCategory(),
            updates.getStatus() != null ? updates.getStatus().name() : null,
            updates.getMutuallyExclusive(),
            updates.getSubTitle(),
            updates.getYesSubTitle(),
            updates.getNoSubTitle(),
            updates.getExpectedExpirationTime() != null ? 
                Timestamp.valueOf(updates.getExpectedExpirationTime()) : null,
            updates.getResponsePriceUnits(),
            Timestamp.valueOf(updates.getUpdatedAt()),
            eventTicker
        );
        
        return getEventByTicker(eventTicker, false);
    }
    
    /**
     * Delete an event
     */
    @Transactional
    public void deleteEvent(String eventTicker) {
        // Check if event has associated markets
        String checkSql = "SELECT COUNT(*) FROM markets WHERE event_ticker = ?";
        Integer marketCount = jdbcTemplate.queryForObject(checkSql, Integer.class, eventTicker);
        
        if (marketCount != null && marketCount > 0) {
            throw new IllegalStateException("Cannot delete event with associated markets. Found " + marketCount + " markets.");
        }
        
        String sql = "DELETE FROM events WHERE event_ticker = ?";
        int rowsAffected = jdbcTemplate.update(sql, eventTicker);
        
        if (rowsAffected == 0) {
            throw new IllegalArgumentException("Event not found: " + eventTicker);
        }
    }
    
    /**
     * Check if an event exists
     */
    public boolean existsEvent(String eventTicker) {
        String sql = "SELECT COUNT(*) FROM events WHERE event_ticker = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, eventTicker);
        return count != null && count > 0;
    }
    
    /**
     * Get events by category
     */
    public List<Event> getEventsByCategory(String category, String cursor, int limit, boolean includeMarkets) {
        String sql;
        List<Event> events;
        
        if (cursor != null && !cursor.isEmpty()) {
            sql = "SELECT * FROM events WHERE category = ? AND event_ticker > ? ORDER BY event_ticker LIMIT ?";
            events = jdbcTemplate.query(sql, eventRowMapper, category, cursor, limit);
        } else {
            sql = "SELECT * FROM events WHERE category = ? ORDER BY event_ticker LIMIT ?";
            events = jdbcTemplate.query(sql, eventRowMapper, category, limit);
        }
        
        // Load markets if requested
        if (includeMarkets) {
            for (Event event : events) {
                List<Market> markets = marketService.getMarketsByEvent(event.getEventTicker());
                event.setMarkets(markets);
            }
        }
        
        return events;
    }
    
    /**
     * Get events by series
     */
    public List<Event> getEventsBySeries(String seriesTicker) {
        String sql = "SELECT * FROM events WHERE series_ticker = ? ORDER BY event_ticker";
        return jdbcTemplate.query(sql, eventRowMapper, seriesTicker);
    }
    
    /**
     * Update event status
     */
    @Transactional
    public void updateEventStatus(String eventTicker, EventStatus newStatus) {
        String sql = "UPDATE events SET status = ?, updated_at = ? WHERE event_ticker = ?";
        int rowsAffected = jdbcTemplate.update(sql, 
            newStatus.name(), 
            Timestamp.valueOf(LocalDateTime.now()),
            eventTicker
        );
        
        if (rowsAffected == 0) {
            throw new IllegalArgumentException("Event not found: " + eventTicker);
        }
    }
    
    /**
     * Get the count of all events
     */
    public int getEventCount() {
        String sql = "SELECT COUNT(*) FROM events";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class);
        return count != null ? count : 0;
    }
    
    /**
     * Get the count of events by series
     */
    public int getEventCountBySeries(String seriesTicker) {
        String sql = "SELECT COUNT(*) FROM events WHERE series_ticker = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, seriesTicker);
        return count != null ? count : 0;
    }
    
    /**
     * Get the count of events by status
     */
    public int getEventCountByStatus(EventStatus status) {
        String sql = "SELECT COUNT(*) FROM events WHERE status = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, status.name());
        return count != null ? count : 0;
    }
    
    /**
     * Bulk update event statuses (useful for closing expired events)
     */
    @Transactional
    public int bulkUpdateExpiredEvents() {
        String sql = """
            UPDATE events SET 
                status = ?, 
                updated_at = ?
            WHERE status = ? 
            AND expected_expiration_time IS NOT NULL 
            AND expected_expiration_time < ?
        """;
        
        LocalDateTime now = LocalDateTime.now();
        return jdbcTemplate.update(sql,
            EventStatus.CLOSED.name(),
            Timestamp.valueOf(now),
            EventStatus.OPEN.name(),
            Timestamp.valueOf(now)
        );
    }
}