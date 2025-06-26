import { useState, useEffect, useRef } from 'react'
import SystemStats from './components/SystemStats'
import MarketFilter from './components/MarketFilter'
import OrderBookViewer from './components/OrderBookViewer'
import ConnectionStatus from './components/ConnectionStatus'
import { adminAPI } from './services/api'
import { AdminWebSocketService } from './services/adminWebSocket'
import './App.css'

function App() {
  const [connectionStatus, setConnectionStatus] = useState('disconnected')
  const [systemStats, setSystemStats] = useState(null)
  const [selectedMarket, setSelectedMarket] = useState(null)
  const [filteredMarkets, setFilteredMarkets] = useState([])
  const [orderBookData, setOrderBookData] = useState(null)
  const wsService = useRef(null)

  useEffect(() => {
    // Initialize WebSocket service
    wsService.current = new AdminWebSocketService('ws://localhost:8084/ws/admin')
    
    // Set up WebSocket event handlers
    wsService.current.onConnectionChange = (status) => {
      setConnectionStatus(status)
    }
    
    wsService.current.onSystemStats = (stats) => {
      setSystemStats(stats)
    }
    
    wsService.current.onOrderBookUpdate = (data) => {
      if (selectedMarket && data.marketTicker === selectedMarket) {
        setOrderBookData(data)
      }
    }
    
    wsService.current.onMarketFilter = (markets) => {
      setFilteredMarkets(markets)
    }
    
    // Connect to WebSocket
    wsService.current.connect()
    
    // Load initial system stats
    loadSystemStats()
    
    return () => {
      if (wsService.current) {
        wsService.current.disconnect()
      }
    }
  }, [selectedMarket])

  const loadSystemStats = async () => {
    try {
      const stats = await adminAPI.getSystemStats()
      setSystemStats(stats)
    } catch (error) {
      console.error('Failed to load system stats:', error)
    }
  }

  const handleMarketFilter = (searchTerm) => {
    if (wsService.current && wsService.current.isConnected()) {
      wsService.current.filterMarkets(searchTerm)
    }
  }

  const handleMarketSelect = (marketTicker) => {
    setSelectedMarket(marketTicker)
    if (wsService.current && wsService.current.isConnected()) {
      wsService.current.subscribeToMarket(marketTicker)
    }
  }

  return (
    <div className="app">
      <header className="header">
        <h1>Market Data Server Admin</h1>
        <div className="status-bar">
          <ConnectionStatus status={connectionStatus} />
          {systemStats && (
            <>
              <div className="status-item">
                <span>Markets: {systemStats.totalMarkets}</span>
              </div>
              <div className="status-item">
                <span>Avg Latency: {systemStats.averageLatency}ms</span>
              </div>
              <div className="status-item">
                <span>Messages/sec: {systemStats.messagesPerSecond}</span>
              </div>
            </>
          )}
        </div>
      </header>

      <main className="main-content">
        <div className="left-panel">
          <SystemStats 
            stats={systemStats} 
            onRefresh={loadSystemStats}
          />
          
          <MarketFilter
            markets={filteredMarkets}
            selectedMarket={selectedMarket}
            onFilter={handleMarketFilter}
            onMarketSelect={handleMarketSelect}
          />
        </div>

        <div className="right-panel">
          <OrderBookViewer
            marketTicker={selectedMarket}
            orderBookData={orderBookData}
            connectionStatus={connectionStatus}
          />
        </div>
      </main>
    </div>
  )
}

export default App