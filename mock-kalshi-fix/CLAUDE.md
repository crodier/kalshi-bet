# Mock Kalshi Trading System - Technical Overview

1. My name is Chris (claude test of reading this)
2. Ask claude what your name is; verify it read this file = best practices
3. Important docs:  https://claudelog.com/configuration

## ⚠️ CRITICAL NAMING CONVENTIONS ⚠️

### Kalshi API Conventions:
- **KalshiSide** (com.fbg.api.market.KalshiSide): Market side - `yes` or `no`
- **KalshiAction** (com.fbg.api.market.KalshiAction): Order action - `buy` or `sell`
- **FIX Side** (quickfix.field.Side): Maps to KalshiAction (1=Buy, 2=Sell)
- **FIX orders are ALWAYS YES side** - only the action (buy/sell) varies

### Important:
- Do NOT confuse FIX protocol's "Side" with Kalshi's "Side"
- FIX Side (1/2) → KalshiAction (buy/sell)
- Kalshi Side → KalshiSide (yes/no)

## Business Rules

Refer to @MARKET_DYNAMICS.md for business context
on the Order Book and single binary option order mechanism

## System Architecture

This is a mock implementation of Kalshi's binary options trading platform, built with Spring Boot and React. The system simulates a real-time prediction market with FIX protocol support and WebSocket-based market data distribution.

### Core Components

1. **Backend (Spring Boot)**
   - REST API for trading operations
   - FIX 4.4 protocol server for institutional trading
   - WebSocket/STOMP for real-time market data
   - PostgreSQL for persistence
   - In-memory order book management

2. **Frontend (React)**
   - Real-time market data display
   - Order placement interface
   - Portfolio management
   - WebSocket client for live updates

## Real-Time Broadcasting Architecture

### Order Book Updates
The system uses an event-driven architecture with broadcast notifications:

1. **OrderBookEventPublisher**: Central event hub
   - Publishes events for all order book changes
   - Supports multiple listeners
   - Events include: OrderPlaced, OrderCanceled, OrderMatched, OrderBookUpdated

2. **WebSocket Broadcasting**:
   - All order book changes are broadcast via STOMP WebSocket
   - Topic structure: `/topic/orderbook/{marketTicker}`
   - Clients receive full order book snapshots on each update
   - No incremental updates - always full book state

3. **Market Data Updates**:
   - Price changes broadcast to `/topic/market/{marketTicker}`
   - Includes best bid/ask and last trade price
   - Updates triggered by order matches

### WebSocket Message Flow
```
Order Placed → OrderBookService → OrderBookEventPublisher → WebSocketPublisher → All Connected Clients
```

## Key Services

### OrderBookService
- Manages in-memory order books using ConcurrentOrderBook
- Handles order placement, cancellation, and matching
- Converts NO orders to YES orders automatically
- Persists order state changes to database

### MarketDataService
- Aggregates order book data for market display
- Calculates best bid/ask prices
- Tracks market statistics (volume, open interest)

### PersistenceService
- Handles all database operations
- Maintains order history
- Tracks user positions and portfolios

## Database Schema

Key tables:
- `markets`: Market definitions and current prices
- `orders`: All orders with status tracking
- `trades`: Executed trades history
- `positions`: User positions by market
- `users`: User accounts and balances

## Business Rules

For detailed business rules including:
- Order matching logic
- Position limits
- Settlement procedures
- Fee structures

See the separate README file.

## Development Notes

### Running the System
1. Start PostgreSQL (via Docker)
2. Run Spring Boot backend: `mvn spring-boot:run`
3. Run React frontend: `npm start`

### Test Data
- SQL scripts in `src/main/resources/data.sql` create test markets
- MockMarketsInitializer loads markets on startup
- Initial order books populated with market maker orders

### Key Configuration
- Backend port: 9090
- FIX port: 9878
- WebSocket endpoint: `/ws`
- Database: PostgreSQL on localhost:5432

## API Conventions

### Buy-Only Architecture
Kalshi uses a **buy-only** architecture where all orders are expressed as BUY orders:
- **Sell YES @ X** → converted to **Buy NO @ (100-X)**
- **Sell NO @ X** → converted to **Buy YES @ (100-X)**
- This conversion happens internally; the API and WebSocket publish buy-only format

### Order Book API Structure

The order book API follows Kalshi's exact format with separated YES and NO sides:

```json
{
  "orderbook": {
    "yes": [[price, quantity], ...],  // Buy YES orders only
    "no": [[price, quantity], ...]     // Buy NO orders only
  }
}
```

### Important Notes:
- The `yes` array contains ONLY Buy YES orders
- The `no` array contains ONLY Buy NO orders  
- Each inner array is `[price_in_cents, quantity]`
- YES side is sorted by price descending (best bids first)
- NO side is sorted by price descending (best bids first)
- Frontend displays Buy NO orders as "Sell YES" at equivalent price

### WebSocket Format
WebSocket publishes follow the same buy-only format:
- `orderbook_snapshot`: Full book with yes/no arrays of buy orders only
- `orderbook_delta`: Updates specify side (yes/no) and are always for buy orders
- FIX integration: All FIX orders are YES side, action determines buy/sell
- Frontend displays Buy NO orders as Sell YES at price (100 - NO price)

### Internal Representation:
- All orders are normalized to YES representation internally
- Buy NO at price X → Sell YES at price (100-X)
- This normalization simplifies matching logic
- The API response separates them back into YES/NO for Kalshi compatibility

## Testing Checklist

When making changes to the order book system:
1. Verify NO→YES order conversion
2. Check WebSocket broadcasts are sent
3. Ensure database persistence
4. Validate price calculations (YES + NO = 100)
5. Test order matching logic
6. Verify market data updates
7. Confirm order book API returns proper Kalshi format

## Common Issues

1. **Markets not showing**: Check if data.sql has run and markets table is populated
2. **WebSocket disconnections**: Ensure CORS is configured properly
3. **Order book not updating**: Verify OrderBookEventPublisher has registered listeners
4. **Database connection**: Check PostgreSQL credentials in application.properties