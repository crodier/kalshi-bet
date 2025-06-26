const WebSocket = require('ws');

const ws = new WebSocket('ws://localhost:9090/trade-api/ws/v2');

ws.on('open', function open() {
  console.log('Connected to WebSocket');
  
  // Send subscribe command
  const subscribeCmd = {
    id: 1,
    cmd: "subscribe",
    params: {
      channels: ["orderbook_snapshot", "orderbook_delta", "ticker"],
      market_tickers: ["INXD-23DEC29-B5000", "BTCZ-23DEC31-B50000"]
    }
  };
  
  console.log('Sending:', JSON.stringify(subscribeCmd, null, 2));
  ws.send(JSON.stringify(subscribeCmd));
});

ws.on('message', function message(data) {
  console.log('Received:', data.toString());
  try {
    const msg = JSON.parse(data);
    console.log('Parsed:', JSON.stringify(msg, null, 2));
  } catch (e) {
    console.error('Failed to parse message');
  }
});

ws.on('error', function error(err) {
  console.error('WebSocket error:', err);
});

ws.on('close', function close() {
  console.log('Connection closed');
});

// Keep the script running
setInterval(() => {}, 1000);