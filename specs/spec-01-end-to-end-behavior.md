# Specification 01: End-to-End System Behavior
## Kalshi Binary Options Trading Platform

### Document Version: 1.0
### Date: 2024-01-27
### Status: Draft

---

## Table of Contents

1. [System Overview](#1-system-overview)
2. [Architecture Components](#2-architecture-components)
3. [Data Flow Pipelines](#3-data-flow-pipelines)
4. [Trading Flow - Order Lifecycle](#4-trading-flow---order-lifecycle)
5. [Market Data Pipeline](#5-market-data-pipeline)
6. [FIX Protocol Integration](#6-fix-protocol-integration)
7. [Order Book Management](#7-order-book-management)
8. [Kafka Message Translation](#8-kafka-message-translation)
9. [Admin Monitoring System](#9-admin-monitoring-system)
10. [Error Handling & Recovery](#10-error-handling--recovery)
11. [Testing Strategy](#11-testing-strategy)

---

## 1. System Overview

The Kalshi Binary Options Trading Platform is a distributed system that enables trading of binary options contracts through multiple interfaces (REST API, FIX Protocol) with real-time market data distribution and comprehensive monitoring capabilities.

### 1.1 Key Characteristics
- **Binary Options**: YES/NO contracts that settle at $0 or $100
- **Buy-Only Architecture**: All orders internally converted to BUY orders
- **Dual Pipeline Architecture**: Separate pipelines for trading and market data
- **Event-Driven**: Apache Pekko actors with event sourcing
- **Real-Time Distribution**: WebSocket and Kafka for data propagation

### 1.2 Core Business Rules
- **Price Invariant**: YES Price + NO Price = $100
- **Order Conversion**: 
  - Sell YES @ X → Buy NO @ (100-X)
  - Sell NO @ X → Buy YES @ (100-X)
- **Settlement**: Binary outcome - contracts settle at $0 or $100

---

## 2. Architecture Components

### 2.1 Trading System Components

#### 2.1.1 temp-orders (FIX Gateway & Order Management)
- **Technology**: Spring Boot, Apache Pekko, QuickFIX/J
- **Responsibilities**:
  - REST API for order placement/cancellation
  - FIX protocol gateway for institutional trading
  - Order lifecycle management via event-sourced actors
  - Position tracking and risk management
  - Wallet integration for collateral management

#### 2.1.2 mock-kalshi-fix (Exchange Simulator)
- **Technology**: Spring Boot, QuickFIX/J, PostgreSQL
- **Responsibilities**:
  - Order book management (ConcurrentOrderBook)
  - Order matching engine
  - FIX protocol server
  - WebSocket market data distribution
  - Trade execution and settlement

### 2.2 Market Data Components

#### 2.2.1 market-data-server
- **Technology**: Spring Boot, Kafka Producer
- **Responsibilities**:
  - Connects to mock exchange WebSocket
  - Normalizes market data
  - Publishes to Kafka topics
  - Maintains market data cache

#### 2.2.2 order-book-rebuilder
- **Technology**: Spring Boot, Kafka Consumer
- **Responsibilities**:
  - Consumes market data from Kafka
  - Rebuilds order book state
  - Publishes to WebSocket clients
  - Maintains order book snapshots

### 2.3 Monitoring Components

#### 2.3.1 admin-frontend
- **Technology**: React, WebSocket clients
- **Responsibilities**:
  - Single pane of glass for system monitoring
  - Real-time order status tracking
  - Market data visualization
  - System health monitoring
  - Throughput metrics display

---

## 3. Data Flow Pipelines

### 3.1 Trading Pipeline

```
REST API → temp-orders → FIX Protocol → mock-kalshi-fix → Order Book
    ↓                                           ↓
    └→ Kafka (FIX-ORDER-<ENV>)                 └→ WebSocket (Market Data)
                                                        ↓
                                                  market-data-server
                                                        ↓
                                                  Kafka (MARKET-DATA)
                                                        ↓
                                                  order-book-rebuilder
                                                        ↓
                                                  WebSocket Clients
```

### 3.2 Market Data Pipeline

```
mock-kalshi-fix (Order Book Changes)
         ↓
    WebSocket (Market Data)
         ↓
   market-data-server
         ↓
    Kafka Topics:
    - orderbook-snapshots
    - orderbook-deltas
    - trades
    - ticker-updates
         ↓
   order-book-rebuilder
         ↓
    WebSocket Distribution
         ↓
   Trading Clients & Admin Frontend
```

---

## 4. Trading Flow - Order Lifecycle

### 4.1 Order Placement Flow

#### 4.1.1 REST API Entry
1. **HTTP POST** `/v1/order`
   ```json
   {
     "betOrderId": "550e8400-e29b-41d4-a716-446655440000",
     "symbol": "KXETHD-25JUN2311-T1509.99",
     "side": "BUY",
     "quantity": 100,
     "price": 65,
     "orderType": "LIMIT",
     "timeInForce": "GTC",
     "userId": "user-123"
   }
   ```

2. **OrderController** validates and logs:
   ```
   REST_ORDER_RECEIVED: orderId=550e8400... symbol=KXETHD-25JUN2311-T1509.99 side=BUY qty=100 price=65
   ```

#### 4.1.2 Actor System Processing
1. **OpenPositionProcessManager** workflow:
   - Query PositionActor for current position
   - Calculate required collateral
   - Debit FBGWalletActor
   - Credit KalshiWalletActor
   - Create order in OrderActor

2. **OrderActor** processing:
   - Generate ClOrdID: `FBG_550e8400_e29b_41d4_a716_446655440000`
   - Store in Redis: `fix-orders/clordid/<clOrdId>` → `<betOrderId>`
   - Send to FixGatewayActor

#### 4.1.3 FIX Protocol Transmission
1. **FixGatewayActor** creates NewOrderSingle:
   ```
   8=FIXT.1.1|9=256|35=D|34=5|49=FBG|56=KALSHI|52=20240127-12:30:45.123|
   11=FBG_550e8400_e29b_41d4_a716_446655440000|38=100|40=2|44=65|54=1|
   55=KXETHD-25JUN2311-T1509.99|59=1|60=20240127-12:30:45.123|453=1|
   448=b0f5a944-8dbd-46ad-a48b-f82e29f57599_user-123|452=24|10=147|
   ```

2. **Kafka Publication**:
   - Topic: `FIX-ORDER-LOCAL`
   - Message: Serialized order with metadata

### 4.2 Order Types and Time in Force

#### 4.2.1 Immediate or Cancel (IOC)
- **Behavior**: Execute immediately or cancel remainder
- **Order Book Impact**: No resting orders
- **FIX TimeInForce**: Tag 59=3
- **Execution Flow**:
  1. Order arrives at matching engine
  2. Immediate match attempt
  3. Fill what's available
  4. Cancel unfilled quantity
  5. ExecutionReport with OrdStatus=4 (Canceled) for remainder

#### 4.2.2 Good Till Cancel (GTC)
- **Behavior**: Rest in order book until filled or canceled
- **Order Book Impact**: Creates new price level or adds to existing
- **FIX TimeInForce**: Tag 59=1
- **Execution Flow**:
  1. Order arrives at matching engine
  2. Immediate match attempt
  3. Unfilled quantity enters order book
  4. ExecutionReport with OrdStatus=0 (New) for accepted
  5. Subsequent fills generate OrdStatus=1 (PartiallyFilled) or 2 (Filled)

### 4.3 Order Matching and Execution

#### 4.3.1 Matching Engine Logic
```java
public List<Trade> matchOrder(Order incomingOrder) {
    // 1. Convert NO orders to YES equivalents
    if (incomingOrder.getSide() == KalshiSide.NO) {
        incomingOrder = convertToYesEquivalent(incomingOrder);
    }
    
    // 2. Check opposite side of book
    OrderBook oppositeBook = (incomingOrder.getAction() == KalshiAction.BUY) 
        ? askBook : bidBook;
    
    // 3. Match at price levels
    while (!oppositeBook.isEmpty() && canMatch(incomingOrder, oppositeBook.peek())) {
        Order restingOrder = oppositeBook.peek();
        Trade trade = executeTrade(incomingOrder, restingOrder);
        trades.add(trade);
        
        // 4. Update quantities
        updateOrderQuantities(incomingOrder, restingOrder, trade);
        
        // 5. Remove filled orders
        if (restingOrder.isFilled()) {
            oppositeBook.remove(restingOrder);
        }
    }
    
    // 6. Add remainder to book (GTC only)
    if (!incomingOrder.isFilled() && incomingOrder.getTimeInForce() == TimeInForce.GTC) {
        addToOrderBook(incomingOrder);
    }
    
    return trades;
}
```

### 4.4 Execution Report Processing

#### 4.4.1 Exchange to Gateway
1. **mock-kalshi-fix** generates ExecutionReport:
   ```
   8=FIXT.1.1|9=300|35=8|34=10|49=KALSHI|56=FBG|52=20240127-12:30:45.456|
   11=FBG_550e8400_e29b_41d4_a716_446655440000|37=KALSHI_ORDER_789|17=EXEC_001|
   150=0|39=0|55=KXETHD-25JUN2311-T1509.99|54=1|38=100|44=65|14=0|151=100|
   60=20240127-12:30:45.456|10=203|
   ```

2. **QuickfixJApplication** receives and enriches:
   - Extract ClOrdID → lookup betOrderId in Redis
   - Attach original order data
   - Create enriched ExecutionReport

3. **Kafka Publication**:
   - Topic: `FIX-EXECUTION-LOCAL`
   - Message: Enriched execution report

---

## 5. Market Data Pipeline

### 5.1 Order Book Change Detection

#### 5.1.1 Order Book Update Events
```java
public class OrderBookEventPublisher {
    public void publishOrderBookUpdate(String marketId, OrderBookSnapshot snapshot) {
        // 1. Calculate deltas from previous state
        OrderBookDelta delta = calculateDelta(previousSnapshot, snapshot);
        
        // 2. Publish to WebSocket
        webSocketPublisher.publishSnapshot(marketId, snapshot);
        webSocketPublisher.publishDelta(marketId, delta);
        
        // 3. Update market statistics
        MarketStats stats = calculateStats(snapshot);
        webSocketPublisher.publishTicker(marketId, stats);
    }
}
```

### 5.2 Market Data Server Processing

#### 5.2.1 WebSocket Subscription
```java
@Component
public class MarketDataWebSocketClient {
    @EventHandler
    public void handleOrderBookSnapshot(OrderBookSnapshot snapshot) {
        // 1. Convert to normalized format
        MarketDataEnvelope envelope = MarketDataEnvelope.builder()
            .type(MessageType.ORDERBOOK_SNAPSHOT)
            .marketId(snapshot.getMarketId())
            .timestamp(Instant.now())
            .data(normalizeOrderBook(snapshot))
            .build();
        
        // 2. Publish to Kafka
        kafkaProducer.send("market-data-orderbook-snapshots", envelope);
        
        // 3. Update local cache
        marketDataCache.updateOrderBook(snapshot.getMarketId(), envelope);
    }
}
```

### 5.3 Order Book Rebuilder

#### 5.3.1 Kafka Consumer
```java
@KafkaListener(topics = {"market-data-orderbook-snapshots", "market-data-trades"})
public void processMarketData(MarketDataEnvelope envelope) {
    switch (envelope.getType()) {
        case ORDERBOOK_SNAPSHOT:
            rebuildOrderBook(envelope);
            break;
        case TRADE:
            updateLastTrade(envelope);
            break;
        case TICKER:
            updateMarketStats(envelope);
            break;
    }
    
    // Publish to WebSocket clients
    broadcastToClients(envelope);
}
```

---

## 6. FIX Protocol Integration

### 6.1 Message Types

#### 6.1.1 Outgoing Messages (Client → Exchange)
- **NewOrderSingle (D)**: New order placement
- **OrderCancelRequest (F)**: Cancel existing order
- **OrderCancelReplaceRequest (G)**: Modify existing order

#### 6.1.2 Incoming Messages (Exchange → Client)
- **ExecutionReport (8)**: Order status updates
- **OrderCancelReject (9)**: Cancel request rejected
- **MarketDataSnapshotFullRefresh (W)**: Market data updates

### 6.2 ClOrdID Management

#### 6.2.1 ID Generation Rules
```kotlin
class FixClOrdIdGenerator {
    fun generateNewOrderClOrdId(betOrderId: String): String {
        // Format: FBG_<betOrderId with underscores>
        return "FBG_${betOrderId.replace("-", "_")}"
    }
    
    fun generateModifyClOrdId(betOrderId: String): String {
        // Format: FBG_<betOrderId>_M_<timestamp>
        return "FBG_${betOrderId.replace("-", "_")}_M_${System.currentTimeMillis()}"
    }
    
    fun generateCancelClOrdId(betOrderId: String): String {
        // Format: FBG_<betOrderId>_C_<timestamp>
        return "FBG_${betOrderId.replace("-", "_")}_C_${System.currentTimeMillis()}"
    }
}
```

#### 6.2.2 Redis Storage Structure
```
fix-orders/
├── clordid/
│   └── FBG_550e8400_e29b_41d4_a716_446655440000 → "550e8400-e29b-41d4-a716-446655440000"
├── orderid/
│   └── 550e8400-e29b-41d4-a716-446655440000 → "FBG_550e8400_e29b_41d4_a716_446655440000"
├── orderdata/
│   └── 550e8400-e29b-41d4-a716-446655440000 → {serialized OrderRequestDTO}
├── cancel/
│   └── 550e8400-e29b-41d4-a716-446655440000 → "FBG_550e8400_e29b_41d4_a716_446655440000_C_1234567890"
├── modify/
│   └── 550e8400-e29b-41d4-a716-446655440000 → "FBG_550e8400_e29b_41d4_a716_446655440000_M_1234567890"
└── latestModifyAccepted/
    └── 550e8400-e29b-41d4-a716-446655440000 → "FBG_550e8400_e29b_41d4_a716_446655440000_M_1234567890"
```

---

## 7. Order Book Management

### 7.1 Order Book Structure

#### 7.1.1 Internal Representation
```java
public class ConcurrentOrderBook {
    // All orders normalized to YES side
    private final ConcurrentSkipListMap<BigDecimal, Queue<Order>> bidLevels;
    private final ConcurrentSkipListMap<BigDecimal, Queue<Order>> askLevels;
    
    // Buy YES orders → bid side
    // Sell YES orders (converted Buy NO) → ask side
}
```

#### 7.1.2 API Representation
```json
{
  "orderbook": {
    "yes": [
      [65, 100],  // Buy YES @ 65¢ for 100 contracts
      [64, 200],
      [63, 150]
    ],
    "no": [
      [35, 100],  // Buy NO @ 35¢ (equiv to Sell YES @ 65¢)
      [36, 200],
      [37, 150]
    ]
  }
}
```

### 7.2 Order Book Impact

#### 7.2.1 New Order Impact
1. **Immediate Execution**: Reduces liquidity on opposite side
2. **Resting Order**: Adds liquidity at price level
3. **Market Statistics Update**: Best bid/ask, depth, spread

#### 7.2.2 Order Book Events
```java
public enum OrderBookEventType {
    ORDER_ADDED,      // New resting order
    ORDER_FILLED,     // Full execution
    ORDER_PARTIALLY_FILLED,  // Partial execution
    ORDER_CANCELLED,  // Manual cancellation
    ORDER_EXPIRED,    // IOC expiration
    TRADE_OCCURRED    // Match executed
}
```

---

## 8. Kafka Message Translation

### 8.1 Kafka Topics

#### 8.1.1 Trading Topics
- `FIX-ORDER-{ENV}`: Outgoing FIX orders
- `FIX-EXECUTION-{ENV}`: Incoming execution reports
- `FIX-ERRORS-{ENV}`: Connection and protocol errors

#### 8.1.2 Market Data Topics
- `market-data-orderbook-snapshots`: Full order book state
- `market-data-orderbook-deltas`: Incremental updates
- `market-data-trades`: Executed trades
- `market-data-ticker`: Price and volume updates

### 8.2 Message Formats

#### 8.2.1 Order Message
```json
{
  "messageType": "NEW_ORDER",
  "timestamp": "2024-01-27T12:30:45.123Z",
  "orderId": "550e8400-e29b-41d4-a716-446655440000",
  "clOrdId": "FBG_550e8400_e29b_41d4_a716_446655440000",
  "userId": "user-123",
  "symbol": "KXETHD-25JUN2311-T1509.99",
  "side": "BUY",
  "quantity": 100,
  "price": 65,
  "orderType": "LIMIT",
  "timeInForce": "GTC",
  "fixMessage": "8=FIXT.1.1|9=256|35=D|..."
}
```

#### 8.2.2 Market Data Message
```json
{
  "type": "ORDERBOOK_SNAPSHOT",
  "marketId": "KXETHD-25JUN2311-T1509.99",
  "timestamp": "2024-01-27T12:30:45.456Z",
  "sequence": 12345,
  "data": {
    "bids": [[65, 100], [64, 200]],
    "asks": [[66, 150], [67, 250]],
    "lastTradePrice": 65,
    "lastTradeTime": "2024-01-27T12:30:40.123Z"
  }
}
```

---

## 9. Admin Monitoring System

### 9.1 Admin Frontend Overview

The admin-frontend provides a single pane of glass for monitoring all system components, displaying real-time status of both trading and market data pipelines.

### 9.2 Dashboard Components

#### 9.2.1 System Health Panel
```javascript
const SystemHealthDashboard = () => {
  return (
    <div className="health-dashboard">
      <ServiceCard 
        name="FIX Gateway"
        status={fixGatewayStatus}
        metrics={{
          connected: true,
          messagesPerSecond: 145,
          lastHeartbeat: "2024-01-27T12:30:45Z",
          sequenceNumber: 12567
        }}
      />
      <ServiceCard 
        name="Market Data Server"
        status={marketDataStatus}
        metrics={{
          websocketConnected: true,
          kafkaLag: 0,
          snapshotsPerMinute: 720,
          lastSnapshot: "2024-01-27T12:30:45Z"
        }}
      />
    </div>
  );
};
```

#### 9.2.2 Order Flow Monitoring
- **Active Orders Grid**: Real-time order status across all markets
- **Execution Timeline**: Visual flow of order lifecycle
- **Position Summary**: Current positions and P&L by market
- **Error Console**: Failed orders and system errors

#### 9.2.3 Market Data Visualization
- **Order Book Depth**: Live order book visualization
- **Price Charts**: Real-time price movements
- **Volume Metrics**: Trading volume by market
- **Spread Analysis**: Bid-ask spread trends

### 9.3 Metrics Collection

#### 9.3.1 Key Performance Indicators
```java
@Component
public class SystemMetricsCollector {
    // Trading Metrics
    private final Counter ordersReceived = Counter.builder("orders.received").register(registry);
    private final Counter ordersExecuted = Counter.builder("orders.executed").register(registry);
    private final Timer orderLatency = Timer.builder("order.latency").register(registry);
    
    // Market Data Metrics
    private final Gauge websocketConnections = Gauge.builder("websocket.connections", () -> connectionCount).register(registry);
    private final Counter marketDataMessages = Counter.builder("market.data.messages").register(registry);
    
    // System Health Metrics
    private final Gauge kafkaLag = Gauge.builder("kafka.consumer.lag", () -> calculateLag()).register(registry);
    private final Gauge fixSequenceGap = Gauge.builder("fix.sequence.gap", () -> getSequenceGap()).register(registry);
}
```

### 9.4 WebSocket Connections

#### 9.4.1 Admin WebSocket Channels
```javascript
// Connect to multiple system endpoints
const connections = {
  trading: new WebSocket('ws://localhost:8080/ws/admin/trading'),
  marketData: new WebSocket('ws://localhost:8081/ws/admin/market-data'),
  orderBook: new WebSocket('ws://localhost:8082/ws/admin/orderbook'),
  systemHealth: new WebSocket('ws://localhost:8080/ws/admin/health')
};

// Subscribe to admin-specific channels
connections.trading.send(JSON.stringify({
  type: 'SUBSCRIBE',
  channels: ['orders', 'executions', 'positions', 'errors']
}));
```

---

## 10. Error Handling & Recovery

### 10.1 FIX Connection Management

#### 10.1.1 Sequence Number Recovery
```java
public class FixSequenceManager {
    public void handleSequenceGap(int expectedSeq, int receivedSeq) {
        if (receivedSeq > expectedSeq) {
            // Gap detected - request resend
            sendResendRequest(expectedSeq, receivedSeq - 1);
        } else if (receivedSeq < expectedSeq) {
            // Possible duplicate - check PossDupFlag
            if (!message.isPossibleDuplicate()) {
                // Real sequence issue - disconnect and reconnect
                initiateSequenceReset();
            }
        }
    }
}
```

### 10.2 Actor System Recovery

#### 10.2.1 Event Sourcing Recovery
```scala
class OrderActor extends EventSourcedBehavior {
  override def onRecovery(state: State, event: Event): State = {
    event match {
      case OrderPlacedEvt(orderId, _, _, _, _) =>
        state.copy(placed = true)
      case OrderFilledEvt(_, _, filledQty, _, status) =>
        state.copy(filledQty = state.filledQty + filledQty, status = status)
    }
  }
}
```

### 10.3 Market Data Recovery

#### 10.3.1 Snapshot Recovery
```java
@EventHandler
public void handleConnectionLoss() {
    // 1. Mark connection as down
    connectionStatus = ConnectionStatus.DISCONNECTED;
    
    // 2. Request full snapshot on reconnection
    reconnectionHandler = () -> {
        requestFullOrderBookSnapshot();
        resubscribeToAllMarkets();
        resyncFromKafkaOffset(lastProcessedOffset);
    };
}
```

---

## 11. Testing Strategy

### 11.1 Unit Testing

#### 11.1.1 Actor Tests
```kotlin
class OrderActorTest {
    @Test
    fun `should transition from placed to filled on execution`() {
        val probe = testKit.createTestProbe<OrderActor.Response>()
        val orderActor = testKit.spawn(OrderActor.create("test-order-1"))
        
        // Place order
        orderActor.tell(OrderActor.PlaceOrder(orderRequest, probe.ref))
        probe.expectMessage(OrderActor.OrderPlaced("test-order-1"))
        
        // Receive fill
        orderActor.tell(OrderActor.OrderFillUpdate(BigDecimal(100), Instant.now()))
        probe.expectMessageClass(OrderActor.OrderFillStatus::class.java)
    }
}
```

### 11.2 Integration Testing

#### 11.2.1 FIX Message Integration Tests
```kotlin
@Test
fun `should generate correct FIX message for IOC order`() {
    // Intercept outgoing FIX messages
    val interceptor = TestFixMessageInterceptor()
    FixMessageInterceptorRegistry.register(interceptor)
    
    // Place IOC order
    val response = restTemplate.postForEntity(
        "/v1/order",
        OrderRequest(
            orderId = "test-ioc-order",
            timeInForce = "IOC",
            // ... other fields
        ),
        String::class.java
    )
    
    // Verify FIX message
    val fixMessage = interceptor.capturedMessages.first()
    assertEquals("3", fixMessage.getString(TimeInForce.FIELD)) // IOC
    assertTrue(fixMessage.toString().contains("59=3"))
}
```

### 11.3 End-to-End Testing

#### 11.3.1 Full Trading Cycle Test
```kotlin
@Test
fun `should complete full trading cycle with market data updates`() {
    // 1. Subscribe to market data
    val marketDataClient = MarketDataWebSocketClient()
    marketDataClient.subscribe("TEST-MARKET")
    
    // 2. Place resting order
    val restingOrder = placeOrder(OrderRequest(
        orderId = "resting-order",
        side = "SELL",
        price = 66,
        quantity = 100,
        timeInForce = "GTC"
    ))
    
    // 3. Verify order book update
    val orderBookUpdate = marketDataClient.waitForOrderBookUpdate()
    assertTrue(orderBookUpdate.asks.contains(listOf(66, 100)))
    
    // 4. Place crossing order
    val crossingOrder = placeOrder(OrderRequest(
        orderId = "crossing-order",
        side = "BUY",
        price = 66,
        quantity = 50,
        timeInForce = "IOC"
    ))
    
    // 5. Verify execution
    val execution = waitForExecution("crossing-order")
    assertEquals(50, execution.filledQuantity)
    
    // 6. Verify market data updates
    val trade = marketDataClient.waitForTrade()
    assertEquals(66, trade.price)
    assertEquals(50, trade.quantity)
    
    // 7. Verify order book adjustment
    val updatedBook = marketDataClient.waitForOrderBookUpdate()
    assertTrue(updatedBook.asks.contains(listOf(66, 50))) // Remaining 50
}
```

### 11.4 Performance Testing

#### 11.4.1 Load Test Scenarios
- **Order Throughput**: 1000 orders/second sustained
- **Market Data Distribution**: 10,000 updates/second
- **Latency Requirements**: 
  - REST to FIX: < 10ms
  - FIX to Exchange: < 5ms
  - Market Data: < 50ms end-to-end

---

## 12. Configuration Reference

### 12.1 Application Properties

```yaml
# temp-orders configuration
fix:
  config-path: classpath:quickfixj-mock.cfg
  session-qualifier: KalshiRT
  
kafka:
  bootstrap-servers: localhost:9092
  topics:
    orders: FIX-ORDER-${ENVIRONMENT}
    executions: FIX-EXECUTION-${ENVIRONMENT}
    
redis:
  host: localhost
  port: 6379
  
# market-data-server configuration  
market-data:
  websocket-url: ws://localhost:9090/trade-api/ws/v2
  kafka-topic-prefix: market-data
  snapshot-interval: 10 # seconds
  
# order-book-rebuilder configuration
rebuilder:
  kafka-consumer-group: order-book-rebuilder
  websocket-port: 8082
  cache-size: 1000
```

---

## 13. Deployment Architecture

### 13.1 Container Structure
```yaml
services:
  postgres:
    image: postgres:15
    environment:
      POSTGRES_DB: trading
      
  redis:
    image: redis:7-alpine
    
  kafka:
    image: confluentinc/cp-kafka:7.5.0
    
  temp-orders:
    build: ./temp-orders
    depends_on: [postgres, redis, kafka]
    
  mock-kalshi-fix:
    build: ./mock-kalshi-fix
    depends_on: [postgres]
    
  market-data-server:
    build: ./market-data-server
    depends_on: [kafka, mock-kalshi-fix]
    
  order-book-rebuilder:
    build: ./order-book-rebuilder
    depends_on: [kafka]
    
  admin-frontend:
    build: ./admin-frontend
    depends_on: [temp-orders, market-data-server]
```

---

## 14. Security Considerations

### 14.1 Authentication & Authorization
- OAuth2/JWT for REST API
- FIX session authentication via RSA signatures
- User-specific order visibility
- Admin role for monitoring access

### 14.2 Data Protection
- TLS for all external connections
- Encrypted FIX sessions
- Secure WebSocket (WSS) for production
- PII masking in logs

---

## 15. Conclusion

This specification defines a comprehensive binary options trading platform with:
- Dual pipeline architecture for trading and market data
- Real-time order book management with proper impact tracking
- FIX protocol integration supporting IOC and GTC orders
- Kafka-based event streaming for scalability
- Complete admin monitoring capabilities
- Robust error handling and recovery mechanisms

The system is designed for high throughput, low latency, and operational visibility, suitable for production deployment in financial markets.