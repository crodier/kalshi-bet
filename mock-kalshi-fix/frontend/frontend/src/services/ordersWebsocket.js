class OrdersWebSocketService {
  constructor() {
    this.ws = null;
    this.messageHandlers = new Map();
    this.subscriptions = new Map();
    this.reconnectAttempts = 0;
    this.maxReconnectAttempts = 5;
    this.reconnectDelay = 1000;
    this.messageId = 0;
    this.connectionStatusHandlers = new Set();
    this.connectionState = {
      isConnected: false,
      isConnecting: false,
      error: null
    };
  }

  connect(url = 'ws://localhost:9090/trade-api/ws/v2') {
    this.updateConnectionState({ isConnecting: true, error: null });
    
    return new Promise((resolve, reject) => {
      try {
        this.ws = new WebSocket(url);

        this.ws.onopen = () => {
          console.log('Internal orders WebSocket connected');
          this.reconnectAttempts = 0;
          this.updateConnectionState({ isConnected: true, isConnecting: false, error: null });
          resolve();
        };

        this.ws.onmessage = (event) => {
          try {
            const message = JSON.parse(event.data);
            this.handleMessage(message);
          } catch (error) {
            console.error('Failed to parse orders WebSocket message:', error);
          }
        };

        this.ws.onerror = (error) => {
          console.error('Orders WebSocket error:', error);
          this.updateConnectionState({ 
            isConnected: false, 
            isConnecting: false, 
            error: 'Connection failed' 
          });
          reject(error);
        };

        this.ws.onclose = () => {
          console.log('Orders WebSocket disconnected');
          this.updateConnectionState({ 
            isConnected: false, 
            isConnecting: false, 
            error: this.reconnectAttempts >= this.maxReconnectAttempts ? 'Max reconnection attempts reached' : null 
          });
          this.attemptReconnect();
        };
      } catch (error) {
        this.updateConnectionState({ 
          isConnected: false, 
          isConnecting: false, 
          error: 'Failed to create WebSocket connection' 
        });
        reject(error);
      }
    });
  }

  attemptReconnect() {
    if (this.reconnectAttempts < this.maxReconnectAttempts) {
      this.reconnectAttempts++;
      console.log(`Attempting to reconnect orders WebSocket... (${this.reconnectAttempts}/${this.maxReconnectAttempts})`);
      setTimeout(() => {
        this.connect();
      }, this.reconnectDelay * this.reconnectAttempts);
    }
  }

  send(message) {
    if (this.ws && this.ws.readyState === WebSocket.OPEN) {
      this.ws.send(JSON.stringify(message));
    } else {
      console.error('Orders WebSocket is not connected');
    }
  }

  subscribe(marketTicker, handler) {
    const id = ++this.messageId;
    const subscription = {
      id,
      cmd: 'subscribe',
      params: {
        channels: ['orders'],
        market_tickers: [marketTicker]
      }
    };

    this.subscriptions.set(marketTicker, { id, handler });
    this.messageHandlers.set(`orders_${marketTicker}`, handler);
    this.send(subscription);

    return id;
  }

  unsubscribe(marketTicker) {
    const subscription = this.subscriptions.get(marketTicker);
    if (subscription) {
      const unsubscribeMessage = {
        id: ++this.messageId,
        cmd: 'unsubscribe',
        sid: subscription.id
      };

      this.send(unsubscribeMessage);
      this.subscriptions.delete(marketTicker);
      this.messageHandlers.delete(`orders_${marketTicker}`);
    }
  }

  handleMessage(message) {
    // Handle different message types
    if (message.type === 'subscribed') {
      console.log('Subscribed to orders:', message);
    } else if (message.type === 'order_update') {
      // Notify all relevant handlers
      const orderUpdate = message.msg;
      if (orderUpdate && orderUpdate.market_ticker) {
        const handler = this.messageHandlers.get(`orders_${orderUpdate.market_ticker}`);
        if (handler) {
          handler(message);
        }
      }
    } else if (message.type === 'error') {
      console.error('Orders WebSocket error:', message.error);
    }
  }

  disconnect() {
    if (this.ws) {
      this.ws.close();
      this.ws = null;
    }
    this.messageHandlers.clear();
    this.subscriptions.clear();
    this.updateConnectionState({ isConnected: false, isConnecting: false, error: null });
  }

  updateConnectionState(updates) {
    this.connectionState = { ...this.connectionState, ...updates };
    this.notifyConnectionStatusHandlers();
  }

  notifyConnectionStatusHandlers() {
    this.connectionStatusHandlers.forEach(handler => {
      handler(this.connectionState);
    });
  }

  onConnectionStatusChange(handler) {
    this.connectionStatusHandlers.add(handler);
    // Immediately notify with current state
    handler(this.connectionState);
    
    // Return unsubscribe function
    return () => {
      this.connectionStatusHandlers.delete(handler);
    };
  }

  getConnectionState() {
    return this.connectionState;
  }
}

export default new OrdersWebSocketService();