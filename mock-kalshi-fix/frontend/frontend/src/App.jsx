import { useState, useEffect } from 'react';
import MarketGrid from './components/MarketGrid';
import OrderBook from './components/OrderBook';
import OrderEntry from './components/OrderEntry';
import EventOrderTicket from './components/EventOrderTicket';
import ConnectionStatus from './components/ConnectionStatus';
import { useWebSocket } from './hooks/useWebSocket';
import './App.css';

function App() {
  const [selectedMarket, setSelectedMarket] = useState(null);
  const [connectionState, setConnectionState] = useState({
    isConnected: false,
    isConnecting: true,
    error: null,
    wasConnected: false
  });
  const websocket = useWebSocket();

  useEffect(() => {
    // Subscribe to connection status changes
    const unsubscribe = websocket.onConnectionStatusChange((state) => {
      setConnectionState(state);
    });

    return unsubscribe;
  }, [websocket]);

  const handleMarketSelect = (market) => {
    setSelectedMarket(market);
  };

  const handleOrderPlaced = (order) => {
    console.log('Order placed:', order);
    // You could refresh the orderbook or show a notification here
  };

  return (
    <div className={`app ${!connectionState.isConnected ? 'disconnected' : ''}`}>
      <ConnectionStatus 
        isConnected={connectionState.isConnected}
        isConnecting={connectionState.isConnecting}
        error={connectionState.error}
        wasConnected={connectionState.wasConnected}
      />
      <header className="app-header">
        <h1>Mock Kalshi Trading Platform</h1>
      </header>
      
      <div className="app-content">
        <div className="top-panel">
          <MarketGrid onMarketSelect={handleMarketSelect} />
        </div>
        
        <div className="bottom-panel">
          <div className="orderbook-section">
            <OrderBook marketTicker={selectedMarket?.ticker} />
          </div>
          
          <div className="order-entry-section">
            <EventOrderTicket 
              marketTicker={selectedMarket?.ticker}
            />
            <OrderEntry 
              marketTicker={selectedMarket?.ticker} 
              onOrderPlaced={handleOrderPlaced}
            />
          </div>
        </div>
      </div>
    </div>
  );
}

export default App;