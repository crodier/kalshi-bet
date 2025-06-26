# Market Data Server Admin Frontend

A real-time admin interface for monitoring and managing the Kalshi Market Data Server.

## Features

### 📊 System Statistics Dashboard
- **Real-time Metrics**: Total markets, active markets, bootstrapped markets
- **Performance Monitoring**: Messages/sec, average latency, max latency, max throughput
- **System Health**: WebSocket status, uptime, memory usage
- **Message Statistics**: Messages received/published/skipped with skip rate calculation

### 🔍 Advanced Market Search
- **Fast Search**: Trie-based prefix matching with infix substring support
- **Auto-complete**: Real-time filtering as you type
- **Market Details**: Ticker, status, last update time, message counts
- **Selection State**: Visual indication of selected market

### 📈 Order Book Viewer
- **Real-time Order Book**: Live YES/NO bid displays
- **Timestamp Tracking**: Shows received and published timestamps for each level
- **Processing Latency**: Displays latency between message receipt and publishing
- **Visual Flashing**: Price level changes trigger visual animations
- **Message Counts**: Track total messages and last update time

### 🔄 Real-time Updates
- **WebSocket Connection**: Admin-specific WebSocket endpoint for live data
- **Connection Management**: Auto-reconnect with exponential backoff
- **Status Indicators**: Connection status with visual feedback
- **Periodic Updates**: System stats refresh every 5 seconds

## Technology Stack

- **React 18** with hooks and modern patterns
- **Vite** for fast development and optimized builds
- **WebSocket** for real-time data streaming
- **Axios** for REST API communication
- **CSS Grid/Flexbox** for responsive layouts
- **Playwright** for end-to-end testing

## Development Setup

### Prerequisites
- Node.js 18+
- Market Data Server running on port 8084

### Installation
```bash
cd market-data-server/frontend
npm install
```

### Development Server
```bash
npm run dev
```
Opens on http://localhost:3000 with proxy to backend

### Production Build
```bash
npm run build
```
Builds to `../src/main/resources/static` for Spring Boot serving

## Testing

### Unit Tests
```bash
npm test
```

### E2E Tests with Playwright
```bash
npm run test:e2e
```

### Install Playwright Browsers
```bash
npm run install-playwright
```

## API Integration

### REST Endpoints
- `GET /api/admin/stats` - System statistics
- `GET /api/admin/markets` - All markets
- `GET /api/admin/markets/search?q={query}` - Search markets
- `GET /api/admin/orderbook/{ticker}` - Order book data

### WebSocket Admin Endpoint
- **URL**: `ws://localhost:8084/ws/admin`
- **Channels**: `system-stats`, `market-data`, `market-filter-result`
- **Commands**: `subscribe`, `unsubscribe`, `filter-markets`

### Spring Boot Actuator
- `GET /actuator/health` - Application health
- `GET /actuator/metrics` - JVM and application metrics
- `GET /actuator/info` - Application information

## Components

### SystemStats
Displays comprehensive system metrics with refresh capability and performance indicators.

### MarketFilter  
Advanced search interface with Trie-based filtering and market selection.

### OrderBookViewer
Real-time order book display with timestamp tracking and visual change indicators.

### ConnectionStatus
WebSocket connection status with visual indicators and state management.

## Docker Deployment

### Development
```bash
docker-compose up market-data-admin
```
Available at http://localhost:3000

### Production
The frontend is built and served by nginx with proxy configuration for API and WebSocket requests.

## Architecture

```
┌─────────────────┐    WebSocket     ┌──────────────────┐
│  Admin Frontend │ ←─────────────→  │ Market Data      │
│   (React SPA)   │    /ws/admin     │ Server (Spring)  │
└─────────────────┘                  └──────────────────┘
         │                                     │
         │ REST API                           │
         │ /api/admin/*                       │
         └────────────────────────────────────┘
```

## Key Features Implementation

### Market Search with Trie
The backend implements a Trie data structure for O(k) prefix matching where k is the query length, providing instant search results even with thousands of markets.

### Real-time Flashing
Order book changes trigger CSS animations with configurable flash duration and color coding for different market sides.

### Latency Tracking
Each order book update displays received and published timestamps, allowing real-time monitoring of processing latency.

### Responsive Design
Mobile-first design that adapts to different screen sizes while maintaining full functionality.

## Performance

- **Search**: Sub-millisecond market filtering
- **Updates**: 60fps order book animations
- **Memory**: Efficient WebSocket subscription management
- **Network**: Optimized message frequency and compression

## Browser Support

- Chrome 90+ ✅
- Firefox 88+ ✅  
- Safari 14+ ✅
- Edge 90+ ✅

## Contributing

1. Follow the existing component patterns
2. Add tests for new features
3. Ensure responsive design compliance
4. Update documentation for API changes