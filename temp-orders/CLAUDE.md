# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a FIX Gateway project that connects to the Kalshi exchange for trading binary options contracts. The system implements a sophisticated order management and execution platform using event-driven architecture with Apache Pekko actors.

### Key Technologies
- **Language**: Kotlin/Java (mixed)
- **Framework**: Spring Boot 3.4.0
- **Actor System**: Apache Pekko 1.1.3 (typed actors, persistence, cluster sharding)
- **FIX Protocol**: QuickFIX/J 3.0.0-SNAPSHOT
- **Database**: PostgreSQL with R2DBC for reactive access
- **Build Tool**: Maven
- **Java Version**: 21

## Common Development Commands

### Building the Project
```bash
# Full build with tests
./mvnw clean install

# Build without tests
./mvnw clean install -DskipTests

# Build with code coverage
./mvnw clean install -Dcodecoverage=true
```

### Running Tests
```bash
# Run all tests
./mvnw test

# Run a specific test class
./mvnw test -Dtest=OrderActorTest

# Run tests with a pattern
./mvnw test -Dtest=*ActorTest

# Run integration tests
./mvnw verify
```

### Running the Application Locally
```bash
# Start PostgreSQL (required)
docker run --rm -e POSTGRES_DB=trading -e POSTGRES_USER=postgres -e POSTGRES_PASSWORD=postgres -p 5432:5432 postgres

# Run single node
GRPC_SERVER_PORT=9090 SERVER_PORT=8080 PEKKO_HOST=127.0.0.1 PEKKO_PORT=2551 ./mvnw spring-boot:run -Dspring-boot.run.profiles=local

# Run cluster (2 nodes)
# Node 1:
GRPC_SERVER_PORT=9090 SERVER_PORT=8080 PEKKO_HOST=127.0.0.1 PEKKO_PORT=2551 ./mvnw spring-boot:run -Dspring-boot.run.profiles=local
# Node 2:
GRPC_SERVER_PORT=9091 SERVER_PORT=8080 PEKKO_HOST=127.0.0.1 PEKKO_PORT=2552 ./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

### Linting and Code Quality
```bash
# Run checkstyle (configured in checkstyle.xml)
./mvnw checkstyle:check

# Format OPA policies
make format

# Test deployment configurations
make test
```

## Architecture Overview

### Core Components

1. **FIX Gateway Integration**
   - `FixGatewayActor` (src/main/kotlin/com/betfanatics/exchange/order/actor/FixGatewayActor.kt): Cluster singleton managing FIX protocol connections
   - `QuickfixJApplication` (src/main/kotlin/com/betfanatics/exchange/order/actor/QuickfixJApplication.kt): QuickFIX/J application implementation
   - Configuration: `quickfixj-mock.cfg`, `quickfixj-dev.cfg` for different environments
   - FIX dictionaries: `kalshi-fix.xml`, `kalshi-FIXT11.xml`

2. **Actor System (Apache Pekko)**
   - **OrderActor**: Event-sourced actor managing individual order lifecycle
   - **PositionActor**: Tracks user positions per contract for margin calculations
   - **OpenPositionProcessManager**: Orchestrates order placement workflow (wallet → order → position)
   - **FBGWalletActor**: Interface to FBG wallet system
   - **KalshiWalletActor**: Interface to Kalshi wallet system

3. **REST API**
   - `OrderController` (src/main/kotlin/com/betfanatics/exchange/order/controller/OrderController.kt): Order placement endpoints
   - `PortfolioController`: Portfolio query endpoints
   - Uses Spring Security with OAuth2/JWT authentication

4. **Event Sourcing & Projections**
   - All actors use event sourcing for state persistence
   - `OrderProjection`: Read model for order data queries
   - R2DBC for reactive database access
   - PostgreSQL for both event journal and projections

### Workflow: Order Placement

1. User submits order via REST API
2. `OpenPositionProcessManager` queries `PositionActor` for current position
3. Calculates required collateral based on position
4. Transfers funds via wallet actors (FBG → Kalshi)
5. On successful transfer, creates order in `OrderActor`
6. `OrderActor` sends FIX message via `FixGatewayActor`
7. Fills/cancellations update both `OrderActor` and `PositionActor`

### Database Schema

Migration files in `src/main/resources/db/migration/`:
- V1: Initial schema for Pekko persistence
- V2: QuickFIX/J session tables
- V3-V6: Order projection tables and indexes

## Kalshi FIX API Integration

The system connects to Kalshi's FIX API for order management:
- **Protocol**: FIXT.1.1 with FIX 5.0 SP2 application layer
- **Authentication**: RSA signature-based (2048-bit keys)
- **Message Types**: NewOrderSingle, ExecutionReport, OrderCancelRequest, etc.
- **Market Settlement**: Handles UMS (User Market Settlement) messages

Key endpoints configured in `quickfixj-*.cfg`:
- Mock server: localhost:9878
- Production: fix-rt.elections.kalshi.com:8230

## Testing Strategy

- Unit tests for individual actors using Pekko TestKit
- Integration tests for controllers using MockMvc
- Testcontainers for PostgreSQL in tests
- H2 in-memory database for fast unit tests
- K6 performance tests in `src/test/k6/`

## Important Notes

1. **Sequence Number Management**: FIX sequence numbers reset daily at 2 AM ET. See `fixissues.md` for handling sequence mismatches.

2. **Actor Recovery**: All event-sourced actors can recover from crashes by replaying events from the journal.

3. **Wallet Integration**: Currently uses mock implementations. Real wallet calls are TODO items marked in code.

4. **Position Management**: Position tracking is implemented but margin calculations are still in development.

5. **WebSocket Market Data**: Plans to connect to Kalshi WebSocket for orderbook updates (not yet implemented).

## Configuration Files

- `application.yml`: Main Spring Boot configuration
- `application-local.yml`: Local development overrides
- `application.conf`: Pekko actor system configuration
- `bootstrap.yml`: Cloud configuration bootstrap

## Deployment

- Kubernetes-ready with Helm charts in `deploy/` directory
- Different environments: dev-1, test-1, cert-1, prod-1
- Uses Spring Cloud Kubernetes for config management
- Datadog integration for monitoring