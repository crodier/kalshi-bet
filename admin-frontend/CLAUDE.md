My name is chris (test to make sure this file is grokked by Claude at start of coding.)

# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Important

- ALL instructions within this document MUST BE FOLLOWED, these are not optional unless explicitly stated.
- ASK FOR CLARIFICATION If you are uncertain of any of thing within the document.
- DO NOT edit more code than you have to.

## Read the parent folder ../CLAUDE.md for general details.

### This module - Admin Frontend

This is a consolidated admin frontend using React, Vite, and best practices for a consistent look and feel across all services. This frontend will connect to and administer multiple backend services in our Kalshi trading system.

## Architecture Overview

### Multi-Service Administration
This frontend connects to and monitors multiple services:
- **mock-kalshi-fix**: Mock Kalshi exchange server (port 9090)
- **market-data-server**: Market data distribution service 
- **order-book-rebuilder**: Order book reconstruction service
- **temp-orders**: FIX bridge system (when implemented)

### Technology Stack
- **React 18+**: Modern React with hooks and functional components
- **Vite**: Fast build tool and dev server
- **AG Grid**: For Markets grid and Executions grid
- **WebSocket**: Multiple connections to all services
- **CSS Modules/Styled Components**: Consistent styling

## Core Components

### 1. Environment Selector (Top Level)
Located at the very top of the page, above the health dashboard:
- **Environment Dropdown**: Select between Local, Dev, QA, or Prod
- **Service URL Configuration**: Each environment has predefined WebSocket URLs for all services
- **Custom URL Override**: Option to enter custom WebSocket URLs per service
- **Connect/Disconnect Controls**: Manual connection control per service
- **URL Validation**: Real-time validation of WebSocket URL format

#### Environment Configuration Layout
```
┌─────────────────────────────────────────────────────────────────────────────────┐
│  Environment: [Local ▼] | [Apply] | [Save Config]                              │
├─────────────────────────────────────────────────────────────────────────────────┤
│  Mock Server:      ws://localhost:9090/ws           [Custom: _______________]    │
│  Market Data:      ws://localhost:8080/ws           [Custom: _______________]    │
│  Order Rebuilder:  ws://localhost:8081/ws           [Custom: _______________]    │
│  Temp Orders:      ws://localhost:8082/ws           [Custom: _______________]    │
└─────────────────────────────────────────────────────────────────────────────────┘
```

### 2. System Health Dashboard (Below Environment Selector)
Located below the environment selector, this component provides real-time system health:
- **Service Status Indicators**: Stop light system (🟢 Green = Up, 🔴 Red = Down)
- **Uptime Statistics**: Shows uptime percentage and duration since last connection
- **Connection Timestamps**: When each service connected/disconnected or if never connected
- **Real-time Status**: Based on WebSocket connection state for each service
- **Message Throughput**: Messages per second for each service
- **Last Message Timestamp**: Most recent message received from each service
- **Last Market Updated**: Most recently updated market (if applicable)
- **Last Execution**: Most recent trade execution across all services

