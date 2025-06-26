package com.kalshi.mock.service;

import com.fbg.api.market.KalshiSide;
import com.fbg.api.rest.Fill;
import com.fbg.api.rest.Position;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@Service
public class PositionsService {
    
    @Autowired
    private PersistenceService persistenceService;
    
    // Lock for thread-safe position updates
    private final ReadWriteLock positionLock = new ReentrantReadWriteLock();
    
    /**
     * Update user positions based on a fill
     * @param fill the fill to process
     * @param userId the user ID
     * @param isBuy true if this fill is from a buy order, false if from a sell order
     */
    @Transactional
    public void updatePositionFromFill(Fill fill, String userId, boolean isBuy) {
        positionLock.writeLock().lock();
        try {
            // Determine quantity change based on buy/sell action
            // Buy increases position (+), Sell decreases position (-)
            int quantityChange = isBuy ? fill.getCount() : -fill.getCount();
            
            // Update position with signed quantity change
            persistenceService.updatePosition(
                userId,
                fill.getMarket_id(),
                fill.getMarket_ticker(),
                fill.getSide(),
                quantityChange,
                fill.getPrice()
            );
            
        } finally {
            positionLock.writeLock().unlock();
        }
    }
    
    /**
     * Update user positions based on a fill (legacy method for compatibility)
     */
    @Transactional
    public void updatePositionFromFill(Fill fill, String userId) {
        // Default to buy (positive quantity) for backward compatibility
        updatePositionFromFill(fill, userId, true);
    }
    
    /**
     * Update positions from multiple fills (batch update)
     * Note: This method assumes all fills are from buy orders for backward compatibility
     */
    @Transactional
    public void updatePositionsFromFills(List<Fill> fills, String userId) {
        positionLock.writeLock().lock();
        try {
            for (Fill fill : fills) {
                updatePositionFromFill(fill, userId, true); // Default to buy
            }
        } finally {
            positionLock.writeLock().unlock();
        }
    }
    
    /**
     * Get all positions for a user
     */
    public List<Position> getUserPositions(String userId) {
        positionLock.readLock().lock();
        try {
            return persistenceService.getUserPositions(userId);
        } finally {
            positionLock.readLock().unlock();
        }
    }
    
    /**
     * Get a specific position for a user
     */
    public Position getUserPosition(String userId, String marketTicker, KalshiSide side) {
        positionLock.readLock().lock();
        try {
            return persistenceService.getUserPosition(userId, marketTicker, side);
        } finally {
            positionLock.readLock().unlock();
        }
    }
    
    /**
     * Calculate the total portfolio value for a user
     * @param userId the user ID
     * @param currentPrices map of market ticker to current price
     * @return total portfolio value in cents
     */
    public int calculatePortfolioValue(String userId, java.util.Map<String, Integer> currentPrices) {
        positionLock.readLock().lock();
        try {
            List<Position> positions = getUserPositions(userId);
            int totalValue = 0;
            
            for (Position position : positions) {
                Integer currentPrice = currentPrices.get(position.getMarket_ticker());
                if (currentPrice != null) {
                    totalValue += position.getQuantity() * currentPrice;
                }
            }
            
            return totalValue;
        } finally {
            positionLock.readLock().unlock();
        }
    }
    
    /**
     * Calculate unrealized P&L for a position
     */
    public int calculateUnrealizedPnL(Position position, int currentPrice) {
        if (position.getQuantity() == 0) {
            return 0;
        }
        
        int currentValue = position.getQuantity() * currentPrice;
        int costBasis = position.getTotal_cost();
        
        return currentValue - costBasis;
    }
    
    /**
     * Close a position (set quantity to 0 and calculate realized P&L)
     */
    @Transactional
    public void closePosition(String userId, String marketTicker, KalshiSide side, int closingPrice) {
        positionLock.writeLock().lock();
        try {
            Position currentPosition = persistenceService.getUserPosition(userId, marketTicker, side);
            if (currentPosition != null && currentPosition.getQuantity() != 0) {
                // Calculate realized P&L
                int realizedPnL = calculateUnrealizedPnL(currentPosition, closingPrice);
                
                // Update position to closed state
                persistenceService.updatePosition(
                    userId,
                    currentPosition.getMarket_id(),
                    marketTicker,
                    side,
                    -currentPosition.getQuantity(), // Close entire position
                    closingPrice
                );
            }
        } finally {
            positionLock.writeLock().unlock();
        }
    }
}