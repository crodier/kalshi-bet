## Intro

Exchange Order API is a Kotlin-based backend service for managing and orchestrating order workflows on a betting exchange. It leverages event-driven architecture and actor-based concurrency to handle complex, multi-step processes such as order placement, fund transfers, and settlement, ensuring reliability and scalability for high-throughput trading environments.

The system is built using Apache Pekko (the open-source fork of Akka) for typed actors, persistence, and cluster sharding, providing robust state management and distributed processing. It integrates with external systems via QuickFIX/J for FIX protocol messaging, and uses R2DBC for reactive database access, supporting both PostgreSQL and in-memory H2 for testing.

The architecture implements the Saga pattern through process manager actors that orchestrate complex workflows with built-in compensation logic for failure scenarios. Each workflow step is atomic and recoverable, with comprehensive event sourcing ensuring all state changes are auditable and the system can recover gracefully from failures. The OpenPositionProcessManager coordinates wallet debits, order placement, and fill processing as a distributed transaction, automatically compensating (refunding wallet debits) when orders are rejected or cancelled to maintain financial consistency.

Position and risk management capabilities are currently in development, with the PositionActor designed to track real-time position data per user and contract for future margin calculations and risk limits. The system integrates with external wallets (FBG and Kalshi) with comprehensive error handling, timeout management, and simulated failure scenarios for testing, laying the groundwork for robust fund management once full wallet integration is completed.

Serialization and event sourcing are handled with Jackson (including the Kotlin module) for seamless data interchange and compatibility with Kotlin data classes. The project is designed for cloud-native deployment, with configuration and service discovery tailored for Kubernetes environments.

### The actors

- **FIX gateway** (cluster singleton): Manages the FIX protocol connection to the external exchange, handling all inbound and outbound FIX messages and ensuring only one instance is active in the cluster.
- **Order** (sharded event sourced): Represents an individual order's lifecycle, persisting all state changes and events, and coordinating with other actors for fills, cancellations, and status updates.
- **FBGWalletActor** (stateless): Handles requests related to the FBG wallet, such as reserving, transferring, or crediting funds, acting as an interface to the FBG wallet system.
- **KalshiWalletActor** (stateless): Handles requests related to the Kalshi wallet, such as deposits and withdrawals, providing an interface to the Kalshi wallet system.
- **OpenPositionProcessManager** (sharded event sourced): Orchestrates the workflow for opening a new position, coordinating between wallet actors and the order actor to ensure funds are moved and orders are placed atomically.
- **PositionActor** (sharded event sourced): Tracks each user's positions per contract/market, updates positions in response to fills, and provides up-to-date position info for collateral calculations. It is queried before every order to ensure accurate collateral requirements are met.

#### Idea, don't exist yet
- **ClosePositionProcessManager** (sharded event sourced): Manages the workflow for closing a position, ensuring proper settlement, fund transfers, and order state transitions.
- **PositionCompletionProcessManager** (sharded event sourced): Handles the finalization and settlement of positions, including any post-trade processing or compensation logic required for complete lifecycle management.

### Updated Workflow: Order Placement and Collateral Management

1. **User requests to place an order.**
2. **OpenPositionProcessManager** queries **PositionActor** for the user's current position in the relevant contract.
3. **OpenPositionProcessManager** calculates the required collateral for the new order, considering the user's current position.
4. **OpenPositionProcessManager** instructs **WalletActor** to transfer the required funds to Kalshi.
5. On successful transfer, **OpenPositionProcessManager** tells **OrderActor** to place the order.
6. **OrderActor** receives fills/cancels, updates **PositionActor** with the new fill/cancel info.


## Run locally

- Get the secrets from Rich.
- Run 2 nodes, have a postgres running.
```
docker run --rm -e POSTGRES_DB=trading -e POSTGRES_USER=postgres -e POSTGRES_PASSWORD=postgres -p 5432:5432 postgres
GRPC_SERVER_PORT=9090 SERVER_PORT=8080 PEKKO_HOST=127.0.0.1 PEKKO_PORT=2551 ./mvnw spring-boot:run -Dspring-boot.run.profiles=local
GRPC_SERVER_PORT=9091 SERVER_PORT=8080 PEKKO_HOST=127.0.0.1 PEKKO_PORT=2552 ./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

Place an order

```
curl -X POST http://localhost:8080/v1/order \
    -H "Content-Type: application/json" \
    -H "X-Dev-User: test-user-1" \
    -d "{
      \"orderId\": \"$(uuidgen | tr '[:upper:]' '[:lower:]')\",
      \"symbol\": \"KXUSDTMIN-25DEC31-0.95\",
      \"side\": \"SELL\",
      \"quantity\": 1,
      \"price\": 19.00,
      \"orderType\": \"LIMIT\",
      \"timeInForce\": \"GTC\"
    }"
    
