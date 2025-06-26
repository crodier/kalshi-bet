#!/bin/bash

# Generate continuous stream of realistic market data for testing

echo "Starting continuous market data generation..."
echo "Press Ctrl+C to stop"

# Market tickers to simulate
tickers=("DUMMY_TEST" "MARKET_MAKER" "TRUMPWIN-24NOV05" "BTCZ-23DEC31-B50000" "INXD-23DEC29-B5000")

# Current sequence numbers for each market
declare -A sequences
sequences["DUMMY_TEST"]=1000
sequences["MARKET_MAKER"]=2000
sequences["TRUMPWIN-24NOV05"]=3000
sequences["BTCZ-23DEC31-B50000"]=4000
sequences["INXD-23DEC29-B5000"]=5000

# Current prices for each market (in cents)
declare -A prices
prices["DUMMY_TEST"]=4850
prices["MARKET_MAKER"]=5000
prices["TRUMPWIN-24NOV05"]=3900
prices["BTCZ-23DEC31-B50000"]=3025
prices["INXD-23DEC29-B5000"]=4750

# Function to get current timestamp in milliseconds
current_timestamp_ms() {
    echo $(($(date +%s) * 1000 + $(date +%N) / 1000000))
}

# Function to generate random price movement
random_price_change() {
    local current_price=$1
    local min_price=1000  # 10.00
    local max_price=9000  # 90.00
    
    # Random change between -200 and +200 cents
    local change=$((RANDOM % 401 - 200))
    local new_price=$((current_price + change))
    
    # Keep within bounds
    if [ $new_price -lt $min_price ]; then
        new_price=$min_price
    elif [ $new_price -gt $max_price ]; then
        new_price=$max_price
    fi
    
    echo $new_price
}

# Function to generate orderbook snapshot
generate_snapshot() {
    local ticker=$1
    local seq=$2
    local timestamp=$(current_timestamp_ms)
    local price=${prices[$ticker]}
    
    # Generate YES side (slightly below current price)
    local yes_price1=$((price - 50))
    local yes_price2=$((price - 100))
    local yes_qty1=$((RANDOM % 500 + 100))
    local yes_qty2=$((RANDOM % 300 + 200))
    
    # Generate NO side (slightly above complement price)
    local no_price1=$((10000 - price + 50))
    local no_price2=$((10000 - price + 100))
    local no_qty1=$((RANDOM % 400 + 150))
    local no_qty2=$((RANDOM % 250 + 300))
    
    echo "{\"payload\":{\"channel\":\"orderbook_snapshot\",\"market_ticker\":\"$ticker\",\"seq\":$seq,\"yes\":[[$yes_price1,$yes_qty1],[$yes_price2,$yes_qty2]],\"no\":[[$no_price1,$no_qty1],[$no_price2,$no_qty2]]},\"receivedTimestamp\":$timestamp,\"publishedTimestamp\":$((timestamp + RANDOM % 50 + 10)),\"channel\":\"orderbook_snapshot\",\"marketTicker\":\"$ticker\",\"sequence\":$seq,\"source\":\"kalshi-websocket\",\"version\":1}"
}

# Function to generate orderbook delta
generate_delta() {
    local ticker=$1
    local seq=$2
    local timestamp=$(current_timestamp_ms)
    local price=${prices[$ticker]}
    
    # Random side (yes or no)
    local sides=("yes" "no")
    local side=${sides[$((RANDOM % 2))]}
    
    # Random price near current price
    local delta_price
    if [ "$side" = "yes" ]; then
        delta_price=$((price + RANDOM % 100 - 50))
    else
        delta_price=$((10000 - price + RANDOM % 100 - 50))
    fi
    
    # Random quantity change (positive or negative)
    local delta_qty=$((RANDOM % 201 - 100))
    
    echo "{\"payload\":{\"channel\":\"orderbook_delta\",\"market_ticker\":\"$ticker\",\"seq\":$seq,\"price\":$delta_price,\"delta\":$delta_qty,\"side\":\"$side\"},\"receivedTimestamp\":$timestamp,\"publishedTimestamp\":$((timestamp + RANDOM % 30 + 5)),\"channel\":\"orderbook_delta\",\"marketTicker\":\"$ticker\",\"sequence\":$seq,\"source\":\"kalshi-websocket\",\"version\":1}"
}

# Function to generate ticker update
generate_ticker() {
    local ticker=$1
    local timestamp=$(current_timestamp_ms)
    local price=${prices[$ticker]}
    local volume=$((RANDOM % 10000 + 1000))
    
    echo "{\"payload\":{\"channel\":\"ticker\",\"market_ticker\":\"$ticker\",\"lastPrice\":$price,\"volume\":$volume},\"receivedTimestamp\":$timestamp,\"publishedTimestamp\":$((timestamp + RANDOM % 20 + 5)),\"channel\":\"ticker\",\"marketTicker\":\"$ticker\",\"sequence\":null,\"source\":\"kalshi-websocket\",\"version\":1}"
}

# Function to generate trade
generate_trade() {
    local ticker=$1
    local timestamp=$(current_timestamp_ms)
    local price=${prices[$ticker]}
    local size=$((RANDOM % 100 + 10))
    local sides=("yes" "no")
    local side=${sides[$((RANDOM % 2))]}
    
    echo "{\"payload\":{\"channel\":\"trade\",\"market_ticker\":\"$ticker\",\"price\":$price,\"size\":$size,\"side\":\"$side\"},\"receivedTimestamp\":$timestamp,\"publishedTimestamp\":$((timestamp + RANDOM % 15 + 3)),\"channel\":\"trade\",\"marketTicker\":\"$ticker\",\"sequence\":null,\"source\":\"kalshi-websocket\",\"version\":1}"
}

# Function to send message to Kafka
send_message() {
    local message="$1"
    echo "$message" | docker exec -i kalshi-kafka kafka-console-producer --bootstrap-server localhost:9092 --topic MARKET-DATA-ALL
}

# Main loop
counter=1
while true; do
    # Pick a random ticker
    ticker=${tickers[$((RANDOM % ${#tickers[@]}))]}
    
    # Increment sequence for this ticker
    sequences[$ticker]=$((sequences[$ticker] + 1))
    seq=${sequences[$ticker]}
    
    # Update price occasionally
    if [ $((RANDOM % 10)) -eq 0 ]; then
        prices[$ticker]=$(random_price_change ${prices[$ticker]})
    fi
    
    # Generate different types of messages randomly
    message_type=$((RANDOM % 100))
    if [ $message_type -lt 40 ]; then
        # 40% orderbook deltas
        message=$(generate_delta "$ticker" "$seq")
        echo "[$counter] Delta for $ticker (seq: $seq, price: ${prices[$ticker]})"
    elif [ $message_type -lt 60 ]; then
        # 20% orderbook snapshots
        message=$(generate_snapshot "$ticker" "$seq")
        echo "[$counter] Snapshot for $ticker (seq: $seq, price: ${prices[$ticker]})"
    elif [ $message_type -lt 80 ]; then
        # 20% ticker updates
        message=$(generate_ticker "$ticker")
        echo "[$counter] Ticker for $ticker (price: ${prices[$ticker]})"
    else
        # 20% trades
        message=$(generate_trade "$ticker")
        echo "[$counter] Trade for $ticker (price: ${prices[$ticker]})"
    fi
    
    # Send message
    send_message "$message"
    
    # Increment counter
    counter=$((counter + 1))
    
    # Random delay between 1-3 seconds
    sleep $((RANDOM % 3 + 1))
done