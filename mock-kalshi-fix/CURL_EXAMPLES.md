# Mock Kalshi API - CURL Examples

## Server Setup

1. Build the project:
```bash
mvn clean install -DskipTests
```

2. Start the server:
```bash
java -jar target/mock-kalshi-fix-0.0.1-SNAPSHOT.jar
```

The server will run on http://localhost:9090

## Swagger UI

Access the Swagger UI documentation at: **http://localhost:9090/swagger-ui.html**

## API Examples

### 1. Create a Sell Order
```bash
curl -X POST http://localhost:9090/trade-api/v2/portfolio/orders \
  -H "Content-Type: application/json" \
  -H "KALSHI-ACCESS-KEY: demo-api-key" \
  -d '{
    "market_ticker": "DUMMY_TEST",
    "side": "yes",
    "action": "sell",
    "type": "limit",
    "count": 100,
    "price": 65
  }'
```

### 2. Create a Buy Order (Will Match if Price Crosses)
```bash
curl -X POST http://localhost:9090/trade-api/v2/portfolio/orders \
  -H "Content-Type: application/json" \
  -H "KALSHI-ACCESS-KEY: demo-api-key" \
  -d '{
    "market_ticker": "DUMMY_TEST",
    "side": "yes",
    "action": "buy",
    "type": "limit",
    "count": 50,
    "price": 65
  }'
```

### 3. Create a NO Order (Converts to YES)
Buy NO at 35¢ converts to Sell YES at 65¢:
```bash
curl -X POST http://localhost:9090/trade-api/v2/portfolio/orders \
  -H "Content-Type: application/json" \
  -H "KALSHI-ACCESS-KEY: demo-api-key" \
  -d '{
    "market_ticker": "DUMMY_TEST",
    "side": "no",
    "action": "buy",
    "type": "limit",
    "count": 30,
    "price": 35
  }'
```

### 4. Get All Orders
```bash
curl http://localhost:9090/trade-api/v2/portfolio/orders \
  -H "KALSHI-ACCESS-KEY: demo-api-key"
```

### 5. Get Specific Order
```bash
curl http://localhost:9090/trade-api/v2/portfolio/orders/ORD-1001 \
  -H "KALSHI-ACCESS-KEY: demo-api-key"
```

### 6. Cancel an Order
```bash
curl -X DELETE http://localhost:9090/trade-api/v2/portfolio/orders/ORD-1001 \
  -H "KALSHI-ACCESS-KEY: demo-api-key"
```

### 7. Get Fills (Executed Trades)
```bash
curl http://localhost:9090/trade-api/v2/portfolio/fills \
  -H "KALSHI-ACCESS-KEY: demo-api-key"
```

### 8. Get Positions
```bash
curl http://localhost:9090/trade-api/v2/portfolio/positions \
  -H "KALSHI-ACCESS-KEY: demo-api-key"
```

### 9. Get Account Balance
```bash
curl http://localhost:9090/trade-api/v2/portfolio/balance \
  -H "KALSHI-ACCESS-KEY: demo-api-key"
```

### 10. Batch Orders
Create multiple orders in one request:
```bash
curl -X POST http://localhost:9090/trade-api/v2/portfolio/batch_orders \
  -H "Content-Type: application/json" \
  -H "KALSHI-ACCESS-KEY: demo-api-key" \
  -d '[
    {
      "market_ticker": "DUMMY_TEST",
      "side": "yes",
      "action": "buy",
      "type": "limit",
      "count": 25,
      "price": 60
    },
    {
      "market_ticker": "DUMMY_TEST",
      "side": "yes",
      "action": "sell",
      "type": "limit",
      "count": 40,
      "price": 70
    }
  ]'
```

## Available Test Markets

The following markets are pre-configured:
- `INXD-23DEC29-B5000`
- `BTCZ-23DEC31-B50000`
- `TRUMPWIN-24NOV05`
- `DUMMY_TEST`

## Response Format

All responses are in JSON format. Use `| python3 -m json.tool` to pretty-print:

```bash
curl http://localhost:9090/trade-api/v2/portfolio/orders \
  -H "KALSHI-ACCESS-KEY: demo-api-key" | python3 -m json.tool
```

## Notes

- The API uses a demo user `USER-DEMO-001` for all requests
- Orders are persisted in SQLite database (`kalshi-mock.db`)
- NO/YES conversion is automatic:
  - Buy NO @ P → Sell YES @ (100-P)
  - Sell NO @ P → Buy YES @ (100-P)
- Position tracking is implemented with support for long and short positions
- The order book uses FIFO matching