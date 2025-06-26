#!/bin/bash

echo "🚀 Starting Kalshi Trading System..."

# Create network if it doesn't exist
if ! docker network ls | grep -q kalshi-network; then
    echo "📡 Creating Docker network..."
    docker network create kalshi-network --driver bridge
fi

# Start all services
echo "🔧 Starting all services..."
docker-compose up -d

# Wait for services to be ready
echo "⏳ Waiting for services to start..."
sleep 10

# Check service health
echo "🏥 Checking service health..."
docker-compose ps

echo ""
echo "✅ Kalshi Trading System is starting up!"
echo ""
echo "🌐 Frontend:        http://localhost:8080"
echo "🔧 Mock Exchange:   http://localhost:9090"
echo "📊 Market Maker:    http://localhost:8888"
echo "📈 Market Data:     http://localhost:8084"
echo ""
echo "📋 Use 'docker-compose logs -f' to view logs"
echo "🛑 Use 'docker-compose down' to stop the system"