#### Health Check Widget Layout
A stylized grid component displaying service cards in a responsive layout:

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                           SYSTEM HEALTH DASHBOARD                              │
├─────────────────────────────┬─────────────────────────────┬───────────────────┤
│        MOCK SERVER          │       MARKET DATA           │   ORDER REBUILDER │
│  🟢  Status: Connected      │  🔴  Status: Disconnected   │  🟢  Status: Connected │
│      Uptime: 99.8%          │      Uptime: 0%             │      Uptime: 97.2% │
│      Duration: 2h 15m       │      Duration: Never        │      Duration: 45m │
│      Throughput: 45 msg/s   │      Throughput: 0 msg/s    │      Throughput: 12 msg/s │
│      Last Msg: 14:32:15     │      Last Msg: --           │      Last Msg: 14:32:14 │
│      Market: PRES2024       │      Market: --             │      Market: WEATHER │
│      Execution:             │      Execution: --          │      Execution: -- │
│      BUY YES@67¢×100        │                             │                   │
├─────────────────────────────┼─────────────────────────────┼───────────────────┤
│        TEMP ORDERS          │       GLOBAL STATS          │    LAST EXECUTION │
│  🟢  Status: Connected      │  📊  Total Messages: 15.2K  │  ⚡ Latest Trade   │
│      Uptime: 89.1%          │      Services Up: 3/4       │     Time: 14:32:15 │
│      Duration: 12m          │      Avg Latency: 12ms      │     Market: PRES2024 │
│      Throughput: 3 msg/s    │      Total Throughput: 60/s │     Action: BUY YES │
│      Last Msg: 14:32:10     │      Error Rate: 0.02%      │     Price: 67¢     │
│      Market: SPORTS         │      Last Error: 14:18:33   │     Size: 100      │
│      Execution:             │                             │     Source: Mock   │
│      SELL NO@33¢×50         │                             │                   │
└─────────────────────────────┴─────────────────────────────┴───────────────────┘
```

#### Service Card Structure
Each service card contains:
- **Header**: Service name with large status indicator
- **Connection Info**: Status, uptime %, connection duration
- **Activity Metrics**: Throughput (msg/s), last message timestamp
- **Market Data**: Last updated market ticker
- **Execution Info**: Most recent trade execution details

#### Global Stats Card
- **System Overview**: Total messages, services online count
- **Performance**: Average latency, total throughput
- **Health**: Error rate, last error timestamp

#### Last Execution Card
- **Real-time Trade Info**: Most recent execution across all services
- **Trade Details**: Market, action, price, size, source service
- **Timestamp**: When the execution occurred

### 3. Markets Admin Component (Below Health Dashboard)
Located below the health dashboard, this component provides:
- **Market Selection**: Dropdown/search for markets
- **Service Status Matrix**: Shows which markets exist in each service
- **Real-time Metrics**: Last update time, best bid/ask across services
- **Latency Monitoring**: Service-specific latency measurements
- **Connection Controls**: Toggle connections to mock server and other services

#### Markets Grid (AG Grid)
Columns should include:
- Market Ticker
- Description
- Mock Server (✓/✗, last update, best yes/no buy)
- Market Data Server (✓/✗, last update, best yes/no buy)
- Order Book Rebuilder (✓/✗, last update, best yes/no buy)
- Temp Orders (✓/✗, last update, best yes/no buy)
- Total Latency

### 4. Market Search/Filter Component
- **Trie-based Search**: Efficient market filtering
- **Cursor Navigation**: Keyboard navigation through results
- **WebSocket Command**: Sends filter commands to market-data-server websocket

### 5. Order Book Display Panel (Lower Pane)
When a market is selected, display three order book views side by side:

#### 5.1 Mock Server Order Book
- **Connection Toggle**: Enable/disable (default: enabled)
- **WebSocket Connection**: Direct to mock-kalshi-fix websocket
- **Order Book Component**: Reuse the existing OrderBook component from mock frontend

#### 5.2 Market Data Server Order Book
- **WebSocket Connection**: To market-data-server
- **Same Look & Feel**: Use consistent OrderBook component

#### 5.3 Reconstructed Order Book
- **WebSocket Connection**: To order-book-rebuilder
- **Comparison View**: Shows reconstructed vs original

### 6. Executions Grid (Bottom Panel)
AG Grid displaying:
- Timestamp
- Market
- Side (Yes/No)
- Action (Buy/Sell)
- Price
- Quantity
- Source Service
- Latency

## WebSocket Management

### Independent WebSocket Connections
Each service maintains its own WebSocket connection for optimal isolation and performance:

```javascript
// Independent WebSocket hooks per service
const useWebSocketConnections = (environment) => {
  const mockServer = useIndependentWebSocket(environment.mockServerUrl, {
    onMessage: handleMockServerMessage,
    onConnect: () => updateServiceStatus('mockServer', 'connected'),
    onDisconnect: () => updateServiceStatus('mockServer', 'disconnected'),
    reconnectInterval: 5000
  });
  
  const marketData = useIndependentWebSocket(environment.marketDataUrl, {
    onMessage: handleMarketDataMessage,
    onConnect: () => updateServiceStatus('marketData', 'connected'),
    onDisconnect: () => updateServiceStatus('marketData', 'disconnected'),
    reconnectInterval: 3000
  });
  
  const orderRebuilder = useIndependentWebSocket(environment.orderRebuilderUrl, {
    onMessage: handleOrderRebuilderMessage,
    onConnect: () => updateServiceStatus('orderRebuilder', 'connected'),
    onDisconnect: () => updateServiceStatus('orderRebuilder', 'disconnected'),
    reconnectInterval: 5000
  });
  
  const tempOrders = useIndependentWebSocket(environment.tempOrdersUrl, {
    onMessage: handleTempOrdersMessage,
    onConnect: () => updateServiceStatus('tempOrders', 'connected'),
    onDisconnect: () => updateServiceStatus('tempOrders', 'disconnected'),
    reconnectInterval: 10000
  });

  return { mockServer, marketData, orderRebuilder, tempOrders };
};
```

### Connection States Per Service
Each service independently tracks:
- **Connected**: Green indicator
- **Connecting**: Yellow indicator  
- **Disconnected**: Red indicator
- **Error**: Red with error message
- **Never Connected**: Gray indicator

### Direct Message Handling
Each WebSocket connection directly handles its service's messages:
1. **No Message Routing Required**: Direct service-to-component flow
2. **Service-Specific Parsing**: Each handler knows its message format
3. **Independent Timestamping**: Per-service latency calculation
4. **Isolated Error Handling**: Service failures don't affect others

## Component Structure

```
src/
├── components/
│   ├── admin/
│   │   ├── EnvironmentSelector.jsx   # Top-level environment and URL configuration
│   │   ├── SystemHealthDashboard.jsx # Health check grid widget
│   │   ├── ServiceCard.jsx           # Individual service status card
│   │   ├── GlobalStatsCard.jsx       # System-wide statistics card
│   │   ├── LastExecutionCard.jsx     # Latest execution display card
│   │   ├── MarketsAdmin.jsx          # Markets administration
│   │   ├── MarketSearch.jsx          # Search/filter with trie
│   │   ├── ServiceStatus.jsx         # Connection status indicators
│   │   └── LatencyMonitor.jsx        # Real-time latency display
│   ├── orderbook/
│   │   ├── OrderBook.jsx             # Reusable order book component
│   │   ├── OrderBookPanel.jsx        # Multi-service order book display
│   │   └── OrderBookComparison.jsx   # Side-by-side comparison
│   ├── grids/
│   │   ├── MarketsGrid.jsx           # AG Grid for markets
│   │   └── ExecutionsGrid.jsx        # AG Grid for executions
│   └── layout/
│       ├── Navigation.jsx            # Main navigation
│       └── Layout.jsx                # Overall layout
├── services/
│   ├── websocket/
│   │   ├── useIndependentWebSocket.js # Individual WebSocket hook
│   │   ├── mockServerWebSocket.js     # Mock server WebSocket handler
│   │   ├── marketDataWebSocket.js     # Market data WebSocket handler
│   │   ├── orderRebuilderWebSocket.js # Order rebuilder WebSocket handler
│   │   ├── tempOrdersWebSocket.js     # Temp orders WebSocket handler
│   │   └── connectionStatus.js        # Per-service connection tracking
│   ├── environment/
│   │   ├── environmentConfig.js       # Environment URL configurations
│   │   └── environmentManager.js      # Environment switching logic
│   └── api/
│       └── RestClients.js             # REST API clients
├── hooks/
│   ├── useWebSocketConnections.js    # Main hook managing all independent WebSockets
│   ├── useEnvironmentConfig.js       # Environment URL configuration hook
│   ├── useMarketData.js              # Market data management
│   ├── useLatencyMonitor.js          # Latency tracking per service
│   ├── useSystemHealth.js            # System health and uptime tracking
│   ├── useThroughputMonitor.js       # Message throughput monitoring per service
│   └── useExecutionTracker.js        # Last execution tracking across services
└── utils/
    ├── marketTrie.js                 # Trie for market search
    └── latencyCalculator.js          # Latency computation
