#!/bin/bash
# Get user fills with optional filters
# Usage: ./get_fills.sh [ticker] [order_id] [limit]

BASE_URL="http://localhost:9090/trade-api/v2"
API_KEY="${KALSHI_ACCESS_KEY:-demo-api-key}"

# Build query parameters
QUERY_PARAMS=""
if [ ! -z "$1" ]; then
    QUERY_PARAMS="ticker=$1"
fi
if [ ! -z "$2" ]; then
    if [ ! -z "$QUERY_PARAMS" ]; then
        QUERY_PARAMS="${QUERY_PARAMS}&order_id=$2"
    else
        QUERY_PARAMS="order_id=$2"
    fi
fi
if [ ! -z "$3" ]; then
    if [ ! -z "$QUERY_PARAMS" ]; then
        QUERY_PARAMS="${QUERY_PARAMS}&limit=$3"
    else
        QUERY_PARAMS="limit=$3"
    fi
fi

# Build URL
URL="${BASE_URL}/portfolio/fills"
if [ ! -z "$QUERY_PARAMS" ]; then
    URL="${URL}?${QUERY_PARAMS}"
fi

echo "Fetching fills from: $URL"
curl -s -H "KALSHI-ACCESS-KEY: $API_KEY" "$URL" | python3 -m json.tool