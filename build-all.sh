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

# Build kalshi-fix-api first (Gradle)
echo ""
echo "Building kalshi-fix-api (Gradle)..."
cd kalshi-fix-api
./gradlew publishToMavenLocal -x test
print_status "kalshi-fix-api build"
cd ..

# Build all Maven modules
echo ""
echo "Building Maven modules..."
mvn clean install -DskipTests
print_status "Maven modules build"

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