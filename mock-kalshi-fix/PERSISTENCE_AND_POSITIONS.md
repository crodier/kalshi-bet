# Persistence and Position Tracking Implementation

## Overview
We've successfully implemented a complete persistence layer using PostgreSQL for the catalog system (Series, Events, Markets). Position tracking and order persistence are implemented in-memory with plans for PostgreSQL integration.

## Key Components

### 1. PostgreSQL Persistence Layer
- **Database**: PostgreSQL with Docker support
- **Tables**:
  - `series`: Market series catalog
  - `events`: Events within series
  - `markets`: Individual trading markets
  - Additional tables for orders, fills, positions (to be implemented)

### 2. PersistenceService
- Thread-safe database operations using Spring's `@Transactional`
- Handles all CRUD operations for orders, fills, positions, and trades
- Implements proper NULL handling for optional fields
- Efficient querying with indexes on key fields

### 3. PositionsService
- Concurrent position management with `ReadWriteLock` for thread safety
- Tracks both long and short positions
- Handles position lifecycle:
  - Opening new positions (long or short)
  - Increasing existing positions
  - Reducing positions (partial close)
  - Flipping positions (long to short or vice versa)
  - Closing positions completely
- Calculates unrealized P&L and portfolio value

### 4. Position Tracking Logic
- **Buy orders**: Increase position (positive quantity)
- **Sell orders**: Decrease position (negative quantity)
- **Short positions**: Represented as negative quantities
- **Average price calculation**:
  - When increasing: Weighted average of old and new
  - When reducing: Maintains existing average price
  - When flipping: New position at current price

## Database Schema

### Current PostgreSQL Tables

#### Series Table
```sql
CREATE TABLE series (
    ticker VARCHAR(255) PRIMARY KEY,
    frequency VARCHAR(50) NOT NULL,
    title VARCHAR(500) NOT NULL,
    category VARCHAR(100),
    contract_url TEXT,
    fee_type VARCHAR(20) DEFAULT 'quadratic',
    fee_multiplier INTEGER DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

#### Events Table  
```sql
CREATE TABLE events (
    event_ticker VARCHAR(255) PRIMARY KEY,
    series_ticker VARCHAR(255) NOT NULL,
    title VARCHAR(500) NOT NULL,
    category VARCHAR(100),
    status VARCHAR(20) NOT NULL DEFAULT 'unopened',
    mutually_exclusive BOOLEAN DEFAULT false,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (series_ticker) REFERENCES series(ticker) ON DELETE CASCADE
);
```

#### Markets Table
```sql
CREATE TABLE markets (
    ticker VARCHAR(255) PRIMARY KEY,
    event_ticker VARCHAR(255) NOT NULL,
    market_type VARCHAR(20) NOT NULL DEFAULT 'binary',
    title VARCHAR(500) NOT NULL,
    open_time BIGINT,
    close_time BIGINT,
    status VARCHAR(20) NOT NULL DEFAULT 'unopened',
    yes_bid INTEGER,
    yes_ask INTEGER,
    volume BIGINT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (event_ticker) REFERENCES events(event_ticker) ON DELETE CASCADE
);
```

## Test Coverage

### Unit Tests
1. **PositionsServiceTest** (9 tests)
   - Buy/sell fill processing
   - Position calculations
   - P&L calculations
   - Portfolio value calculations

2. **PersistenceServicePositionTest** (10 tests)
   - Creating long/short positions
   - Increasing/reducing positions
   - Flipping positions
   - Multiple markets and sides

### Integration Tests
1. **OrderControllerIntegrationTest** (8 tests)
   - Order creation and cancellation
   - Order retrieval
   - Batch orders
   - Fill generation

2. **PositionTrackingIntegrationTest** (5 tests)
   - Position creation from matched orders
   - Short position tracking
   - Position aggregation
   - NO order conversion effects

## Example Usage

### Creating Orders and Tracking Positions
```bash
# Create a buy order
curl -X POST http://localhost:9090/trade-api/v2/portfolio/orders \
  -H "Content-Type: application/json" \
  -d '{
    "market_ticker": "TRUMPWIN-24NOV05",
    "side": "yes",
    "action": "buy",
    "type": "limit",
    "count": 100,
    "price": 65
  }'

# Get current positions
curl http://localhost:9090/trade-api/v2/portfolio/positions

# Response shows position after trade:
{
  "positions": [{
    "market_id": "TRUMPWIN-24NOV05",
    "market_ticker": "TRUMPWIN-24NOV05",
    "quantity": 100,        // Long position
    "avg_price": 65,
    "side": "yes",
    "realized_pnl": 0,
    "total_cost": 6500
  }]
}
```

### Short Position Example
```bash
# Sell without owning (going short)
curl -X POST http://localhost:9090/trade-api/v2/portfolio/orders \
  -H "Content-Type: application/json" \
  -d '{
    "market_ticker": "BTCZ-23DEC31-B50000",
    "side": "yes",
    "action": "sell",
    "type": "limit",
    "count": 50,
    "price": 80
  }'

# Position will show negative quantity:
{
  "positions": [{
    "market_ticker": "BTCZ-23DEC31-B50000",
    "quantity": -50,        // Short position
    "avg_price": 80,
    "side": "yes",
    "realized_pnl": 0,
    "total_cost": 4000
  }]
}
```

## Key Features

1. **Persistent Storage**: All data survives server restarts
2. **Thread-Safe**: Concurrent access handled properly
3. **Position Netting**: Automatically nets long/short positions
4. **Zero Position Filtering**: Zero quantity positions excluded from API responses
5. **FIFO Matching**: Orders matched in time priority order
6. **Real-time Updates**: Positions updated immediately after trades

## Configuration

### PostgreSQL Configuration
Set in `application.yml`:
```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/kalshi_mock
    username: kalshi
    password: kalshi_dev_password
    driver-class-name: org.postgresql.Driver
```

### Docker Configuration
Use docker-compose for easy setup:
```bash
# Start PostgreSQL
docker-compose up -d postgres

# Start everything
docker-compose up
```

## Next Steps

1. **Add WebSocket support** for real-time position updates
2. **Implement realized P&L tracking** when positions are closed
3. **Add position history** for audit trail
4. **Implement margin/collateral tracking**
5. **Add position limits and risk controls**