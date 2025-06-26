#!/bin/bash
# Get markets with optional filters
# Usage: ./get_markets.sh [limit] [cursor] [event_ticker] [series_ticker] [status]

BASE_URL="http://localhost:9090/trade-api/v2"

# Build query parameters
QUERY_PARAMS=""
if [ ! -z "$1" ]; then
    QUERY_PARAMS="limit=$1"
fi
if [ ! -z "$2" ]; then
    if [ ! -z "$QUERY_PARAMS" ]; then
        QUERY_PARAMS="${QUERY_PARAMS}&cursor=$2"
    else
        QUERY_PARAMS="cursor=$2"
    fi
fi
if [ ! -z "$3" ]; then
    if [ ! -z "$QUERY_PARAMS" ]; then
        QUERY_PARAMS="${QUERY_PARAMS}&event_ticker=$3"
    else
        QUERY_PARAMS="event_ticker=$3"
    fi
fi
if [ ! -z "$4" ]; then
    if [ ! -z "$QUERY_PARAMS" ]; then
        QUERY_PARAMS="${QUERY_PARAMS}&series_ticker=$4"
    else
        QUERY_PARAMS="series_ticker=$4"
    fi
fi
if [ ! -z "$5" ]; then
    if [ ! -z "$QUERY_PARAMS" ]; then
        QUERY_PARAMS="${QUERY_PARAMS}&status=$5"
    else
        QUERY_PARAMS="status=$5"
    fi
fi

# Build URL
URL="${BASE_URL}/markets"
if [ ! -z "$QUERY_PARAMS" ]; then
    URL="${URL}?${QUERY_PARAMS}"
fi

echo "Fetching markets from: $URL"
curl -s "$URL" | python3 -m json.tool