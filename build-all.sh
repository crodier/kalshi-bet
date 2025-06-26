#!/bin/bash

# Build script for Kalshi Trading System
# This builds all modules except QuickFIX/J

set -e

echo "========================================="
echo "Building Kalshi Trading System"
echo "========================================="

# Colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
NC='\033[0m' # No Color

# Function to print status
print_status() {
    if [ $? -eq 0 ]; then
        echo -e "${GREEN}✓ $1 succeeded${NC}"
    else
        echo -e "${RED}✗ $1 failed${NC}"
        exit 1
    fi
}

# Build all Maven modules (including kalshi-fix-api)
echo ""
echo "Building all modules with Maven..."
mvn clean install -DskipTests
print_status "Maven build"

echo ""
echo "========================================="
echo -e "${GREEN}Build completed successfully!${NC}"
echo "========================================="
echo ""
echo "To run the system:"
echo "1. Start PostgreSQL: docker-compose -f mock-kalshi-fix/docker-compose.yml up -d postgres"
echo "2. Start Mock Server: java -jar mock-kalshi-fix/target/mock-kalshi-fix-*.jar"
echo "3. Start Market Maker: java -jar market-maker/target/market-maker-*.jar"
echo "4. Start Frontend: cd mock-kalshi-fix/frontend/frontend && npm start"
echo ""