export class AdminWebSocketService {
  constructor(url) {
    this.url = url
    this.ws = null
    this.isConnecting = false
    this.reconnectAttempts = 0
    this.maxReconnectAttempts = 10
    this.reconnectDelay = 1000
    this.subscriptions = new Map()
    
    // Event handlers
    this.onConnectionChange = null
    this.onSystemStats = null
    this.onOrderBookUpdate = null
    this.onMarketFilter = null
    this.onError = null
    
    // Auto-reconnect
    this.shouldReconnect = true
  }

  connect() {
    if (this.isConnecting || (this.ws && this.ws.readyState === WebSocket.OPEN)) {
      return
    }

    this.isConnecting = true
    this.notifyConnectionChange('connecting')

    try {
      this.ws = new WebSocket(this.url)
      
      this.ws.onopen = () => {
        console.log('Admin WebSocket connected')
        this.isConnecting = false
        this.reconnectAttempts = 0
        this.notifyConnectionChange('connected')
        
        // Resubscribe to all previous subscriptions
        this.resubscribeAll()
      }

      this.ws.onmessage = (event) => {
        try {
          const data = JSON.parse(event.data)
          this.handleMessage(data)
        } catch (error) {
          console.error('Error parsing WebSocket message:', error)
        }
      }

      this.ws.onclose = (event) => {
        console.log('Admin WebSocket closed:', event.code, event.reason)
        this.isConnecting = false
        this.notifyConnectionChange('disconnected')
        
        if (this.shouldReconnect) {
          this.scheduleReconnect()
        }
      }

      this.ws.onerror = (error) => {
        console.error('Admin WebSocket error:', error)
        this.isConnecting = false
        this.notifyConnectionChange('error')
        
        if (this.onError) {
          this.onError(error)
        }
      }

    } catch (error) {
      console.error('Failed to create WebSocket connection:', error)
      this.isConnecting = false
      this.notifyConnectionChange('error')
      this.scheduleReconnect()
    }
  }

  disconnect() {
    this.shouldReconnect = false
    if (this.ws) {
      this.ws.close()
      this.ws = null
    }
    this.subscriptions.clear()
    this.notifyConnectionChange('disconnected')
  }

  isConnected() {
    return this.ws && this.ws.readyState === WebSocket.OPEN
  }

  send(message) {
    if (this.isConnected()) {
      this.ws.send(JSON.stringify(message))
      return true
    }
    return false
  }

  // Subscribe to system statistics updates
  subscribeToSystemStats() {
    const message = {
      type: 'subscribe',
      channel: 'system-stats'
    }
    this.subscriptions.set('system-stats', message)
    return this.send(message)
  }

  // Subscribe to specific market updates
  subscribeToMarket(marketTicker) {
    const message = {
      type: 'subscribe',
      channel: 'market-data',
      marketTicker: marketTicker
    }
    this.subscriptions.set(`market-${marketTicker}`, message)
    return this.send(message)
  }

  // Unsubscribe from market
  unsubscribeFromMarket(marketTicker) {
    const message = {
      type: 'unsubscribe',
      channel: 'market-data',
      marketTicker: marketTicker
    }
    this.subscriptions.delete(`market-${marketTicker}`)
    return this.send(message)
  }

  // Filter markets
  filterMarkets(searchTerm) {
    const message = {
      type: 'filter-markets',
      searchTerm: searchTerm
    }
    return this.send(message)
  }

  handleMessage(data) {
    switch (data.type) {
      case 'system-stats':
        if (this.onSystemStats) {
          this.onSystemStats(data.payload)
        }
        break
        
      case 'market-data':
        if (this.onOrderBookUpdate) {
          this.onOrderBookUpdate(data.payload)
        }
        break
        
      case 'market-filter-result':
        if (this.onMarketFilter) {
          this.onMarketFilter(data.payload)
        }
        break
        
      case 'ping':
        // Respond to ping with pong
        this.send({ type: 'pong' })
        break
        
      default:
        console.log('Unknown message type:', data.type)
    }
  }

  resubscribeAll() {
    this.subscriptions.forEach((message) => {
      this.send(message)
    })
  }

  scheduleReconnect() {
    if (this.reconnectAttempts >= this.maxReconnectAttempts) {
      console.error('Max reconnection attempts reached')
      this.notifyConnectionChange('error')
      return
    }

    const delay = this.reconnectDelay * Math.pow(2, this.reconnectAttempts)
    this.reconnectAttempts++
    
    console.log(`Reconnecting in ${delay}ms (attempt ${this.reconnectAttempts}/${this.maxReconnectAttempts})`)
    
    setTimeout(() => {
      if (this.shouldReconnect) {
        this.connect()
      }
    }, delay)
  }

  notifyConnectionChange(status) {
    if (this.onConnectionChange) {
      this.onConnectionChange(status)
    }
  }
}