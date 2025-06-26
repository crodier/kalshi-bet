# Market Data Server

A high-performance WebSocket proxy service that subscribes to Kalshi market data and redistributes it to multiple clients using Kafka and Redis.

## Architecture

1. **WebSocket Client**: Connects to mock-kalshi-fix server and subscribes to all available markets
2. **Market Data Envelope**: Wraps each message with receivedTimestamp and publishedTimestamp for latency tracking
3. **Kafka Producer**: Publishes all received market data to Kafka with timing metadata
4. **Redis Publisher**: Consumes from Kafka and publishes to Redis channels for real-time distribution
5. **WebSocket Server**: Allows clients to connect and subscribe to specific market data channels

## Features

- **Bootstrap from Historical Data**: On startup, loads market data from Kafka from the past 2 hours to rebuild order book state
- **Smart Deduplication**: Skips publishing identical snapshots and unchanged deltas to reduce Kafka load
- **Timing Metadata**: Wraps each message with `receivedTimestamp` and `publishedTimestamp` for latency analysis
- **Automatic Market Discovery**: Fetches all available markets from the mock server REST API
- **Intelligent Subscriptions**: 
  - Subscribes to all markets for channels that support it (ticker_v2, trade, market_lifecycle_v2)
  - Batched WebSocket subscriptions for orderbook channels to avoid overwhelming the upstream server
- **Auto-reconnect & Refresh**: Automatic reconnection on connection loss and periodic market refresh (every 5 minutes)
- **Kafka Partitioning**: Uses market ticker as key for efficient consumption and ordering
- **Comprehensive Testing**: Unit and integration tests covering all bootstrap and deduplication scenarios
- **Statistics & Monitoring**: Tracks message counts, skip rates, and market state for operational visibility
- **Scalable Architecture**: Ready for 10,000+ concurrent clients with Redis distribution

## Prerequisites

1. Start the required infrastructure:
```bash
cd /home/crodier/coding/kalshi-bet
docker-compose up -d
```

2. Ensure the mock-kalshi-fix server is running on port 9090

## Running the Server

```bash
cd /home/crodier/coding/kalshi-bet/market-data-server
mvn spring-boot:run
```

The server will start on port 8084.

## Client WebSocket Connection

Connect to: `ws://localhost:8084/ws/market-data`

### Subscribe to Channels

```json
{
  "action": "subscribe",
  "channel": "all"
}
```

Or subscribe to specific market channels:
```json
{
  "action": "subscribe",
  "channel": "EURUSD-23JUN2618-B1.087:orderbook_snapshot"
}
```

### Available Channels

- `all` - All market data from all markets
- `{marketTicker}:orderbook_snapshot` - Order book snapshots for specific market
- `{marketTicker}:orderbook_delta` - Order book deltas for specific market
- `{marketTicker}:ticker_v2` - Ticker updates for specific market
- `{marketTicker}:trade` - Trade events for specific market

### Unsubscribe

```json
{
  "action": "unsubscribe",
  "channel": "all"
}
```

### Ping/Pong

```json
{
  "action": "ping"
}
```

## Data Flow

1. Mock Kalshi Server → WebSocket Client → MarketDataEnvelope → Kafka Topic (`market-data-all`)
2. Kafka → Redis Publisher → Redis Pub/Sub Channels
3. Redis → WebSocket Server → Connected Clients

### Message Envelope Format

Each message published to Kafka contains:
```json
{
  "payload": { /* original WebSocket message */ },
  "receivedTimestamp": 1640001234567,
  "publishedTimestamp": 1640001234570,
  "channel": "ticker_v2",
  "marketTicker": "TRUMP-2024",
  "sequence": 12345,
  "source": "kalshi-websocket",
  "version": 1
}
```

### Bootstrap Configuration

The server supports configurable bootstrap behavior:

```properties
# Enable/disable bootstrap on startup
bootstrap.enabled=true

# How far back to look for historical data (in minutes)
bootstrap.lookback.minutes=120
```

## Smart Deduplication Logic

1. **First Startup**: All messages are published to establish initial state
2. **After Bootstrap**: 
   - Identical snapshots are skipped (compares full order book state)
   - Only changed deltas are published
   - Non-orderbook messages (trades, tickers) are always published
3. **Statistics Tracking**: Monitors skip rate and latency for operational insight

## Configuration

See `src/main/resources/application.properties` for configuration options.

## Health Check

GET `http://localhost:8084/health` - Returns health status of Kafka and Redis connections

## Scaling

To handle 10,000+ clients:
1. Run multiple instances of the market-data-server behind a load balancer
2. Use Redis Cluster for horizontal scaling
3. Configure Kafka with appropriate partitions and replication
4. Consider using WebSocket connection pooling on the client side