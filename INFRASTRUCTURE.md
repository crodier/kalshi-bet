# Kalshi-Bet Infrastructure

This document describes the infrastructure services for the Kalshi-Bet project.

## Services

The project uses Docker Compose to manage the following services:

### Core Infrastructure
- **PostgreSQL** (mock-exchange-postgres): Database for the mock exchange
  - Port: 5432
  - Database: kalshi_mock
  - Username: kalshi
  - Password: kalshi_dev_password
  
- **Apache Kafka**: Message broker for event streaming
  - Port: 9092 (external), 29092 (internal)
  
- **Zookeeper**: Coordination service for Kafka
  - Port: 2181
  
- **Redis**: Cache and pub/sub for real-time data
  - Port: 6379

### Application Services
- **mock-kalshi-fix**: Mock Kalshi exchange with FIX protocol support
  - HTTP Port: 9090
  - FIX Port: 9878
  
- **market-data-server**: Real-time market data aggregation service
  - Port: 8084

## Getting Started

### Prerequisites
- Docker and Docker Compose installed
- At least 4GB of RAM available for Docker

### Starting All Services

```bash
# From the project root
./start-infrastructure.sh
```

This script will:
1. Create the Docker network (kalshi-network)
2. Build any services that need building
3. Start all services
4. Wait for them to be healthy
5. Display connection information

### Starting Individual Services

```bash
# Start only infrastructure services (DB, Kafka, Redis)
docker-compose up -d mock-exchange-postgres kafka zookeeper redis

# Start only the mock exchange
docker-compose up -d mock-kalshi-fix

# Start only the market data server
docker-compose up -d market-data-server
```

### Viewing Logs

```bash
# All services
docker-compose logs -f

# Specific service
docker-compose logs -f mock-exchange-postgres
docker-compose logs -f mock-kalshi-fix
```

### Stopping Services

```bash
# Stop all services
docker-compose down

# Stop and remove volumes (full cleanup)
docker-compose down -v
```

## Database Schema

The PostgreSQL database is automatically initialized with:
- Schema from `mock-kalshi-fix/src/main/resources/db/schema.sql`
- Bootstrap data from `mock-kalshi-fix/src/main/resources/data.sql`

Key tables include:
- `markets`: Market definitions
- `orders`: Order book entries
- `trades`: Executed trades
- `positions`: User positions
- `fills`: Order fills

The bootstrap data includes several test markets including:
- MARKET_MAKER: Used by the automated market maker
- DUMMY_TEST: General testing market
- Various prediction markets (INXD, BTCZ, TRUMPWIN)

## Network Configuration

All services communicate on the `kalshi-network` Docker network. Services can reference each other by their container names:
- `mock-exchange-postgres`
- `kafka`
- `redis`
- `mock-kalshi-fix`
- `market-data-server`

## Development Tips

### Connecting from Host Machine

When developing locally, use these connection strings:
- PostgreSQL: `jdbc:postgresql://localhost:5432/kalshi_mock`
- Kafka: `localhost:9092`
- Redis: `redis://localhost:6379`
- Mock Exchange API: `http://localhost:9090`
- Market Data API: `http://localhost:8084`

### Rebuilding After Code Changes

For Java services (mock-kalshi-fix, market-data-server):
```bash
# Build the JAR first
cd mock-kalshi-fix
mvn clean package -DskipTests

# Then rebuild the Docker image
cd ..
docker-compose build mock-kalshi-fix
docker-compose up -d mock-kalshi-fix
```

### Troubleshooting

1. **Services failing to start**: Check that the kalshi-network exists:
   ```bash
   docker network ls | grep kalshi
   ```

2. **Database connection issues**: Ensure PostgreSQL is healthy:
   ```bash
   docker-compose ps mock-exchange-postgres
   docker exec kalshi-mock-exchange-postgres pg_isready
   ```

3. **Kafka connection issues**: Check Kafka and Zookeeper are running:
   ```bash
   docker-compose logs kafka zookeeper
   ```

4. **Port conflicts**: Ensure ports 5432, 6379, 9092, 9090, 9878, and 8084 are not in use