```

## Key Features

### 1. System Health and Uptime Tracking
Track service availability and uptime:
- Connection state monitoring (connected, disconnected, never connected)
- Uptime percentage calculation since page load
- Connection duration tracking
- Historical connection events logging
- Visual stoplight indicators (green/red)
- Real-time message throughput monitoring (messages per second)
- Last message timestamp tracking
- Last market updated tracking (per service)
- Last execution tracking across all services

### 2. Real-time Latency Monitoring
Track end-to-end latency:
- Message timestamp from source
- Network transmission time
- Processing time in admin frontend
- Display running average and current latency

### 3. Market Comparison
- Side-by-side order book comparison
- Highlight differences between services
- Show synchronization status

### 4. Independent Connection Management
Each service has its own connection management:
- **Service-Specific Reconnection**: Different retry strategies per service
- **Manual Controls**: Individual connect/disconnect per service
- **Isolated Health Monitoring**: Per-service connection state tracking
- **Environment Switching**: Seamless reconnection when changing environments

### 5. Performance Considerations
- Efficient WebSocket message handling
- Debounced UI updates for high-frequency data
- Memory management for large datasets
- Virtual scrolling for large grids

## Integration Requirements

### WebSocket Endpoints
- Mock Server: `ws://localhost:9090/ws`
- Market Data Server: `ws://localhost:[market-data-port]/ws`
- Order Book Rebuilder: `ws://localhost:[rebuilder-port]/ws`
- Temp Orders: `ws://localhost:[temp-orders-port]/ws`

