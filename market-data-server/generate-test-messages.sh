#!/bin/bash

# Generate test messages for market data topic

echo "Generating test market data messages..."

# Sample market data messages in the format the market data server expects
messages=(
    '{"payload":{"channel":"orderbook_snapshot","market_ticker":"DUMMY_TEST","seq":1,"yes":[[4800,100],[4700,200]],"no":[[5200,150],[5300,300]]},"receivedTimestamp":1735232548000,"publishedTimestamp":1735232548100,"channel":"orderbook_snapshot","marketTicker":"DUMMY_TEST","sequence":1,"source":"kalshi-websocket","version":1}'
    '{"payload":{"channel":"orderbook_delta","market_ticker":"DUMMY_TEST","seq":2,"price":4800,"delta":50,"side":"yes"},"receivedTimestamp":1735232549000,"publishedTimestamp":1735232549100,"channel":"orderbook_delta","marketTicker":"DUMMY_TEST","sequence":2,"source":"kalshi-websocket","version":1}'
    '{"payload":{"channel":"orderbook_delta","market_ticker":"DUMMY_TEST","seq":3,"price":5200,"delta":-25,"side":"no"},"receivedTimestamp":1735232550000,"publishedTimestamp":1735232550100,"channel":"orderbook_delta","marketTicker":"DUMMY_TEST","sequence":3,"source":"kalshi-websocket","version":1}'
    '{"payload":{"channel":"orderbook_snapshot","market_ticker":"MARKET_MAKER","seq":10,"yes":[[4500,100],[4400,200]],"no":[[5500,150],[5600,300]]},"receivedTimestamp":1735232551000,"publishedTimestamp":1735232551100,"channel":"orderbook_snapshot","marketTicker":"MARKET_MAKER","sequence":10,"source":"kalshi-websocket","version":1}'
    '{"payload":{"channel":"orderbook_delta","market_ticker":"MARKET_MAKER","seq":11,"price":4500,"delta":25,"side":"yes"},"receivedTimestamp":1735232552000,"publishedTimestamp":1735232552100,"channel":"orderbook_delta","marketTicker":"MARKET_MAKER","sequence":11,"source":"kalshi-websocket","version":1}'
    '{"payload":{"channel":"ticker","market_ticker":"DUMMY_TEST","lastPrice":4850,"volume":1500},"receivedTimestamp":1735232553000,"publishedTimestamp":1735232553100,"channel":"ticker","marketTicker":"DUMMY_TEST","sequence":null,"source":"kalshi-websocket","version":1}'
    '{"payload":{"channel":"trade","market_ticker":"DUMMY_TEST","price":4850,"size":50,"side":"yes"},"receivedTimestamp":1735232554000,"publishedTimestamp":1735232554100,"channel":"trade","marketTicker":"DUMMY_TEST","sequence":null,"source":"kalshi-websocket","version":1}'
    '{"payload":{"channel":"orderbook_snapshot","market_ticker":"TRUMPWIN-24NOV05","seq":100,"yes":[[3800,500],[3700,1000]],"no":[[6200,400],[6300,800]]},"receivedTimestamp":1735232555000,"publishedTimestamp":1735232555100,"channel":"orderbook_snapshot","marketTicker":"TRUMPWIN-24NOV05","sequence":100,"source":"kalshi-websocket","version":1}'
    '{"payload":{"channel":"orderbook_delta","market_ticker":"TRUMPWIN-24NOV05","seq":101,"price":3800,"delta":100,"side":"yes"},"receivedTimestamp":1735232556000,"publishedTimestamp":1735232556100,"channel":"orderbook_delta","marketTicker":"TRUMPWIN-24NOV05","sequence":101,"source":"kalshi-websocket","version":1}'
    '{"payload":{"channel":"orderbook_delta","market_ticker":"TRUMPWIN-24NOV05","seq":102,"price":6200,"delta":-50,"side":"no"},"receivedTimestamp":1735232557000,"publishedTimestamp":1735232557100,"channel":"orderbook_delta","marketTicker":"TRUMPWIN-24NOV05","sequence":102,"source":"kalshi-websocket","version":1}'
)

# Function to send message to Kafka
send_message() {
    local message="$1"
    local market_ticker="$2"
    echo "Sending message for $market_ticker..."
    echo "$message" | docker exec -i kalshi-kafka kafka-console-producer --bootstrap-server localhost:9092 --topic MARKET-DATA-ALL --property "key.separator=:" --property "parse.key=true" <<< "$market_ticker:$message"
}

# Send all messages with small delays
for i in "${!messages[@]}"; do
    message="${messages[$i]}"
    # Extract market ticker from message
    market_ticker=$(echo "$message" | grep -o '"marketTicker":"[^"]*"' | cut -d'"' -f4)
    
    echo "[$((i+1))/${#messages[@]}] Sending message to MARKET-DATA-ALL topic (key: $market_ticker)..."
    echo "$message" | docker exec -i kalshi-kafka kafka-console-producer --bootstrap-server localhost:9092 --topic MARKET-DATA-ALL
    
    # Small delay between messages
    sleep 2
done

echo "Done! Generated ${#messages[@]} test messages."
echo ""
echo "To consume messages from the topic, run:"
echo "docker exec kalshi-kafka kafka-console-consumer --bootstrap-server localhost:9092 --topic MARKET-DATA-ALL --from-beginning"