import { useState, useCallback } from 'react';
import { useIndependentWebSocket } from '../services/websocket/useIndependentWebSocket.js';

export const useWebSocketConnections = (environment) => {
  const [lastExecution, setLastExecution] = useState(null);
  const [marketData, setMarketData] = useState({});
  const [orderBooks, setOrderBooks] = useState({});

  // Mock Server WebSocket
  const handleMockServerMessage = useCallback((message) => {
    if (message.type === 'trade' || message.type === 'fill') {
      setLastExecution({
        timestamp: Date.now(),
        market: message.msg?.market_ticker || 'Unknown',
        action: message.msg?.action || (message.type === 'trade' ? 'TRADE' : 'FILL'),
        side: message.msg?.side || 'unknown',
        price: message.msg?.price || 0,
        quantity: message.msg?.count || message.msg?.quantity || 0,
        source: 'Mock Server'
      });
    }
    
    if (message.type === 'order_update') {
      setLastExecution({
        timestamp: Date.now(),
        market: message.msg?.market_ticker || 'Unknown',
        action: `${message.msg?.action} ${message.msg?.side}`.toUpperCase(),
        side: message.msg?.side || 'unknown',
        price: message.msg?.price || 0,
        quantity: message.msg?.original_quantity || 0,
        source: 'Mock Server (Order)'
      });
    }
    
    if (message.type === 'orderbook_snapshot' || message.type === 'orderbook_delta') {
      const ticker = message.msg?.market_ticker;
      if (ticker) {
        setOrderBooks(prev => ({
          ...prev,
          [ticker]: {
            ...prev[ticker],
            mockServer: message,
            lastUpdated: Date.now()
          }
        }));
      }
    }
  }, []);

  const handleMarketDataMessage = useCallback((message) => {
    if (message.type === 'market-data') {
      const ticker = message.payload?.marketTicker;
      if (ticker) {
        setMarketData(prev => ({
          ...prev,
          [ticker]: {
            ...message.payload,
            lastUpdated: Date.now(),
            source: 'Market Data Server'
          }
        }));
      }
    }
  }, []);

  const handleOrderRebuilderMessage = useCallback((message) => {
    if (message.type === 'orderbook_snapshot') {
      const ticker = message.market_ticker;
      if (ticker) {
        setOrderBooks(prev => ({
          ...prev,
          [ticker]: {
            ...prev[ticker],
            orderRebuilder: message,
            lastUpdated: Date.now()
          }
        }));
      }
    }
  }, []);

  const handleTempOrdersMessage = useCallback((message) => {
    if (message.type === 'execution_report') {
      setLastExecution({
        timestamp: Date.now(),
        market: message.market_ticker || 'Unknown',
        action: message.action || 'ORDER',
        side: message.side || 'unknown', 
        price: message.price || 0,
        quantity: message.quantity || 0,
        source: 'Temp Orders'
      });
    }
  }, []);

  const mockServer = useIndependentWebSocket(environment?.mockServerUrl, {
    onMessage: handleMockServerMessage,
    reconnectInterval: 5000
  });

  const marketDataServer = useIndependentWebSocket(environment?.marketDataUrl, {
    onMessage: handleMarketDataMessage,
    reconnectInterval: 3000
  });

  const orderRebuilder = useIndependentWebSocket(environment?.orderRebuilderUrl, {
    onMessage: handleOrderRebuilderMessage,
    reconnectInterval: 5000
  });

  const tempOrders = useIndependentWebSocket(environment?.tempOrdersUrl, {
    onMessage: handleTempOrdersMessage,
    reconnectInterval: 10000
  });

  return {
    mockServer,
    marketDataServer,
    orderRebuilder,
    tempOrders,
    lastExecution,
    marketData,
    orderBooks
  };
};