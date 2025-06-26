const WebSocket = require('ws');

console.log('=== Testing WebSocket Connections ===\n');

// Test orderbook WebSocket
const ws1 = new WebSocket('ws://localhost:9090/trade-api/ws/v2');

ws1.on('open', function open() {
    console.log('✓ Connected to main WebSocket');
    
    // Subscribe to orderbook and orders
    const subscribeMessage = {
        cmd: "subscribe",
        id: 1,
        params: {
            channels: ["orderbook_snapshot", "orders"],
            market_tickers: ["DUMMY_TEST"]
        }
    };
    
    console.log('→ Sending subscription:', JSON.stringify(subscribeMessage));
    ws1.send(JSON.stringify(subscribeMessage));
});

ws1.on('message', function message(data) {
    const msg = JSON.parse(data.toString());
    console.log('← Received:', JSON.stringify(msg, null, 2).substring(0, 200) + '...');
});

ws1.on('error', function error(err) {
    console.error('✗ WebSocket error:', err.message);
});

// Test internal orders WebSocket (if available)
setTimeout(() => {
    console.log('\n=== Testing Internal Orders WebSocket ===');
    const ws2 = new WebSocket('ws://localhost:9090/trade-api/ws/internal-orders');
    
    ws2.on('open', function open() {
        console.log('✓ Connected to internal orders WebSocket');
        
        const subscribeMessage = {
            cmd: "subscribe",
            id: 2,
            params: {
                channels: ["orders"],
                market_tickers: ["DUMMY_TEST"]
            }
        };
        
        console.log('→ Sending subscription:', JSON.stringify(subscribeMessage));
        ws2.send(JSON.stringify(subscribeMessage));
    });
    
    ws2.on('message', function message(data) {
        const msg = JSON.parse(data.toString());
        console.log('← Received on internal:', JSON.stringify(msg, null, 2));
    });
    
    ws2.on('error', function error(err) {
        console.error('✗ Internal orders WebSocket error:', err.message);
    });
}, 1000);

// Close after 5 seconds
setTimeout(() => {
    console.log('\n=== Closing connections ===');
    ws1.close();
    process.exit(0);
}, 5000);