#!/bin/bash
# Get user positions
# Usage: ./get_positions.sh

BASE_URL="http://localhost:9090/trade-api/v2"
API_KEY="${KALSHI_ACCESS_KEY:-demo-api-key}"

URL="${BASE_URL}/portfolio/positions"

echo "Fetching positions from: $URL"
curl -s -H "KALSHI-ACCESS-KEY: $API_KEY" "$URL" | python3 -m json.tool