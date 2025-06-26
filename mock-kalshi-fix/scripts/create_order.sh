#!/bin/bash
# Create a new order
# Usage: ./create_order.sh <market_ticker> <side> <action> <type> <count> <price>

if [ $# -lt 5 ]; then
    echo "Usage: $0 <market_ticker> <side> <action> <type> <count> [price]"
    echo "  market_ticker: Market to trade (e.g., DUMMY_TEST)"
    echo "  side: yes or no"
    echo "  action: buy or sell"
    echo "  type: limit or market"
    echo "  count: Number of contracts"
    echo "  price: Price in cents (required for limit orders)"
    echo ""
    echo "Examples:"
    echo "  $0 DUMMY_TEST yes buy limit 100 65"
    echo "  $0 DUMMY_TEST no sell market 50"
    exit 1
fi

BASE_URL="http://localhost:9090/trade-api/v2"
API_KEY="${KALSHI_ACCESS_KEY:-demo-api-key}"

MARKET_TICKER="$1"
SIDE="$2"
ACTION="$3"
TYPE="$4"
COUNT="$5"
PRICE="$6"

# Build JSON payload
JSON_PAYLOAD="{"
JSON_PAYLOAD="\"market_ticker\": \"$MARKET_TICKER\","
JSON_PAYLOAD="${JSON_PAYLOAD} \"side\": \"$SIDE\","
JSON_PAYLOAD="${JSON_PAYLOAD} \"action\": \"$ACTION\","
JSON_PAYLOAD="${JSON_PAYLOAD} \"type\": \"$TYPE\","
JSON_PAYLOAD="${JSON_PAYLOAD} \"count\": $COUNT"

if [ ! -z "$PRICE" ] && [ "$TYPE" = "limit" ]; then
    JSON_PAYLOAD="${JSON_PAYLOAD}, \"price\": $PRICE"
fi

JSON_PAYLOAD="${JSON_PAYLOAD} }"

echo "Creating order with payload: $JSON_PAYLOAD"
curl -s -X POST \
    -H "Content-Type: application/json" \
    -H "KALSHI-ACCESS-KEY: $API_KEY" \
    -d "$JSON_PAYLOAD" \
    "${BASE_URL}/portfolio/orders" | python3 -m json.tool