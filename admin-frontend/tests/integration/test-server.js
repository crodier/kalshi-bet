import { WebSocketServer } from 'ws';
import { v4 as uuidv4 } from 'uuid';

class TestWebSocketServer {
  constructor(port) {
    this.port = port;
    this.server = null;
    this.clients = new Set();
    this.sequenceNumber = 1;
    this.intervalId = null;
  }

  start() {
    this.server = new WebSocketServer({ port: this.port });
    console.log(`Test WebSocket server started on port ${this.port}`);

    this.server.on('connection', (ws) => {
      console.log(`Client connected to port ${this.port}`);
      this.clients.add(ws);

      // Send welcome message
      this.sendToClient(ws, {
        type: 'connection',
        message: 'Connected to test server',
        port: this.port
      });

      ws.on('message', (data) => {
        try {
          const message = JSON.parse(data.toString());
          this.handleClientMessage(ws, message);
        } catch (e) {
          console.log('Received non-JSON message:', data.toString());
        }
      });

      ws.on('close', () => {
        console.log(`Client disconnected from port ${this.port}`);
        this.clients.delete(ws);
      });

      ws.on('error', (error) => {
        console.error(`WebSocket error on port ${this.port}:`, error);
        this.clients.delete(ws);
      });
    });

    // Start sending periodic test messages
    this.startPeriodicMessages();
  }

  handleClientMessage(ws, message) {
    console.log(`Received message on port ${this.port}:`, message);
    
    if (message.cmd === 'subscribe') {
      this.sendToClient(ws, {
        type: 'subscription',
        id: message.id,
        status: 'success',
        sids: [`sub_${message.id}_orderbook_snapshot`, `sub_${message.id}_orderbook_delta`]
      });
      
      // Send initial snapshot
      setTimeout(() => {
        this.sendOrderBookSnapshot(ws);
      }, 100);
    }
  }

  sendToClient(ws, message) {
    if (ws.readyState === ws.OPEN) {
      ws.send(JSON.stringify(message));
    }
  }

  broadcast(message) {
    this.clients.forEach(client => {
      this.sendToClient(client, message);
    });
  }

  sendOrderBookSnapshot(ws = null) {
    const message = {
      type: 'orderbook_snapshot',
      seq: this.sequenceNumber++,
      msg: {
        market_ticker: 'TEST_MARKET',
        yes: [
          [50 + Math.floor(Math.random() * 10), 100 + Math.floor(Math.random() * 50)],
          [49 + Math.floor(Math.random() * 10), 150 + Math.floor(Math.random() * 50)],
          [48 + Math.floor(Math.random() * 10), 200 + Math.floor(Math.random() * 50)]
        ],
        no: [
          [51 + Math.floor(Math.random() * 10), 120 + Math.floor(Math.random() * 50)],
          [52 + Math.floor(Math.random() * 10), 180 + Math.floor(Math.random() * 50)],
          [53 + Math.floor(Math.random() * 10), 250 + Math.floor(Math.random() * 50)]
        ]
      }
    };

    if (ws) {
      this.sendToClient(ws, message);
    } else {
      this.broadcast(message);
    }
  }

  sendOrderBookDelta() {
    const message = {
      type: 'orderbook_delta',
      seq: this.sequenceNumber++,
      msg: {
        market_ticker: 'TEST_MARKET',
        price: 48 + Math.floor(Math.random() * 6),
        delta: Math.floor(Math.random() * 50) - 25, // -25 to +25
        side: Math.random() > 0.5 ? 'yes' : 'no'
      }
    };

    this.broadcast(message);
  }

  sendTradeMessage() {
    const message = {
      type: 'trade',
      seq: this.sequenceNumber++,
      msg: {
        market_ticker: 'TEST_MARKET',
        price: 48 + Math.floor(Math.random() * 6),
        count: 10 + Math.floor(Math.random() * 90),
        side: Math.random() > 0.5 ? 'yes' : 'no',
        created_time: new Date().toISOString(),
        trade_id: `trade_${uuidv4()}`
      }
    };

    this.broadcast(message);
  }

  sendMarketDataMessage() {
    const message = {
      type: 'market-data',
      payload: {
        marketTicker: 'TEST_MARKET',
        bestBid: 48 + Math.floor(Math.random() * 4),
        bestAsk: 52 + Math.floor(Math.random() * 4),
        lastPrice: 50 + Math.floor(Math.random() * 4) - 2,
        volume: 1000 + Math.floor(Math.random() * 500),
        timestamp: Date.now()
      }
    };

    this.broadcast(message);
  }

  sendSystemStatsMessage() {
    const message = {
      type: 'system-stats',
      payload: {
        totalMarkets: 150 + Math.floor(Math.random() * 50),
        activeConnections: this.clients.size,
        messagesPerSecond: 40 + Math.random() * 20,
        uptimeSeconds: Math.floor(Date.now() / 1000) % 86400,
        memoryUsage: {
          used: `${500 + Math.floor(Math.random() * 100)}MB`,
          max: '2GB'
        }
      }
    };

    this.broadcast(message);
  }

  sendExecutionReport() {
    const message = {
      type: 'execution_report',
      market_ticker: 'TEST_MARKET',
      action: Math.random() > 0.5 ? 'BUY' : 'SELL',
      side: Math.random() > 0.5 ? 'YES' : 'NO',
      price: 48 + Math.floor(Math.random() * 6),
      quantity: 10 + Math.floor(Math.random() * 90),
      timestamp: Date.now()
    };

    this.broadcast(message);
  }

  startPeriodicMessages() {
    let messageCount = 0;
    
    this.intervalId = setInterval(() => {
      messageCount++;
      
      // Send different types of messages in sequence
      switch (messageCount % 10) {
        case 0:
          this.sendOrderBookSnapshot();
          break;
        case 1:
        case 2:
        case 3:
          this.sendOrderBookDelta();
          break;
        case 4:
          this.sendTradeMessage();
          break;
        case 5:
          this.sendMarketDataMessage();
          break;
        case 6:
          this.sendSystemStatsMessage();
          break;
        case 7:
          this.sendExecutionReport();
          break;
        default:
          this.sendOrderBookDelta();
      }
    }, 1000); // Send message every second
  }

  stop() {
    if (this.intervalId) {
      clearInterval(this.intervalId);
      this.intervalId = null;
    }
    
    if (this.server) {
      this.server.close();
      console.log(`Test WebSocket server on port ${this.port} stopped`);
    }
  }
}

// Create test servers for each service (using different ports to avoid conflicts)
const servers = [
  new TestWebSocketServer(19090), // Mock Server (test port)
  new TestWebSocketServer(18080), // Market Data Server (test port)
  new TestWebSocketServer(18081), // Order Rebuilder (test port)
  new TestWebSocketServer(18082)  // Temp Orders (test port)
];

// Start all servers
servers.forEach(server => server.start());

// Graceful shutdown
process.on('SIGINT', () => {
  console.log('Shutting down test servers...');
  servers.forEach(server => server.stop());
  process.exit(0);
});

process.on('SIGTERM', () => {
  console.log('Shutting down test servers...');
  servers.forEach(server => server.stop());
  process.exit(0);
});

console.log('All test WebSocket servers are running. Press Ctrl+C to stop.');