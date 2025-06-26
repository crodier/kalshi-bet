#!/bin/bash

# Test WebSocket connection and subscribe command

echo "Testing WebSocket API..."

# Subscribe to orderbook_snapshot channel for test markets
curl -N \
  --http1.1 \
  --header "Connection: Upgrade" \
  --header "Upgrade: websocket" \
  --header "Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==" \
  --header "Sec-WebSocket-Version: 13" \
  http://localhost:9090/trade-api/ws/v2 \
  --data-binary '{"id":1,"cmd":"subscribe","params":{"channels":["orderbook_snapshot"],"market_tickers":["INXD-23DEC29-B5000","BTCZ-23DEC31-B50000"]}}'