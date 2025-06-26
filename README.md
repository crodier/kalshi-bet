# Kalshi Trading System

A multi-module financial trading system for FIX messaging and market data integration with Kalshi, a futures exchange for single event binary options.

## Project Structure

```
kalshi-bet/
├── kalshi-fix-api/        # Shared API module (Kotlin multiplatform, Gradle)
├── mock-kalshi-fix/       # Mock Kalshi exchange server (Spring Boot)
├── temp-orders/           # Production FIX bridge system (Spring Boot)
├── market-maker/          # Automated market making bot (Spring Boot)
├── quickfixj/             # Custom QuickFIX/J fork (built separately)
└── pom.xml                # Multi-module Maven build
```

## Prerequisites

- Java 17+
- Maven 3.8+
- Gradle 7.5+
- Docker & Docker Compose
- Node.js 16+ (for frontend)
- PostgreSQL (via Docker)

## Building the System

### Quick Build (All Modules)

```bash
./build-all.sh
```

This script will:
1. Build kalshi-fix-api with Gradle
2. Build all Maven modules (mock-kalshi-fix, temp-orders, market-maker)

### Manual Build

```bash
# Build kalshi-fix-api first
cd kalshi-fix-api
./gradlew publishToMavenLocal -x test
cd ..

# Build all Maven modules
mvn clean install -DskipTests

# Or build with kalshi-api profile
mvn clean install -Pwith-kalshi-api -DskipTests
```

### Building Individual Modules

```bash
# Mock Server
cd mock-kalshi-fix
mvn clean package -DskipTests

# Market Maker
cd market-maker
mvn clean package -DskipTests

# Temp Orders
cd temp-orders
mvn clean package -DskipTests
```

## Running the System

### 1. Start PostgreSQL Database

```bash
cd mock-kalshi-fix
docker-compose up -d postgres
```

### 2. Start Mock Kalshi Server

```bash
java -jar mock-kalshi-fix/target/mock-kalshi-fix-0.0.1-SNAPSHOT.jar
```

- REST API: http://localhost:9090
- WebSocket: ws://localhost:9090/ws
- FIX Port: 9878

### 3. Start Market Maker

```bash
java -jar market-maker/target/market-maker-1.0.0-SNAPSHOT.jar
```

- Port: 8888
- Creates and maintains liquidity for MARKET_MAKER market

### 4. Start Frontend (Optional)

```bash
cd mock-kalshi-fix/frontend/frontend
npm install  # First time only
npm start
```

- URL: http://localhost:5173

## Module Details

### kalshi-fix-api
- Shared DTOs and models
- Kotlin multiplatform module
- Built with Gradle, published to local Maven

### mock-kalshi-fix
- Simulates Kalshi exchange
- REST API, WebSocket, and FIX protocol support
- Order matching engine
- PostgreSQL for persistence

### market-maker
- Automated market making bot
- Maintains bid/ask spreads
- Oscillates prices within configured range
- REST API client to mock server

### temp-orders
- Production FIX bridge (in development)
- Connects to Kalshi via FIX protocol
- Pekko actors for order management
- Kafka integration

## Configuration

Each module has its own configuration in `src/main/resources/application.properties`:

- Mock Server: Port 9090, PostgreSQL connection
- Market Maker: Port 8888, mock server URL, trading parameters
- Temp Orders: FIX configuration, Kafka settings

## Development Notes

- QuickFIX/J is maintained as a separate fork and should be built manually
- The system uses Kalshi's buy-only architecture (all orders normalized to YES side)
- Market dynamics are documented in `mock-kalshi-fix/MARKET_DYNAMICS.md`
- Project instructions are in `CLAUDE.md`

## Testing

```bash
# Run all tests
mvn test

# Run specific module tests
cd mock-kalshi-fix
mvn test
```

## Troubleshooting

1. **Port conflicts**: Check that ports 5432, 8888, 9090, 9878 are available
2. **Database connection**: Ensure PostgreSQL container is running
3. **Build failures**: Build kalshi-fix-api before other modules
4. **Frontend issues**: Check Node.js version and run `npm install`