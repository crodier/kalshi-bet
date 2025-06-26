# Order Book Implementation Instructions

## Overview

This document provides comprehensive instructions for implementing a Kalshi-compatible order book system. It consolidates the analysis and design decisions made for building a mock exchange that replicates Kalshi's binary options market structure.

## 1. Market Structure

### Binary Options Fundamentals
- Every market has YES and NO contracts
- Fundamental relationship: **YES Price + NO Price = $1.00 (100¢)**
- Prices range from 1¢ to 99¢
- Winners receive exactly $1.00 per contract

### Position Types
Refer to `MARKET_DYNAMICS.md` for detailed profit/loss scenarios:
- **Buy YES**: Profits if event occurs
- **Sell YES**: Profits if event doesn't occur (takes opposite side)
- **Buy NO**: Profits if event doesn't occur  
- **Sell NO**: Profits if event occurs (takes opposite side)

## 2. Order Book Design

### Core Concept: NO/YES Conversion

To simplify the order book implementation, all NO orders are internally converted to YES equivalents:

```
Buy NO @ P → Sell YES @ (100 - P)
Sell NO @ P → Buy YES @ (100 - P)
```

This allows maintaining a single order book (YES) instead of separate YES and NO books.

### Order Representation

Orders use the `BookOrder` data structure (`Order.kt`):
- Tracks both original side/price and normalized (converted) values
- Maintains FIFO priority with sequence numbers
- Supports partial fills with `remainingQuantity`

### Concurrent Order Book (`ConcurrentOrderBookV2.kt`)

Key features:
- Thread-safe using `ConcurrentSkipListMap` and locks
- Price levels maintain order queues (FIFO)
- Automatic cross detection (both self-cross and external cross)
- Event listeners for order book changes

## 3. Order Processing

### Order Types (from `IncomingOrder.kt`)

1. **NewOrder**: Contains all order details (market, side, price, quantity, etc.)
2. **CancelOrder**: References order to cancel
3. **ModifyOrder**: Updates price/quantity (loses priority if price changes)

### Processing Flow

1. **New Order**:
   - Apply NO/YES conversion if needed
   - Check for crosses
   - Add to appropriate price level
   - Publish WebSocket update

2. **Cancel Order**:
   - Remove from price level
   - Clean up empty levels
   - Publish negative delta

3. **Modify Order**:
   - If only size changes: modify in place (keeps priority)
   - If price changes: cancel and re-add (loses priority)

## 4. Cross Detection

### Self-Cross
When bid ≥ ask on the same side (YES or NO):
```
YES bid: 65¢
YES ask: 64¢
→ CROSSED (bid > ask)
```

### External Cross
When YES bid + NO bid > 100¢:
```
YES bid: 65¢
NO bid: 40¢
Total: 105¢ > 100¢
→ ARBITRAGE OPPORTUNITY
```

## 5. WebSocket Publishing

### Channels (from `WEBSOCKET.md`)

1. **orderbook_snapshot**: Full order book state
2. **orderbook_update**: Incremental changes
3. **ticker**: Best bid/ask updates
4. **trade**: Executed trades
5. **fill**: User-specific fills

### Message Format

All messages include:
- `type`: Channel name
- `sequence`: Incrementing number for ordering
- `timestamp`: Unix timestamp
- `data`: Channel-specific payload

### Publishing Logic (`WebSocketPublisher.kt`)

The publisher converts internal YES-normalized representation back to original YES/NO format for external consumption.

## 6. REST API Integration

### Order Submission (`API_SPECIFICATION.md`)

REST endpoint accepts orders with:
- `market_ticker`: Market identifier
- `side`: "yes" or "no"
- `action`: "buy" or "sell"
- `type`: "limit" or "market"
- `quantity`: Number of contracts
- `price`: Price in cents (for limit orders)

### Data Flow

1. REST API receives order
2. Converts to `IncomingOrder.NewOrder`
3. Passes to `ConcurrentOrderBookV2`
4. Order book processes and publishes WebSocket updates
5. REST API returns order confirmation

## 7. Implementation Checklist

### Required Components

- [x] Order data structures (`BookOrder`, `IncomingOrder`)
- [x] Concurrent order book (`ConcurrentOrderBookV2`)
- [x] NO/YES conversion logic
- [x] Cross detection algorithms
- [x] WebSocket message formats
- [x] REST API specifications

### To Implement in Mock Exchange

1. **REST Server**:
   - Endpoints as specified in `API_SPECIFICATION.md`
   - Authentication/authorization
   - Rate limiting

2. **WebSocket Server**:
   - Connection management
   - Subscription handling
   - Message broadcasting using `WebSocketPublisher`

3. **Order Matching Engine**:
   - Execute trades when orders cross
   - Update order quantities
   - Generate trade/fill events

4. **Persistence Layer**:
   - Order history
   - Trade history
   - Market data aggregation

5. **FIX Gateway Integration**:
   - Convert REST orders to FIX messages
   - Handle FIX execution reports
   - Maintain order state synchronization

## 8. Testing Scenarios

### Basic Order Flow
1. Submit YES buy order at 65¢
2. Submit NO buy order at 30¢ (converts to YES sell at 70¢)
3. Verify order book shows correct levels
4. Verify WebSocket publishes updates

### Cross Detection
1. Submit YES buy at 65¢
2. Submit YES sell at 64¢
3. Verify self-cross detected
4. Submit NO buy at 40¢
5. Verify external cross detected (65 + 40 > 100)

### Order Modifications
1. Submit order
2. Modify quantity only - verify maintains priority
3. Modify price - verify loses priority (moved to end of queue)

## 9. Performance Considerations

- Use `ConcurrentSkipListMap` for O(log n) operations
- Minimize lock contention with fine-grained locking
- Batch WebSocket updates when possible
- Consider memory pooling for high-frequency updates

## 10. Edge Cases

1. **Price Boundaries**: Handle prices at 1¢ and 99¢
2. **Zero Quantity**: Remove orders when quantity reaches zero
3. **Market Orders**: Implement crossing logic for market orders
4. **Partial Fills**: Track filled vs remaining quantity
5. **Sequence Gaps**: Client should re-subscribe on detected gaps

## Summary

This order book implementation provides a complete foundation for a Kalshi-compatible exchange. The key innovation is the NO/YES conversion that simplifies internal logic while maintaining full compatibility with Kalshi's API format. The concurrent design ensures thread safety for high-frequency trading scenarios.