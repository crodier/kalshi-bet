# Kalshi Exchange API Specification

## Overview

This document specifies the REST and WebSocket APIs for our Kalshi exchange implementation. The APIs follow Kalshi's design patterns while integrating with our internal order book and FIX gateway.

## REST API

### Base URL
```
https://api.kalshi-exchange.com/v1
```

### Authentication
All REST endpoints require authentication via Bearer token:
```
Authorization: Bearer <token>
```

### Order Management Endpoints

#### 1. Submit Order
```http
POST /orders
Content-Type: application/json

{
  "market_ticker": "TRUMP-2024",
  "side": "yes" | "no",
  "action": "buy" | "sell",
  "type": "limit" | "market",
  "quantity": 100,
  "price": 65,  // Optional for market orders, in cents
  "time_in_force": "gtc" | "ioc" | "fok",
  "client_order_id": "client-123"  // Optional
}

Response:
{
  "order_id": "ord_abc123",
  "client_order_id": "client-123",
  "status": "open",
  "created_at": 1640001234567
}
```

#### 2. Cancel Order
```http
DELETE /orders/{order_id}

Response:
{
  "order_id": "ord_abc123",
  "status": "canceled",
  "canceled_at": 1640001234567
}
```

#### 3. Modify Order
```http
PUT /orders/{order_id}
Content-Type: application/json

{
  "quantity": 150,  // Optional
  "price": 66      // Optional
}

Response:
{
  "order_id": "ord_abc123",
  "status": "open",
  "modified_at": 1640001234567
}
```

#### 4. Get Order Status
```http
GET /orders/{order_id}

Response:
{
  "order_id": "ord_abc123",
  "client_order_id": "client-123",
  "market_ticker": "TRUMP-2024",
  "side": "yes",
  "action": "buy",
  "type": "limit",
  "price": 65,
  "quantity": 100,
  "filled_quantity": 25,
  "remaining_quantity": 75,
  "average_fill_price": 65,
  "status": "partial_fill",
  "created_at": 1640001234567,
  "updated_at": 1640001234789
}
```

#### 5. List Orders
```http
GET /orders?market_ticker=TRUMP-2024&status=open

Response:
{
  "orders": [
    {
      "order_id": "ord_abc123",
      // ... full order object
    }
  ],
  "next_cursor": "cursor_xyz"
}
```

### Market Data Endpoints

#### 1. Get Order Book
```http
GET /markets/{market_ticker}/orderbook?depth=10

Response:
{
  "market_ticker": "TRUMP-2024",
  "timestamp": 1640001234567,
  "sequence": 12345,
  "yes_bids": [
    {"price": 65, "size": 220, "orders": 2},
    {"price": 64, "size": 200, "orders": 1}
  ],
  "yes_asks": [
    {"price": 66, "size": 300, "orders": 1},
    {"price": 67, "size": 150, "orders": 1}
  ],
  "no_bids": [
    {"price": 35, "size": 150, "orders": 1},
    {"price": 34, "size": 200, "orders": 1}
  ],
  "no_asks": [
    {"price": 36, "size": 220, "orders": 2},
    {"price": 37, "size": 350, "orders": 1}
  ]
}
```

#### 2. Get Market Info
```http
GET /markets/{market_ticker}

Response:
{
  "ticker": "TRUMP-2024",
  "title": "Donald Trump wins 2024 presidential election",
  "status": "active",
  "yes_bid": 65,
  "yes_ask": 66,
  "last_price": 65,
  "volume_24h": 150000,
  "open_interest": 500000
}
```

#### 3. Get Recent Trades
```http
GET /markets/{market_ticker}/trades?limit=50

Response:
{
  "trades": [
    {
      "trade_id": "trd_123",
      "price": 65,
      "size": 100,
      "side": "yes",
      "timestamp": 1640001234567
    }
  ]
}
```

### Batch Operations

#### Batch Order Submission
```http
POST /orders/batch
Content-Type: application/json

{
  "orders": [
    {
      "market_ticker": "TRUMP-2024",
      "side": "yes",
      "action": "buy",
      "type": "limit",
      "quantity": 100,
      "price": 64
    },
    {
      "market_ticker": "BIDEN-2024",
      "side": "no",
      "action": "sell",
      "type": "limit",
      "quantity": 50,
      "price": 40
    }
  ]
}

Response:
{
  "results": [
    {"order_id": "ord_123", "status": "success"},
    {"order_id": "ord_124", "status": "success"}
  ]
}
```

## WebSocket API

### Connection
```
wss://ws.kalshi-exchange.com/v1/stream
```

### Authentication
Send authentication message after connection:
```json
{
  "type": "auth",
  "token": "Bearer <token>"
}
```

### Message Format

