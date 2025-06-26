# Kalshi Mock Server - Current Implementation Plan

## Business Context

This project implements a mock server that replicates Kalshi's prediction market trading system. Kalshi is a regulated exchange for trading on the outcomes of future events through binary contracts (YES/NO positions).

### Key Business Concepts

1. **Binary Markets**: Each market represents a future event with a YES/NO outcome
   - YES positions profit if the event occurs
   - NO positions profit if the event doesn't occur
   - Prices range from 1¢ to 99¢, representing probability

2. **Order Types**:
   - **Limit Orders**: Specify exact price and quantity
   - **Market Orders**: Execute at best available price

3. **Position Tracking**:
   - Users can hold both long (buy) and short (sell) positions
   - Positions net out when the same user trades both sides
   - P&L calculated based on entry price vs current/settlement price

4. **Order Matching Engine**:
   - FIFO (First In, First Out) priority at each price level
   - NO orders are internally converted to YES orders for unified matching
   - Example: Buy NO at 40¢ = Sell YES at 60¢

## Architecture Overview

### Technology Stack
- **Backend**: Spring Boot 3.x with Java 17
- **Database**: SQLite (for development/testing)
- **WebSocket**: Spring WebSocket with STOMP protocol
- **API**: RESTful API matching Kalshi's v2 specification

### Core Components

1. **Order Book Service** (`OrderBookService.java`)
   - Manages order books for each market
   - Handles order creation, cancellation, and matching
   - Publishes events for WebSocket updates

2. **Matching Engine** (`MatchingEngine.java`)
   - Implements FIFO matching logic
   - Handles NO-to-YES order conversion
   - Creates trades and fills when orders match

3. **Persistence Service** (`PersistenceService.java`)
   - Manages database operations
   - Handles order, fill, trade, and position storage
   - Uses proper column mappings for SQL operations

4. **WebSocket Infrastructure**
   - Real-time order book updates
   - Trade and fill notifications
   - Subscription management for multiple markets

## Frontend Considerations

### WebSocket Client Implementation

The WebSocket client needs to:

1. **Connect to WebSocket endpoint**: `ws://localhost:8080/trade-api/v2/ws`

2. **Subscribe to market data**:
```javascript
{
  "type": "subscribe",
  "channels": ["orderbook_delta"],
  "markets": ["BTCZ-23DEC31-B50000"]
}
```

3. **Handle message types**:
   - `orderbook_snapshot`: Full order book state
   - `orderbook_delta`: Incremental updates
   - `trade`: Executed trades
   - `ticker`: Market statistics

### Order Book Display

The order book should display:
- **YES side only** (NO orders are converted internally)
- Price levels with aggregated quantities
- Best bid/ask prices highlighted
- Real-time updates via WebSocket

### Position Management UI

Display user positions showing:
- Market ticker
- Side (YES/NO)
- Quantity
- Average price
- Current P&L
- Note: Positions with zero quantity are hidden

## Backend Implementation Details

### Database Schema

Key tables:
- `orders`: All order details with status tracking
- `fills`: Execution records for matched orders
- `trades`: Market-wide trade history
- `positions`: User position tracking

### Fixed Issues

1. **Column Mapping Corrections**:
   - Fixed SQL column names in `PersistenceService.saveTrade()`:
     - `id` → `trade_id`
     - `aggressive_order_id` → `taker_order_id`
     - `passive_order_id` → `maker_order_id`

2. **Test Adjustments**:
   - Updated integration tests to handle position netting
   - Tests now verify business logic rather than fixed values
   - Accounts for same-user trading scenarios

### API Endpoints

#### Order Management
- `POST /trade-api/v2/portfolio/orders` - Create new order
- `GET /trade-api/v2/portfolio/orders` - List user orders
- `DELETE /trade-api/v2/portfolio/orders/{id}` - Cancel order

#### Portfolio
- `GET /trade-api/v2/portfolio/positions` - User positions
- `GET /trade-api/v2/portfolio/fills` - User fills
- `GET /trade-api/v2/portfolio/balance` - Account balance

#### Market Data
- `GET /trade-api/v2/orderbook` - Order book snapshot
- `GET /trade-api/v2/trades` - Recent trades

## Testing Strategy

### Integration Tests
- Test complete order flow from creation to execution
- Verify position tracking with various scenarios
- Ensure WebSocket updates trigger correctly

### Key Test Scenarios
1. Simple buy/sell matching
2. NO order conversion and matching
3. Partial fills
4. Position netting (same user trading both sides)
5. WebSocket subscription and updates

## Deployment Considerations

1. **Database Migration**: 
   - Current schema uses SQLite for development
   - Production would likely use PostgreSQL
   - Column names must match exactly

2. **WebSocket Scaling**:
   - Current implementation uses in-memory subscription management
   - Production needs distributed pub/sub (Redis, RabbitMQ)

3. **Order Book Performance**:
   - Current implementation suitable for moderate load
   - High-frequency trading would need optimization

## Future Enhancements

1. **Market Orders**: Currently only limit orders supported
2. **Advanced Order Types**: Stop orders, IOC, FOK
3. **Market Making Tools**: Bulk order placement, spread trading
4. **Historical Data API**: Candlestick data, volume analytics
5. **Authentication**: Currently uses demo user ID
6. **Risk Management**: Position limits, margin requirements

## Version History

- **v1.0.0** (Current): WebSocket API implementation with all tests passing
  - Full order matching engine
  - Real-time WebSocket updates
  - Complete REST API
  - Comprehensive test coverage (86 tests)

## Development Setup

1. Clone repository
2. Run `mvn clean install`
3. Start server: `mvn spring-boot:run`
4. WebSocket test: Use provided Python/JavaScript clients
5. REST API: Available at `http://localhost:8080`

## Notes for Frontend Developers

- All prices are in cents (1-99)
- Quantities are in number of contracts
- WebSocket requires session management for subscriptions
- Order book shows YES side only (NO orders converted internally)
- Positions with zero quantity are filtered from API responses