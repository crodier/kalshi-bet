#!/bin/bash
# Get series with optional filters
# Usage: ./get_series.sh [limit] [cursor] [category]

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
        QUERY_PARAMS="${QUERY_PARAMS}&category=$3"
    else
        QUERY_PARAMS="category=$3"
    fi
fi

# Build URL
URL="${BASE_URL}/series"
if [ ! -z "$QUERY_PARAMS" ]; then
    URL="${URL}?${QUERY_PARAMS}"
fi

echo "Fetching series from: $URL"
curl -s "$URL" | python3 -m json.tool