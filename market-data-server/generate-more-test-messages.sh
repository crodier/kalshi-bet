#!/bin/bash

# Generate additional realistic test messages with varying timestamps

echo "Generating additional test market data messages..."

# Get current timestamp in milliseconds
current_time=$(date +%s)
current_time_ms=$((current_time * 1000))

# Function to generate timestamp with offset
gen_timestamp() {
    local offset_seconds=$1
    echo $((current_time_ms + (offset_seconds * 1000)))
}

# Generate more realistic messages with current timestamps
generate_message_batch() {
    local batch_id=$1
    local base_time_offset=$((batch_id * 10))
    
    messages=(
        "{\"payload\":{\"channel\":\"orderbook_snapshot\",\"market_ticker\":\"BTCZ-23DEC31-B50000\",\"seq\":$((batch_id * 10 + 1)),\"yes\":[[3000,200],[2950,300]],\"no\":[[7000,180],[7050,250]]},\"receivedTimestamp\":$(gen_timestamp $((base_time_offset + 1))),\"publishedTimestamp\":$(gen_timestamp $((base_time_offset + 1))),\"channel\":\"orderbook_snapshot\",\"marketTicker\":\"BTCZ-23DEC31-B50000\",\"sequence\":$((batch_id * 10 + 1)),\"source\":\"kalshi-websocket\",\"version\":1}"
        "{\"payload\":{\"channel\":\"orderbook_delta\",\"market_ticker\":\"BTCZ-23DEC31-B50000\",\"seq\":$((batch_id * 10 + 2)),\"price\":3000,\"delta\":25,\"side\":\"yes\"},\"receivedTimestamp\":$(gen_timestamp $((base_time_offset + 2))),\"publishedTimestamp\":$(gen_timestamp $((base_time_offset + 2))),\"channel\":\"orderbook_delta\",\"marketTicker\":\"BTCZ-23DEC31-B50000\",\"sequence\":$((batch_id * 10 + 2)),\"source\":\"kalshi-websocket\",\"version\":1}"
        "{\"payload\":{\"channel\":\"ticker\",\"market_ticker\":\"BTCZ-23DEC31-B50000\",\"lastPrice\":3025,\"volume\":2500},\"receivedTimestamp\":$(gen_timestamp $((base_time_offset + 3))),\"publishedTimestamp\":$(gen_timestamp $((base_time_offset + 3))),\"channel\":\"ticker\",\"marketTicker\":\"BTCZ-23DEC31-B50000\",\"sequence\":null,\"source\":\"kalshi-websocket\",\"version\":1}"
        "{\"payload\":{\"channel\":\"orderbook_snapshot\",\"market_ticker\":\"INXD-23DEC29-B5000\",\"seq\":$((batch_id * 10 + 4)),\"yes\":[[4500,150],[4450,200]],\"no\":[[5500,120],[5550,180]]},\"receivedTimestamp\":$(gen_timestamp $((base_time_offset + 4))),\"publishedTimestamp\":$(gen_timestamp $((base_time_offset + 4))),\"channel\":\"orderbook_snapshot\",\"marketTicker\":\"INXD-23DEC29-B5000\",\"sequence\":$((batch_id * 10 + 4)),\"source\":\"kalshi-websocket\",\"version\":1}"
        "{\"payload\":{\"channel\":\"orderbook_delta\",\"market_ticker\":\"INXD-23DEC29-B5000\",\"seq\":$((batch_id * 10 + 5)),\"price\":5500,\"delta\":-30,\"side\":\"no\"},\"receivedTimestamp\":$(gen_timestamp $((base_time_offset + 5))),\"publishedTimestamp\":$(gen_timestamp $((base_time_offset + 5))),\"channel\":\"orderbook_delta\",\"marketTicker\":\"INXD-23DEC29-B5000\",\"sequence\":$((batch_id * 10 + 5)),\"source\":\"kalshi-websocket\",\"version\":1}"
        "{\"payload\":{\"channel\":\"trade\",\"market_ticker\":\"INXD-23DEC29-B5000\",\"price\":4500,\"size\":75,\"side\":\"yes\"},\"receivedTimestamp\":$(gen_timestamp $((base_time_offset + 6))),\"publishedTimestamp\":$(gen_timestamp $((base_time_offset + 6))),\"channel\":\"trade\",\"marketTicker\":\"INXD-23DEC29-B5000\",\"sequence\":null,\"source\":\"kalshi-websocket\",\"version\":1}"
        "{\"payload\":{\"channel\":\"orderbook_delta\",\"market_ticker\":\"DUMMY_TEST\",\"seq\":$((batch_id * 10 + 7)),\"price\":4800,\"delta\":10,\"side\":\"yes\"},\"receivedTimestamp\":$(gen_timestamp $((base_time_offset + 7))),\"publishedTimestamp\":$(gen_timestamp $((base_time_offset + 7))),\"channel\":\"orderbook_delta\",\"marketTicker\":\"DUMMY_TEST\",\"sequence\":$((batch_id * 10 + 7)),\"source\":\"kalshi-websocket\",\"version\":1}"
        "{\"payload\":{\"channel\":\"orderbook_delta\",\"market_ticker\":\"MARKET_MAKER\",\"seq\":$((batch_id * 10 + 8)),\"price\":4500,\"delta\":-15,\"side\":\"yes\"},\"receivedTimestamp\":$(gen_timestamp $((base_time_offset + 8))),\"publishedTimestamp\":$(gen_timestamp $((base_time_offset + 8))),\"channel\":\"orderbook_delta\",\"marketTicker\":\"MARKET_MAKER\",\"sequence\":$((batch_id * 10 + 8)),\"source\":\"kalshi-websocket\",\"version\":1}"
        "{\"payload\":{\"channel\":\"ticker\",\"market_ticker\":\"TRUMPWIN-24NOV05\",\"lastPrice\":3900,\"volume\":55000},\"receivedTimestamp\":$(gen_timestamp $((base_time_offset + 9))),\"publishedTimestamp\":$(gen_timestamp $((base_time_offset + 9))),\"channel\":\"ticker\",\"marketTicker\":\"TRUMPWIN-24NOV05\",\"sequence\":null,\"source\":\"kalshi-websocket\",\"version\":1}"
        "{\"payload\":{\"channel\":\"orderbook_delta\",\"market_ticker\":\"TRUMPWIN-24NOV05\",\"seq\":$((batch_id * 10 + 10)),\"price\":3800,\"delta\":50,\"side\":\"yes\"},\"receivedTimestamp\":$(gen_timestamp $((base_time_offset + 10))),\"publishedTimestamp\":$(gen_timestamp $((base_time_offset + 10))),\"channel\":\"orderbook_delta\",\"marketTicker\":\"TRUMPWIN-24NOV05\",\"sequence\":$((batch_id * 10 + 10)),\"source\":\"kalshi-websocket\",\"version\":1}"
    )
    
    # Send all messages in this batch
    for i in "${!messages[@]}"; do
        local message="${messages[$i]}"
        local market_ticker=$(echo "$message" | grep -o '"marketTicker":"[^"]*"' | cut -d'"' -f4)
        
        echo "[Batch $batch_id - $((i+1))/${#messages[@]}] Sending message for $market_ticker..."
        echo "$message" | docker exec -i kalshi-kafka kafka-console-producer --bootstrap-server localhost:9092 --topic MARKET-DATA-ALL
        
        # Small delay between messages
        sleep 1
    done
}

# Generate multiple batches of messages
echo "Generating Batch 1..."
generate_message_batch 1

echo ""
echo "Waiting 5 seconds before next batch..."
sleep 5

echo "Generating Batch 2..."
generate_message_batch 2

echo ""
echo "Waiting 5 seconds before next batch..."
sleep 5

echo "Generating Batch 3..."
generate_message_batch 3

echo ""
echo "Done! Generated 30 additional test messages (3 batches of 10)."
echo ""
echo "Total messages now in topic:"
docker exec kalshi-kafka kafka-run-class kafka.tools.GetOffsetShell --broker-list localhost:9092 --topic MARKET-DATA-ALL