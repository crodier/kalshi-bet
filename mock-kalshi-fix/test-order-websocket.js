const WebSocket = require('ws');

const ws = new WebSocket('ws://localhost:9090/trade-api/ws/v2');

ws.on('open', function open() {
    console.log('Connected to WebSocket');
    
    // Subscribe to order updates for DUMMY_TEST market
    const subscribeMessage = {
        cmd: "subscribe",
        id: 1,
        params: {
            channels: ["orders"],
            market_tickers: ["DUMMY_TEST"]
        }
    };
    
    console.log('Sending subscription:', JSON.stringify(subscribeMessage));
    ws.send(JSON.stringify(subscribeMessage));
});

ws.on('message', function message(data) {
    const msg = JSON.parse(data.toString());
    console.log('Received message:', JSON.stringify(msg, null, 2));
});

ws.on('error', function error(err) {
    console.error('WebSocket error:', err);
});

ws.on('close', function close() {
    console.log('WebSocket connection closed');
});

// Keep the connection alive and create a new order after 2 seconds
setTimeout(() => {
    console.log('\n--- Creating new order to test real-time updates ---');
    
    const http = require('http');
    const postData = JSON.stringify({
        side: "yes",
        market_ticker: "DUMMY_TEST", 
        type: "limit",
        count: 3,
        price: 51,
        time_in_force: "GTC",
        action: "buy"
    });
    
    const options = {
        hostname: 'localhost',
        port: 9090,
        path: '/trade-api/v2/portfolio/orders',
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
            'Content-Length': Buffer.byteLength(postData)
        }
    };
    
    const req = http.request(options, (res) => {
        let data = '';
        res.on('data', (chunk) => data += chunk);
        res.on('end', () => {
            console.log('Order created:', JSON.parse(data));
        });
    });
    
    req.on('error', (e) => {
        console.error('Error creating order:', e);
    });
    
    req.write(postData);
    req.end();
}, 2000);

// Close after 10 seconds
setTimeout(() => {
    console.log('\n--- Closing WebSocket connection ---');
    ws.close();
    process.exit(0);
}, 10000);