All messages follow this structure:
```json
{
  "type": "channel_name",
  "sequence": 12345,
  "timestamp": 1640001234567,
  "data": { /* channel-specific data */ }
}
```

### Subscription Management

#### Subscribe
```json
{
  "id": 1,
  "type": "subscribe",
  "channels": ["orderbook", "ticker", "trades"],
  "markets": ["TRUMP-2024", "BIDEN-2024"]
}
```

#### Unsubscribe
```json
{
  "id": 2,
  "type": "unsubscribe",
  "subscriptions": ["sub_123", "sub_124"]
}
```

### Market Data Channels

#### 1. Order Book Channel

**Full Snapshot** (sent on subscribe):
```json
{
  "type": "orderbook_snapshot",
  "sequence": 12345,
  "timestamp": 1640001234567,
  "data": {
    "market": "TRUMP-2024",
    "bids": {
      "yes": [[65, 220], [64, 200]],
      "no": [[35, 150], [34, 200]]
    },
    "asks": {
      "yes": [[66, 300], [67, 150]],
      "no": [[36, 220], [37, 350]]
    }
  }
}
```

**Incremental Update**:
```json
{
  "type": "orderbook_update",
  "sequence": 12346,
  "timestamp": 1640001234568,
  "data": {
    "market": "TRUMP-2024",
    "changes": [
      {"side": "yes", "type": "bid", "price": 65, "size": 270},  // Update
      {"side": "yes", "type": "ask", "price": 68, "size": 0}     // Remove
    ]
  }
}
```

#### 2. Ticker Channel
```json
{
  "type": "ticker",
  "sequence": 12347,
  "timestamp": 1640001234569,
  "data": {
    "market": "TRUMP-2024",
    "yes_bid": 65,
    "yes_ask": 66,
    "no_bid": 35,
    "no_ask": 36,
    "last_price": 65,
    "volume_24h": 150100,
    "open_interest": 500050
  }
}
```

#### 3. Trades Channel
```json
{
  "type": "trade",
  "sequence": 12348,
  "timestamp": 1640001234570,
  "data": {
    "market": "TRUMP-2024",
    "trade_id": "trd_789",
    "price": 65,
    "size": 50,
    "side": "yes",
    "buyer_maker": false
  }
}
```

### Private Channels (User-specific)

#### 1. Order Updates
```json
{
  "type": "order_update",
  "sequence": 12349,
  "timestamp": 1640001234571,
  "data": {
    "order_id": "ord_abc123",
    "market": "TRUMP-2024",
    "status": "partial_fill",
    "filled_quantity": 25,
    "remaining_quantity": 75,
    "average_fill_price": 65
  }
}
```

#### 2. Fills
```json
{
  "type": "fill",
  "sequence": 12350,
  "timestamp": 1640001234572,
  "data": {
    "order_id": "ord_abc123",
    "trade_id": "trd_789",
    "market": "TRUMP-2024",
    "side": "yes",
    "action": "buy",
    "price": 65,
    "size": 25,
    "fee": 0.50,
    "is_maker": true
  }
}
```

## Error Handling

### REST API Errors
```json
{
  "error": {
    "code": "INSUFFICIENT_BALANCE",
    "message": "Insufficient balance for order",
    "details": {
      "required": 6500,
      "available": 5000
    }
  }
}
```

### WebSocket Errors
```json
{
  "type": "error",
  "error": {
    "code": "INVALID_MARKET",
    "message": "Market INVALID-2024 not found"
  }
}
```

## Rate Limits

- REST API: 100 requests/second
- WebSocket messages: 50 messages/second
- Order submissions: 10 orders/second

## Implementation Notes

### Internal Integration

1. **REST to FIX Bridge**:
   - REST orders converted to `IncomingOrder` objects
   - Sent to FIX gateway for execution
   - Order status tracked in database

2. **Order Book to WebSocket**:
   - `ConcurrentOrderBookV2` publishes events
   - `OrderBookManager` formats for WebSocket
   - WebSocket server broadcasts to subscribers

3. **NO/YES Conversion**:
   - Applied internally in order book
   - Transparent to API users
   - Original side tracked for reporting

### Example Flow

1. User submits order via REST:
   ```
   POST /orders
   {"market_ticker": "TRUMP-2024", "side": "no", "action": "buy", ...}
   ```

2. Server converts to `IncomingOrder.NewOrder`

3. Order book applies NO→YES conversion:
   - NO BUY @ 30¢ → YES SELL @ 70¢

4. WebSocket publishes updates:
   - `orderbook_update`: YES ask level 70¢ increased
   - `ticker`: Updated best ask if applicable

5. If matched, additional events:
   - `trade`: Execution details
   - `fill`: User-specific fill notification