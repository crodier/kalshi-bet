# Mock Kalshi REST API Guide

## Overview

This mock Kalshi REST API implements a complete trading platform with catalog management (Series, Events, Markets), order management, and portfolio tracking. The API is available at `http://localhost:9090/trade-api/v2`.

## Swagger UI

Access the interactive API documentation at: `http://localhost:9090/swagger-ui/index.html`

## Database

The system uses PostgreSQL for persistent storage of all catalog data, orders, fills, and positions.

## Key Features

- **Concurrent Order Book**: Thread-safe implementation with YES/NO market dynamics
- **NO/YES Conversion**: All NO orders are internally converted to YES equivalents for simplified matching
- **Cross Detection**: Detects both self-crosses and external crosses (arbitrage opportunities)
- **FIFO Priority**: Maintains time priority at each price level

## API Endpoints

### Catalog Management

#### Series
```bash
# List all series
GET /trade-api/v2/series

# Get specific series
GET /trade-api/v2/series/{series_ticker}

# Create new series (admin)
POST /trade-api/v2/series
```

#### Events
```bash
# List all events
GET /trade-api/v2/events

# Get specific event
GET /trade-api/v2/events/{event_ticker}

# Create new event (admin)
POST /trade-api/v2/events
```

#### Markets
```bash
# List all markets
GET /trade-api/v2/markets

# Get specific market
GET /trade-api/v2/markets/{ticker}

# Create new market (admin)
POST /trade-api/v2/markets
```

Example - Create a complete catalog hierarchy:
```bash
# 1. Create a series
curl -X POST http://localhost:9090/trade-api/v2/series \
  -H "Content-Type: application/json" \
  -d '{
    "ticker": "ELECTION2024",
    "frequency": "single",
    "title": "2024 US Presidential Election",
    "category": "politics"
  }'

# 2. Create an event within the series
curl -X POST http://localhost:9090/trade-api/v2/events \
  -H "Content-Type: application/json" \
  -d '{
    "event_ticker": "ELECTION2024-PRES",
    "series_ticker": "ELECTION2024",
    "title": "Presidential Winner",
    "category": "politics",
    "status": "open"
  }'

# 3. Create a market within the event
curl -X POST http://localhost:9090/trade-api/v2/markets \
  -H "Content-Type: application/json" \
  -d '{
    "ticker": "TRUMPWIN-24NOV05",
    "event_ticker": "ELECTION2024-PRES",
    "market_type": "binary",
    "title": "Will Trump win the 2024 election?",
    "open_time": "2024-01-01T00:00:00Z",
    "close_time": "2024-11-05T23:59:59Z"
  }'
```

#### Get Order Book
```bash
GET /trade-api/v2/markets/{ticker}/orderbook
```

Example:
```bash
curl http://localhost:9090/trade-api/v2/markets/TRUMPWIN-24NOV05/orderbook
```

### Orders

#### Create Order
```bash
POST /trade-api/v2/portfolio/orders
```

Request Body:
```json
{
  "market_ticker": "TRUMPWIN-24NOV05",
  "side": "yes",
  "action": "buy",
  "type": "limit",
  "count": 100,
  "price": 65,
  "time_in_force": "GTC",
  "client_order_id": "MY-ORDER-001"
}
```

Example:
```bash
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
```

#### Get User Orders
```bash
GET /trade-api/v2/portfolio/orders
```

Example:
```bash
curl http://localhost:9090/trade-api/v2/portfolio/orders
```

#### Cancel Order
```bash
DELETE /trade-api/v2/portfolio/orders/{order_id}
```

Example:
```bash
curl -X DELETE http://localhost:9090/trade-api/v2/portfolio/orders/ORD-1001
```

### Portfolio

#### Get Balance
```bash
GET /trade-api/v2/portfolio/balance
```

Example:
```bash
curl http://localhost:9090/trade-api/v2/portfolio/balance
```

#### Get Positions
```bash
GET /trade-api/v2/portfolio/positions
```

Example:
```bash
curl http://localhost:9090/trade-api/v2/portfolio/positions
```

## Order Book Dynamics

### YES/NO Conversion

The order book internally converts all NO orders to YES equivalents:

- **Buy NO @ P** → **Sell YES @ (100 - P)**
- **Sell NO @ P** → **Buy YES @ (100 - P)**

This simplifies the matching engine while maintaining market semantics.

### Cross Detection

1. **Self-Cross**: When bid ≥ ask on the same side
   - Example: YES bid at 65¢ crosses YES ask at 64¢

2. **External Cross**: When YES bid + NO bid > 100¢
   - Example: YES bid at 65¢ + NO bid at 40¢ = 105¢ (arbitrage opportunity)

## Example Trading Scenarios

### Scenario 1: Basic YES Trading
```bash
# Buy 100 YES contracts at 65¢
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

# Sell 50 YES contracts at 70¢
curl -X POST http://localhost:9090/trade-api/v2/portfolio/orders \
  -H "Content-Type: application/json" \
  -d '{
    "market_ticker": "TRUMPWIN-24NOV05",
    "side": "yes",
    "action": "sell",
    "type": "limit",
    "count": 50,
    "price": 70
  }'
```

### Scenario 2: NO Trading with Conversion
```bash
# Buy 100 NO contracts at 30¢ (converts to Sell YES at 70¢)
curl -X POST http://localhost:9090/trade-api/v2/portfolio/orders \
  -H "Content-Type: application/json" \
  -d '{
    "market_ticker": "TRUMPWIN-24NOV05",
    "side": "no",
    "action": "buy",
    "type": "limit",
    "count": 100,
    "price": 30
  }'
```

### Scenario 3: Batch Orders
```bash
curl -X POST http://localhost:9090/trade-api/v2/portfolio/batch_orders \
  -H "Content-Type: application/json" \
  -d '[
    {
      "market_ticker": "TRUMPWIN-24NOV05",
      "side": "yes",
      "action": "buy",
      "type": "limit",
      "count": 50,
      "price": 60
    },
    {
      "market_ticker": "TRUMPWIN-24NOV05",
      "side": "yes",
      "action": "buy",
      "type": "limit",
      "count": 50,
      "price": 61
    }
  ]'
```

## Testing the Order Book

1. **Check initial market state**:
```bash
curl http://localhost:9090/trade-api/v2/markets/TRUMPWIN-24NOV05
```

2. **Place some orders**:
```bash
# Place buy orders at different prices
for price in 60 61 62; do
  curl -X POST http://localhost:9090/trade-api/v2/portfolio/orders \
    -H "Content-Type: application/json" \
    -d "{\"market_ticker\":\"TRUMPWIN-24NOV05\",\"side\":\"yes\",\"action\":\"buy\",\"type\":\"limit\",\"count\":100,\"price\":$price}"
done
```

3. **Check the order book**:
```bash
curl http://localhost:9090/trade-api/v2/markets/TRUMPWIN-24NOV05/orderbook
```

4. **View your orders**:
```bash
curl http://localhost:9090/trade-api/v2/portfolio/orders
```

## Notes

- All prices are in cents (1-99)
- The demo uses a fixed user ID for simplicity
- Order matching is not yet implemented (orders will cross but not execute)
- WebSocket support will be added in a future update