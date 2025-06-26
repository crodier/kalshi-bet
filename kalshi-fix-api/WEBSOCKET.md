# Kalshi WebSocket API Documentation

## Overview

Kalshi provides a WebSocket API for real-time market data streaming. This document outlines how order book changes are published via WebSocket and the interface we'll implement to replicate this functionality.

## WebSocket Connection

**Endpoint**: `wss://trading-api.kalshi.com/v1/ws`

**Authentication**: 
- First authenticate via REST API to obtain a session token
- Include token in WebSocket connection headers or initial message

## Subscription Model

### Subscribe to Market Data

```json
{
  "id": 1,
  "cmd": "subscribe",
  "params": {
    "channels": ["orderbook_snapshot", "orderbook_delta", "ticker_v2", "trade"],
    "market_tickers": ["TRUMP-2024", "BIDEN-2024"]
  }
}
```

### Subscription Response

```json
{
  "id": 1,
  "status": "success",
  "sids": ["sub-123", "sub-124", "sub-125", "sub-126"]
}
```

## Order Book Updates

### 1. Order Book Snapshot

Published when:
- Client first subscribes to a market
- After reconnection
- Periodically for synchronization

```json
{
  "channel": "orderbook_snapshot",
  "market_ticker": "TRUMP-2024",
  "seq": 12345,
  "data": {
    "yes": [
      [65, 100],  // [price, size]
      [64, 200],
      [63, 300]
    ],
    "no": [
      [35, 150],
      [36, 250],
      [37, 350]
    ]
  }
}
```

### 2. Order Book Delta

Published when:
- New order added to book
- Order canceled or modified
- Order partially or fully filled

```json
{
  "channel": "orderbook_delta", 
  "market_ticker": "TRUMP-2024",
  "seq": 12346,
  "data": {
    "side": "yes",
    "price": 66,
    "delta": 50  // Positive = add, Negative = remove
  }
}
```

### 3. Ticker Updates (Best Bid/Ask)

Published when best bid or ask changes:

```json
{
  "channel": "ticker_v2",
  "market_ticker": "TRUMP-2024",
  "seq": 12347,
  "data": {
    "yes_bid": 65,
    "yes_ask": 66,
    "price": 65,  // Last trade or mid price
    "volume_delta": 10,
    "open_interest_delta": 5
  }
}
```

### 4. Trade Events

Published when trades execute:

```json
{
  "channel": "trade",
  "market_ticker": "TRUMP-2024", 
  "seq": 12348,
  "data": {
    "trade_id": "trade-123",
    "side": "yes",
    "price": 65,
    "size": 25,
    "timestamp": 1640001234567
  }
}
```

### 5. Fill Events (User-specific)

Published when user's orders are filled:

```json
{
  "channel": "fill",
  "market_ticker": "TRUMP-2024",
  "seq": 12349,
  "data": {
    "trade_id": "trade-123",
    "order_id": "order-456",
    "side": "yes",
    "is_taker": true,
    "count": 25
  }
}
```

## Sequence Numbers

- Each message includes a sequence number (`seq`)
- Clients should track sequence to detect gaps
- If gap detected, re-subscribe to get fresh snapshot

## Error Handling

```json
{
  "error": "Invalid market ticker",
  "code": 400
}
```

## Our Implementation Plan

### REST API Endpoints

1. **Order Management**
   - `POST /orders` - Submit new order
   - `PUT /orders/{order_id}` - Modify order
   - `DELETE /orders/{order_id}` - Cancel order
   - `POST /orders/batch` - Batch operations

2. **Market Data** 
   - `GET /markets/{ticker}/orderbook` - Get current order book
   - `GET /markets/{ticker}/trades` - Get recent trades

### WebSocket Publishing

Our `OrderBookManager` will publish updates in the same format:

1. **On Order Add**: Publish `orderbook_delta` with positive delta
2. **On Order Cancel**: Publish `orderbook_delta` with negative delta  
3. **On Order Modify**: Publish one or two deltas (remove old, add new)
4. **On Trade**: Publish `trade` event and update order book
5. **Periodically**: Publish `ticker_v2` with best bid/ask

### Integration Points

```kotlin
// OrderBookManager callback
private val publishCallback: (Channel, Any) -> Unit = { channel, data ->
    websocketServer.broadcast(channel, data)
}

// WebSocket server interface
interface WebSocketServer {
    fun broadcast(channel: Channel, data: Any)
    fun sendToUser(userId: String, channel: Channel, data: Any)
}
```

## Message Flow Example

1. **User submits order via REST**:
   ```
   POST /orders
   {
     "market": "TRUMP-2024",
     "side": "yes",
     "type": "limit",
     "quantity": 100,
     "price": 65
   }
   ```

2. **Server processes order**:
   - Validates order
   - Adds to order book
   - Checks for crosses

3. **Server publishes WebSocket updates**:
   - `orderbook_delta` to all subscribers
   - `ticker_v2` if best bid/ask changed
   - `fill` to affected users if matched

4. **Clients receive real-time updates**:
   - Update local order book state
   - Display new best bid/ask
   - Show execution notifications