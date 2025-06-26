# Order Book Rebuilder

This service listens to Kafka for market data updates and maintains in-memory order books with concurrent access support.

## Features

- Kafka consumer for market data updates
- Concurrent order book data structures using ConcurrentHashMap and ConcurrentSkipListMap
- REST API for bulk order book queries
- WebSocket support for real-time order book subscriptions
- Performance optimized for read-heavy workloads

## Running

### With real Kafka
```bash
mvn spring-boot:run
```

### With test data simulator
```bash
mvn spring-boot:run -Dspring.profiles.active=test
```

## REST API Endpoints

- `GET /api/v1/orderbook/{marketTicker}?depth=5` - Get order book for a specific market
- `POST /api/v1/orderbook/bulk` - Get order books for multiple markets
- `GET /api/v1/orderbook/all?depth=1` - Get all order books
- `GET /api/v1/orderbook/stats` - Get statistics

## WebSocket API

Connect to `ws://localhost:8085/ws/orderbook`

### Subscribe to market
```json
{
  "type": "subscribe",
  "market": "MARKET_MAKER",
  "allChanges": false
}
```

### Unsubscribe
```json
{
  "type": "unsubscribe",
  "market": "MARKET_MAKER"
}
```

### Heartbeat
```json
{
  "type": "heartbeat"
}
```

## Configuration

See `application.properties` for configuration options.