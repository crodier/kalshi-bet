#!/bin/bash
# Cancel an order
# Usage: ./cancel_order.sh <order_id>

if [ -z "$1" ]; then
    echo "Usage: $0 <order_id>"
    echo "Example: $0 ORD-1001"
    exit 1
fi

BASE_URL="http://localhost:9090/trade-api/v2"
API_KEY="${KALSHI_ACCESS_KEY:-demo-api-key}"
ORDER_ID="$1"

URL="${BASE_URL}/portfolio/orders/${ORDER_ID}"

echo "Canceling order: $ORDER_ID"
curl -s -X DELETE \
    -H "KALSHI-ACCESS-KEY: $API_KEY" \
    "$URL" | python3 -m json.tool