# Kafka Test Data Summary

## Topic Status
- **Topic Name**: `MARKET-DATA-ALL`
- **Total Messages**: 50+ test messages
- **Message Types**: Orderbook snapshots, deltas, ticker updates, trades
- **Markets**: DUMMY_TEST, MARKET_MAKER, TRUMPWIN-24NOV05, BTCZ-23DEC31-B50000, INXD-23DEC29-B5000

## Available Test Scripts

### 1. Basic Test Messages (`generate-test-messages.sh`)
- Generates 10 basic test messages
- Contains orderbook snapshots, deltas, ticker updates, and trades
- Fixed timestamps for predictable testing

### 2. Realistic Test Messages (`generate-more-test-messages.sh`)
- Generates 30 messages in 3 batches
- Uses current timestamps
- More realistic price movements and market activity

### 3. Continuous Test Data (`continuous-test-data.sh`)
- Generates continuous stream of realistic market data
- Random price movements and market activity
- Multiple message types with realistic timing
- Press Ctrl+C to stop

## Useful Kafka Commands

### View All Messages
```bash
docker exec kalshi-kafka kafka-console-consumer --bootstrap-server localhost:9092 --topic MARKET-DATA-ALL --from-beginning
```

### Count Messages
```bash
docker exec kalshi-kafka kafka-run-class kafka.tools.GetOffsetShell --broker-list localhost:9092 --topic MARKET-DATA-ALL
```

### Stream New Messages
```bash
docker exec kalshi-kafka kafka-console-consumer --bootstrap-server localhost:9092 --topic MARKET-DATA-ALL
```

### Clear Topic (Delete and Recreate)
```bash
docker exec kalshi-kafka kafka-topics --delete --topic MARKET-DATA-ALL --bootstrap-server localhost:9092
docker exec kalshi-kafka kafka-topics --create --topic MARKET-DATA-ALL --bootstrap-server localhost:9092 --partitions 3 --replication-factor 1
```

## Message Format
All messages follow the market data server envelope format:
```json
{
  "payload": {
    "channel": "orderbook_snapshot|orderbook_delta|ticker|trade",
    "market_ticker": "MARKET_NAME",
    "seq": 123,
    // ... channel-specific data
  },
  "receivedTimestamp": 1735232548000,
  "publishedTimestamp": 1735232548100,
  "channel": "orderbook_snapshot",
  "marketTicker": "MARKET_NAME",
  "sequence": 123,
  "source": "kalshi-websocket",
  "version": 1
}
```

## Testing Notes
- Mock Kalshi server is running on port 9090
- Kafka is running on port 9092
- Redis is running on port 6379
- All test messages use realistic market data formats
- InternalOrderBook implementation can process these messages with granular timestamps