curl -X POST http://localhost:8080/v1/order \
    -H "Content-Type: application/json" \
    -H "X-Dev-User: test-user-2" \
    -d "{
      \"orderId\": \"$(uuidgen | tr '[:upper:]' '[:lower:]')\",
      \"symbol\": \"KXUSDTMIN-25DEC31-0.95\",
      \"side\": \"BUY\",
      \"quantity\": 1,
      \"price\": 19.00,
      \"orderType\": \"LIMIT\",
      \"timeInForce\": \"GTC\"
    }"

curl -X POST http://localhost:8080/v1/order \
    -H "Content-Type: application/json" \
    -H "X-Dev-User: test-user-22" \
    -d "{
      \"orderId\": \"$(uuidgen | tr '[:upper:]' '[:lower:]')\",
      \"symbol\": \"KXETHD-25JUN1312-T2009.99\",
      \"side\": \"SELL\",
      \"quantity\": 1,
      \"price\": 50,
      \"orderType\": \"LIMIT\",
      \"timeInForce\": \"GTC\"
    }"

PGPASSWORD=postgres psql -h localhost -p 5432 -U postgres trading
```

See in my portfolio
```
curl -X GET "http://localhost:8080/v1/portfolio?userId=demo-user&page=0&size=10"
```


```
PGPASSWORD=postgres psql -h localhost -p 5432 -U postgres trading
TRUNCATE TABLE order_projection RESTART IDENTITY CASCADE;
TRUNCATE TABLE event_journal RESTART IDENTITY CASCADE;
TRUNCATE TABLE snapshot RESTART IDENTITY CASCADE;
TRUNCATE TABLE durable_state RESTART IDENTITY CASCADE;
TRUNCATE TABLE projection_offset_store RESTART IDENTITY CASCADE;
TRUNCATE TABLE projection_timestamp_offset_store RESTART IDENTITY CASCADE;
TRUNCATE TABLE projection_management RESTART IDENTITY CASCADE;

TRUNCATE TABLE messages RESTART IDENTITY CASCADE;
TRUNCATE TABLE sessions RESTART IDENTITY CASCADE;


```

## TODOs

### High Level
  - Complete the work in positions.md
  - Order settlement
  - Tests
  - Identity and auth
  - Wallet integration, failure cases
  - Full wallet flow, once we have decided!
  - Secrets in SSM
  - Fancash earn at place time?  easy enough with a projection
  - Tidy up the fix gateway
  - Especially the sequence number logic, which is not working well at all

### Code TODOs by Location

#### Configuration & Infrastructure
- **DataSourceInitializerConfig.kt:12** - Find a less horrid fix
- **OrderController.kt:122** - Move timeout to config
- **OrderActor.kt:38** - Move FILL_TIMEOUT to config
- **FixGatewayActor.kt:351** - Move magic path to spring config
- **FixGatewayActor.kt:426** - Move accountID to config
- **deploy/base/helmrelease.yaml:6** - Change namespace when we have one created
- **AppReadinessHealthIndicator.java:18** - Check infrastructure instead of timestamp simulation

#### Business Logic
- **OrderActor.kt:121** - Implement order business logic
- **FixGatewayActor.kt:310** - Make the code generator work for UMS messages
- **FixGatewayActor.kt:315-325** - Complete position settlement workflow:
  - Mark orderactor as settled
  - Send message to kalshi wallet actor to debit user's account
  - Send message to fbg wallet actor to credit user's account
- **OpenPositionProcessManager.kt:316** - Calculate required collateral using cmd.netPosition and state.orderRequest
- **OpenPositionProcessManager.kt:555** - Implement partial compensation for cancelled remainder

#### Wallet Integration
- **FBGWalletActor.kt:19** - Actually call the wallet
- **KalshiWalletActor.kt:19** - Actually call the wallet

#### Error Handling & Recovery
- **FixGatewayActor.kt:265** - Decide how to handle new messages during recovery
- **FixGatewayActor.kt:381** - Optionally reply with status
- **OrderProjectionHandler.kt:20** - Be more defensive
- **OrderProjectionHandler.kt:50** - Handle other events

#### Testing & Monitoring
- **OrderControllerIntegrationTest.kt:71** - Remove gRPC properly since we aren't using it
- **OpenPositionProcessManagerTest.kt:692-700** - Implement compensation logic:
  - Full compensation for orders that receive no shares
  - Partial compensation logic for cancelled orders
  - Event-driven compensation that works when controller is gone
  - Monitoring/alerting for orders requiring manual compensation

#### Documentation
- **fix50sp2-extended.xml:3069** - Add more message definitions

## Deploy!
- get kalshi private key into SSM
- make the app consume private key from env var
- configure database for pekko
- configure database for springboot
- configure pekko clustering with k8s API