### REST Endpoints
- Market listings from each service
- Health check endpoints
- Configuration endpoints

## Development Guidelines

### Code Quality
- Use TypeScript for type safety
- Implement proper error boundaries
- Add comprehensive logging
- Write unit tests for critical components

### UI/UX Consistency
- Consistent color scheme across all components
- Responsive design for different screen sizes
- Loading states and error handling
- Accessibility compliance

### Performance
- Lazy load components
- Implement virtualization for large datasets
- Optimize re-renders with React.memo
- Use proper dependency arrays in useEffect

## Testing Strategy

### Unit Tests
- WebSocket connection management
- Message parsing and routing
- Latency calculations
- Market search functionality

### Integration Tests
- Multi-service connectivity
- Order book synchronization
- Real-time updates

### E2E Tests
- Complete workflow testing
- Cross-service data consistency
- Performance under load

## Configuration

### Environment-Based WebSocket URLs
```javascript
const environmentConfigs = {
  Local: {
    mockServerUrl: 'ws://localhost:9090/ws',
    marketDataUrl: 'ws://localhost:8080/ws',
    orderRebuilderUrl: 'ws://localhost:8081/ws',
    tempOrdersUrl: 'ws://localhost:8082/ws'
  },
  Dev: {
    mockServerUrl: 'wss://dev-mock.kalshi.com/ws',
    marketDataUrl: 'wss://dev-market-data.kalshi.com/ws',
    orderRebuilderUrl: 'wss://dev-order-rebuilder.kalshi.com/ws',
    tempOrdersUrl: 'wss://dev-temp-orders.kalshi.com/ws'
  },
  QA: {
    mockServerUrl: 'wss://qa-mock.kalshi.com/ws',
    marketDataUrl: 'wss://qa-market-data.kalshi.com/ws',
    orderRebuilderUrl: 'wss://qa-order-rebuilder.kalshi.com/ws',
    tempOrdersUrl: 'wss://qa-temp-orders.kalshi.com/ws'
  },
  Prod: {
    mockServerUrl: 'wss://mock.kalshi.com/ws',
    marketDataUrl: 'wss://market-data.kalshi.com/ws',
    orderRebuilderUrl: 'wss://order-rebuilder.kalshi.com/ws',
    tempOrdersUrl: 'wss://temp-orders.kalshi.com/ws'
  }
};
```

### Per-Service Configuration
- **Independent Connection Timeouts**: Each service can have different timeout settings
- **Service-Specific Retry Logic**: Customizable reconnection strategies
- **Custom URL Override**: Runtime URL changes per service
- **Connection Persistence**: Save custom URLs to localStorage

## Future Enhancements

### Phase 1 (Current)
- Basic multi-service connectivity
- Order book display and comparison
- Markets grid with AG Grid
- Executions grid

### Phase 2 (Future)
- Advanced filtering and search
- Historical data playback
- Performance analytics dashboard
- Alert system for anomalies

### Phase 3 (Future)
- Multi-user support
- Role-based access control
- Advanced monitoring and alerting
- Integration with logging systems