# Kalshi Trading System - Docker Setup

This document describes how to run the complete Kalshi trading system using Docker Compose.

## Architecture

The system consists of the following services:

### Core Infrastructure
- **PostgreSQL**: Database for market data and order storage
- **Kafka**: Message streaming for order and market data events  
- **Zookeeper**: Kafka coordination service
- **Redis**: Caching and session storage

### Trading Services
- **mock-kalshi-fix**: Main trading engine with REST and FIX APIs, WebSocket market data
- **market-maker**: Automated market making service for MARKET_MAKER ticker
- **frontend**: React-based trading UI with real-time order book and market data

### Data Services  
- **market-data-server**: Market data aggregation and distribution
- **order-book-rebuilder**: Order book reconstruction from Kafka events

## Quick Start

1. **Create Docker network** (one-time setup):
   ```bash
   docker network create kalshi-network --driver bridge
   ```

2. **Start all services**:
   ```bash
   cd /path/to/kalshi-bet
   docker-compose up -d
   ```

3. **Check service health**:
   ```bash
   docker-compose ps
   ```

## Service Endpoints

| Service | Port | Description |
|---------|------|-------------|
| Frontend | 8080 | Trading UI (http://localhost:8080) |
| Mock Exchange | 9090 | REST API + WebSocket |
| Market Maker | 8888 | Health endpoint |
| Market Data Server | 8084 | Market data API |
| Order Book Rebuilder | 8085 | Order book API |
| PostgreSQL | 5433 | Database |
| Kafka | 9092 | Message broker |
| Redis | 6379 | Cache |

## Testing the System

1. **Access the Trading UI**: http://localhost:8080
2. **View Order Book**: Click on MARKET_MAKER to see real-time updates
3. **Check Market Maker**: http://localhost:8888/actuator/health
4. **REST API**: http://localhost:9090/trade-api/v2/markets

## Environment Configuration

### Market Maker Settings
- `MARKET_MAKER_TICKER`: Market symbol (default: MARKET_MAKER)
- `MARKET_MAKER_INTERVAL_MS`: Update frequency (default: 3000ms)
- `MARKET_MAKER_PRICE_MIN`: Minimum price (default: 33¢)
- `MARKET_MAKER_PRICE_MAX`: Maximum price (default: 67¢)
- `MARKET_MAKER_SPREAD`: Bid-ask spread (default: 10¢)
- `MARKET_MAKER_QUANTITY`: Order quantity (default: 100)

## Logs and Debugging

View service logs:
```bash
# All services
docker-compose logs -f

# Specific service
docker-compose logs -f mock-kalshi-fix
docker-compose logs -f market-maker
docker-compose logs -f frontend
```

## Stopping the System

```bash
# Stop all services
docker-compose down

# Stop and remove volumes (data loss!)
docker-compose down -v
```

## Delta Updates Feature

The system implements real-time WebSocket delta updates:
- **Frontend**: Receives and displays order book changes with visual indicators
- **Backend**: Sends delta updates every change, snapshots every 10th update  
- **Market Maker**: Generates continuous order book changes for testing
- **Performance**: Typically achieves 80-90% delta ratio vs snapshots

## Development

To rebuild services after code changes:
```bash
# Rebuild specific service
docker-compose build mock-kalshi-fix
docker-compose up -d mock-kalshi-fix

# Rebuild all
docker-compose build
docker-compose up -d
```