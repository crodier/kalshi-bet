#!/bin/bash

# Create the kalshi-network if it doesn't exist
docker network create kalshi-network 2>/dev/null || true

# Build services if needed
echo "Building services (if needed)..."
docker-compose build

# Start all infrastructure services
echo "Starting Kalshi infrastructure services..."
docker-compose up -d

# Wait for services to be healthy
echo "Waiting for services to be ready..."

# Check PostgreSQL
echo -n "Waiting for PostgreSQL..."
until docker exec kalshi-mock-exchange-postgres pg_isready -U kalshi -d kalshi_mock > /dev/null 2>&1; do
  echo -n "."
  sleep 1
done
echo " Ready!"

# Check Kafka
echo -n "Waiting for Kafka..."
until docker exec kalshi-kafka kafka-broker-api-versions --bootstrap-server localhost:9092 > /dev/null 2>&1; do
  echo -n "."
  sleep 1
done
echo " Ready!"

# Check Redis
echo -n "Waiting for Redis..."
until docker exec kalshi-redis redis-cli ping > /dev/null 2>&1; do
  echo -n "."
  sleep 1
done
echo " Ready!"

echo ""
echo "All infrastructure services are running!"
echo ""
echo "Service URLs:"
echo "  - PostgreSQL: localhost:5432 (user: kalshi, password: kalshi_dev_password, db: kalshi_mock)"
echo "  - Kafka: localhost:9092"
echo "  - Redis: localhost:6379"
echo "  - Zookeeper: localhost:2181"
echo ""
echo "To stop all services: docker-compose down"
echo "To view logs: docker-compose logs -f [service-name]"