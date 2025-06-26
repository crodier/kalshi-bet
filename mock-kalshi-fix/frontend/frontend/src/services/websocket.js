class WebSocketService {
  constructor() {
    this.ws = null;
    this.messageHandlers = new Map();
    this.subscriptions = new Set();
    this.reconnectAttempts = 0;
    this.maxReconnectAttempts = 5;
    this.reconnectDelay = 1000;
    this.messageId = 0;
    this.connectionStatusHandlers = new Set();
    this.connectionState = {
      isConnected: false,
      isConnecting: false,
      error: null,
      wasConnected: false // Track if we ever established a connection
    };
  }

  connect(url = 'ws://localhost:9090/trade-api/ws/v2') {
    this.updateConnectionState({ isConnecting: true, error: null });
    
    return new Promise((resolve, reject) => {
      try {
        this.ws = new WebSocket(url);

        this.ws.onopen = () => {
          console.log('WebSocket connected');
          this.reconnectAttempts = 0;
          this.updateConnectionState({ isConnected: true, isConnecting: false, error: null, wasConnected: true });
          this.resubscribe();
          resolve();
        };

        this.ws.onmessage = (event) => {
          try {
            const message = JSON.parse(event.data);
            this.handleMessage(message);
          } catch (error) {
            console.error('Failed to parse WebSocket message:', error);
          }
        };

        this.ws.onerror = (error) => {
          console.error('WebSocket error:', error);
          this.updateConnectionState({ 
            isConnected: false, 
            isConnecting: false, 
            error: 'Connection failed' 
          });
          reject(error);
        };

        this.ws.onclose = () => {
          console.log('WebSocket disconnected');
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
      console.log(`Attempting to reconnect... (${this.reconnectAttempts}/${this.maxReconnectAttempts})`);
      setTimeout(() => {
        this.connect();
      }, this.reconnectDelay * this.reconnectAttempts);
    }
  }

  resubscribe() {
    // Resubscribe to all active subscriptions after reconnection
    this.subscriptions.forEach((subscription) => {
      this.send(subscription);
    });
  }

  send(message) {
    if (this.ws && this.ws.readyState === WebSocket.OPEN) {
      this.ws.send(JSON.stringify(message));
    } else {
      console.error('WebSocket is not connected');
    }
  }

  subscribe(channels, marketTickers, handler) {
    const id = ++this.messageId;
    const subscription = {
      id,
      cmd: 'subscribe',
      params: {
        channels,
        market_tickers: marketTickers
      }
    };

    this.subscriptions.add(subscription);
    this.messageHandlers.set(id, handler);
    this.send(subscription);

    return id;
  }

  unsubscribe(subscriptionId) {
    const unsubscribeMessage = {
      id: ++this.messageId,
      cmd: 'unsubscribe',
      subscription_id: subscriptionId
    };

    this.send(unsubscribeMessage);
    this.messageHandlers.delete(subscriptionId);
    
    // Remove from subscriptions
    this.subscriptions = new Set(
      Array.from(this.subscriptions).filter(sub => sub.id !== subscriptionId)
    );
  }

  handleMessage(message) {
    // Handle different message types
    if (message.type === 'orderbook_snapshot' || 
        message.type === 'orderbook_delta' || 
        message.type === 'trade' || 
        message.type === 'ticker') {
      
      // Notify all handlers about market data updates
      this.messageHandlers.forEach((handler) => {
        handler(message);
      });
    }
  }

  on(eventType, handler) {
    // General event handler for specific message types
    const id = `event_${eventType}_${++this.messageId}`;
    this.messageHandlers.set(id, (message) => {
      if (message.type === eventType) {
        handler(message);
      }
    });
    return id;
  }

  off(handlerId) {
    this.messageHandlers.delete(handlerId);
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

export default new WebSocketService();