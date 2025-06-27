# Admin Frontend

A comprehensive observability and administration system for the Kalshi trading platform, providing real-time monitoring and control across all services.

## Overview

The Admin Frontend consolidates monitoring and administration capabilities for:
- Mock Kalshi FIX server
- Market Data Server  
- Order Book Rebuilder
- Temp Orders (FIX Bridge with Pekko actors)

## Key Features

### 🏥 System Health Dashboard
- Real-time service status indicators
- Connection uptime and throughput metrics
- Last message timestamps and market updates
- Global system statistics

### 📊 Markets Administration
- AG Grid display of all markets
- Cross-service availability matrix
- Real-time bid/ask prices
- Click-to-filter functionality

### 📋 Orders Panel
- Last 1,000 orders with live updates
- Market-specific filtering
- Order lifecycle tracking
- WebSocket integration with temp-orders

### 📈 Execution Reports Grid
- Full FIX ExecutionReport display
- Real-time execution streaming
- Order association and filtering
- Performance metrics calculation

## Technical Architecture

### Domain Model Integration
Uses JavaScript-compiled domain models from `kalshi-fix-api`:
- `ExecutionReport` - Complete FIX execution report structure
- `IncomingOrder` hierarchy - NewOrder, CancelOrder, ModifyOrder
- Type-safe enums for FIX protocol values

### WebSocket Architecture
Independent WebSocket connections per service:
- Isolated failure domains
- Service-specific reconnection strategies
- Real-time message streaming
- Binary and JSON message support

## Development

### Prerequisites
- Node.js 18+
- NPM 8+
- Built kalshi-fix-api JavaScript artifacts

### Installation
```bash
npm install
```

### Running Development Server
```bash
npm run dev
```

### Running Tests
```bash
npm test
```

### Building for Production
```bash
npm run build
```

## Configuration

### Environment Settings
Configure WebSocket URLs for different environments in `src/services/environment/environmentConfig.js`:

```javascript
{
  Local: {
    mockServerUrl: 'ws://localhost:9090/trade-api/ws/v2',
    marketDataUrl: 'ws://localhost:8084/ws/market-data',
    orderRebuilderUrl: 'ws://localhost:8085/ws/orderbook',
    tempOrdersUrl: 'ws://localhost:8080/ws/orders'
  },
  // ... other environments
}
```

### Custom URL Override
The UI allows runtime URL customization for each service connection.

## WebSocket Message Formats

### Order Updates (temp-orders)
```json
{
  "type": "ORDER_UPDATE",
  "timestamp": "2024-01-15T10:30:45.123Z",
  "sequenceNumber": 12345,
  "data": {
    "betOrderId": "550e8400-e29b-41d4-a716",
    "event": "NEW_ORDER",
    "order": { /* OrderSnapshot */ }
  }
}
```

### Order Book Updates
```json
{
  "type": "orderbook_snapshot",
  "market_ticker": "BTCZ-23DEC31-B50000",
  "yes": [/* levels */],
  "no": [/* levels */],
  "timestamp": "2024-01-15T10:30:45.123Z",
  "seq": 12345
}
```

## Performance Considerations

- Virtual scrolling for large datasets (AG Grid)
- Debounced updates for high-frequency data
- Memory-efficient circular buffers
- React component memoization

## Future Enhancements

- Historical data playback
- Advanced search and filtering
- Custom alerts and notifications
- Multi-user support with roles
- Integration with monitoring systems

## Related Documentation

- [Admin Frontend Specification](../specs/admin-frontend-spec.md)
- [Kalshi FIX API JS Compilation](../specs/kalshi-fix-api-js-compilation.md)
- [Parent Project README](../README.md)
