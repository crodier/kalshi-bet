package com.kalshi.mock.service;

import com.fbg.api.market.KalshiSide;
import com.fbg.api.rest.Fill;
import com.fbg.api.rest.Order;
import com.fbg.api.rest.Position;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PersistenceService {
    
    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    // Order RowMapper
    private final RowMapper<Order> orderRowMapper = new RowMapper<Order>() {
        @Override
        public Order mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new Order(
                rs.getString("order_id"),
                rs.getString("client_order_id"),
                rs.getString("user_id"),
                KalshiSide.valueOf(rs.getString("side")),
                rs.getString("market_ticker"),  // symbol field maps to market_ticker column
                rs.getString("order_type"),
                rs.getInt("quantity"),
                rs.getInt("filled_quantity"),
                rs.getInt("remaining_quantity"),
                rs.getObject("price") != null ? rs.getInt("price") : null,
                rs.getObject("avg_fill_price") != null ? rs.getInt("avg_fill_price") : null,
                rs.getString("status"),
                rs.getString("time_in_force"),
                rs.getLong("created_time"),
                rs.getLong("updated_time"),
                rs.getObject("expiration_time") != null ? rs.getLong("expiration_time") : null
            );
        }
    };
    
    // Fill RowMapper
    private final RowMapper<Fill> fillRowMapper = new RowMapper<Fill>() {
        @Override
        public Fill mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new Fill(
                rs.getString("fill_id"),
                rs.getString("order_id"),
                rs.getString("market_id"),
                rs.getString("market_ticker"),
                KalshiSide.valueOf(rs.getString("side")),
                rs.getInt("price"),
                rs.getInt("quantity"),  // count field maps to quantity column
                rs.getBoolean("is_taker"),
                rs.getLong("filled_time"),  // created_time field maps to filled_time column
                rs.getString("trade_id")
            );
        }
    };
    
    // Position RowMapper
    private final RowMapper<Position> positionRowMapper = new RowMapper<Position>() {
        @Override
        public Position mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new Position(
                rs.getString("market_id"),
                rs.getString("market_ticker"),
                rs.getInt("quantity"),
                rs.getInt("avg_price"),
                KalshiSide.valueOf(rs.getString("side")),
                rs.getInt("realized_pnl"),
                rs.getInt("total_cost")
            );
        }
    };
    
    // Order operations
    @Transactional
    public void saveOrder(Order order, String action) {
        // First check if order exists
        String checkSql = "SELECT COUNT(*) FROM orders WHERE order_id = ?";
        int count = jdbcTemplate.queryForObject(checkSql, Integer.class, order.getId());
        
        if (count > 0) {
            // Update existing order
            String updateSql = """
                UPDATE orders SET
                    client_order_id = ?, user_id = ?, side = ?, action = ?,
                    market_ticker = ?, order_type = ?, quantity = ?,
                    filled_quantity = ?, remaining_quantity = ?, price = ?,
                    avg_fill_price = ?, status = ?, time_in_force = ?,
                    created_time = ?, updated_time = ?, expiration_time = ?
                WHERE order_id = ?
            """;
            
            jdbcTemplate.update(updateSql,
                order.getClient_order_id(),
                order.getUser_id(),
                order.getSide().name(),
                action,
                order.getSymbol(),  // market_ticker uses symbol field
                order.getOrder_type(),
                order.getQuantity(),
                order.getFilled_quantity(),
                order.getRemaining_quantity(),
                order.getPrice(),
                order.getAvg_fill_price(),
                order.getStatus(),
                order.getTime_in_force(),
                order.getCreated_time(),
                order.getUpdated_time(),
                order.getExpiration_time(),
                order.getId()
            );
        } else {
            // Insert new order
            String insertSql = """
                INSERT INTO orders (
                    order_id, client_order_id, user_id, side, action,
                    market_ticker, order_type, quantity, filled_quantity,
                    remaining_quantity, price, avg_fill_price, status,
                    time_in_force, created_time, updated_time, expiration_time
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
            
            jdbcTemplate.update(insertSql,
                order.getId(),
                order.getClient_order_id(),
                order.getUser_id(),
                order.getSide().name(),
                action,
                order.getSymbol(),  // market_ticker uses symbol field
                order.getOrder_type(),
                order.getQuantity(),
                order.getFilled_quantity(),
                order.getRemaining_quantity(),
                order.getPrice(),
                order.getAvg_fill_price(),
                order.getStatus(),
                order.getTime_in_force(),
                order.getCreated_time(),
                order.getUpdated_time(),
                order.getExpiration_time()
            );
        }
    }
    
    public Order getOrder(String orderId) {
        String sql = "SELECT * FROM orders WHERE order_id = ?";
        List<Order> orders = jdbcTemplate.query(sql, orderRowMapper, orderId);
        return orders.isEmpty() ? null : orders.get(0);
    }
    
    public List<Order> getUserOrders(String userId) {
        String sql = "SELECT * FROM orders WHERE user_id = ? ORDER BY created_time DESC";
        return jdbcTemplate.query(sql, orderRowMapper, userId);
    }
    
    public List<Order> getUserOrdersByStatus(String userId, String status) {
        String sql = "SELECT * FROM orders WHERE user_id = ? AND status = ? ORDER BY created_time DESC";
        return jdbcTemplate.query(sql, orderRowMapper, userId, status);
    }
    
    public List<Map<String, Object>> getOpenOrdersForMarket(String marketTicker) {
        String sql = "SELECT *, action FROM orders WHERE market_ticker = ? AND status = 'open' ORDER BY created_time ASC";
        return jdbcTemplate.queryForList(sql, marketTicker);
    }
    
    @Transactional
    public void updateOrderStatus(String orderId, String status, int filledQuantity, int remainingQuantity, Integer avgFillPrice) {
        String sql = """
            UPDATE orders SET 
                status = ?, 
                filled_quantity = ?, 
                remaining_quantity = ?,
                avg_fill_price = ?,
                updated_time = ?
            WHERE order_id = ?
        """;
        
        jdbcTemplate.update(sql, status, filledQuantity, remainingQuantity, avgFillPrice, 
                           System.currentTimeMillis(), orderId);
    }
    
    // Fill operations
    @Transactional
    public void saveFill(Fill fill, String userId) {
        String sql = """
            INSERT INTO fills (
                fill_id, order_id, user_id, market_id, market_ticker,
                side, price, quantity, is_taker, filled_time, trade_id
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;
        
        jdbcTemplate.update(sql,
            fill.getId(),
            fill.getOrder_id(),
            userId,
            fill.getMarket_id(),
            fill.getMarket_ticker(),
            fill.getSide().name(),
            fill.getPrice(),
            fill.getCount(),  // quantity uses count field
            fill.is_taker(),
            fill.getCreated_time(),  // filled_time uses created_time field
            fill.getTrade_id()
        );
    }
    
    public List<Fill> getUserFills(String userId) {
        String sql = "SELECT * FROM fills WHERE user_id = ? ORDER BY filled_time DESC";
        return jdbcTemplate.query(sql, fillRowMapper, userId);
    }
    
    public List<Fill> getUserFillsByMarket(String userId, String marketTicker) {
        String sql = "SELECT * FROM fills WHERE user_id = ? AND market_ticker = ? ORDER BY filled_time DESC";
        return jdbcTemplate.query(sql, fillRowMapper, userId, marketTicker);
    }
    
    public List<Fill> getFillsByOrderId(String orderId) {
        String sql = "SELECT * FROM fills WHERE order_id = ? ORDER BY filled_time DESC";
        return jdbcTemplate.query(sql, fillRowMapper, orderId);
    }
    
    // Position operations
    @Transactional
    public void updatePosition(String userId, String marketId, String marketTicker, 
                              KalshiSide side, int quantityChange, int price) {
        // First try to get existing position
        String selectSql = """
            SELECT quantity, avg_price, total_cost 
            FROM positions 
            WHERE user_id = ? AND market_ticker = ? AND side = ?
        """;
        
        List<Map<String, Object>> results = jdbcTemplate.queryForList(selectSql, userId, marketTicker, side.name());
        
        if (results.isEmpty()) {
            // Create new position
            String insertSql = """
                INSERT INTO positions (
                    user_id, market_id, market_ticker, quantity, avg_price,
                    side, realized_pnl, total_cost, updated_time
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
            
            int totalCost = Math.abs(quantityChange * price);
            jdbcTemplate.update(insertSql,
                userId, marketId, marketTicker, quantityChange, price,
                side.name(), 0, totalCost, System.currentTimeMillis()
            );
        } else {
            // Update existing position
            Map<String, Object> currentPosition = results.get(0);
            int currentQuantity = (int) currentPosition.get("quantity");
            int currentAvgPrice = (int) currentPosition.get("avg_price");
            int currentTotalCost = (int) currentPosition.get("total_cost");
            
            int newQuantity = currentQuantity + quantityChange;
            int newTotalCost;
            int newAvgPrice;
            
            if (newQuantity == 0) {
                newAvgPrice = 0;
                newTotalCost = 0;
            } else if ((currentQuantity > 0 && quantityChange > 0) || (currentQuantity < 0 && quantityChange < 0)) {
                // Increasing position (same direction)
                // For long: buying more
                // For short: selling more (both negative)
                newTotalCost = currentTotalCost + Math.abs(quantityChange * price);
                newAvgPrice = Math.abs(newTotalCost) / Math.abs(newQuantity);
            } else if ((currentQuantity > 0 && quantityChange < 0 && Math.abs(quantityChange) <= currentQuantity) ||
                       (currentQuantity < 0 && quantityChange > 0 && quantityChange <= Math.abs(currentQuantity))) {
                // Reducing position (partial close)
                double reductionRatio = (double) Math.abs(newQuantity) / Math.abs(currentQuantity);
                newTotalCost = (int) (currentTotalCost * reductionRatio);
                newAvgPrice = currentAvgPrice; // Keep same avg price when reducing
            } else {
                // Flipping position (from long to short or vice versa)
                // The excess quantity becomes the new position
                newTotalCost = Math.abs(newQuantity * price);
                newAvgPrice = price; // New position at current price
            }
            
            String updateSql = """
                UPDATE positions SET 
                    quantity = ?, 
                    avg_price = ?, 
                    total_cost = ?,
                    updated_time = ?
                WHERE user_id = ? AND market_ticker = ? AND side = ?
            """;
            
            jdbcTemplate.update(updateSql,
                newQuantity, newAvgPrice, newTotalCost, System.currentTimeMillis(),
                userId, marketTicker, side.name()
            );
        }
    }
    
    public List<Position> getUserPositions(String userId) {
        String sql = """
            SELECT * FROM positions 
            WHERE user_id = ? AND quantity != 0 
            ORDER BY market_ticker, side
        """;
        return jdbcTemplate.query(sql, positionRowMapper, userId);
    }
    
    public Position getUserPosition(String userId, String marketTicker, KalshiSide side) {
        String sql = """
            SELECT * FROM positions 
            WHERE user_id = ? AND market_ticker = ? AND side = ?
        """;
        List<Position> positions = jdbcTemplate.query(sql, positionRowMapper, userId, marketTicker, side.name());
        return positions.isEmpty() ? null : positions.get(0);
    }
    
    // Trade operations
    @Transactional
    public void saveTrade(String tradeId, String marketTicker, String aggressiveOrderId, 
                         String passiveOrderId, int quantity, int price) {
        String sql = """
            INSERT INTO trades (
                trade_id, market_ticker, taker_order_id, maker_order_id,
                price, quantity, created_time
            ) VALUES (?, ?, ?, ?, ?, ?, ?)
        """;
        
        jdbcTemplate.update(sql,
            tradeId, marketTicker, aggressiveOrderId, passiveOrderId,
            price, quantity, System.currentTimeMillis()
        );
    }
}