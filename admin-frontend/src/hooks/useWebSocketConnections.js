import { useState, useCallback } from 'react';
import { useIndependentWebSocket } from '../services/websocket/useIndependentWebSocket.js';
import { orderUpdateToExecutionReport, parseExecutionReport } from '../utils/kalshi-fix-utils.js';

export const useWebSocketConnections = (environment) => {
  const [lastExecution, setLastExecution] = useState(null);
  const [marketData, setMarketData] = useState({});
  const [orderBooks, setOrderBooks] = useState({});
  const [subscribedMarkets, setSubscribedMarkets] = useState(new Set());

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
    if (message.type === 'welcome') {
      console.log('Order Book Rebuilder connected:', message.message);
    } else if (message.type === 'orderbook_snapshot') {
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
    // Handle temp-orders WebSocket messages based on spec
    if (message.type === 'SUBSCRIPTION_CONFIRMED') {
      console.log('Temp Orders subscription confirmed:', message.message);
    } else if (message.type === 'ORDER_UPDATE' && message.data) {
      // Convert the order update to an ExecutionReport-like format
      const execReportLike = orderUpdateToExecutionReport(message.data);
      const parsedExec = parseExecutionReport(execReportLike);
      
      setLastExecution({
        timestamp: Date.now(),
        market: parsedExec.symbol || 'Unknown',
        action: `${message.data.event.replace('_', ' ')}`,
        side: parsedExec.side || 'unknown',
        price: parsedExec.lastPrice || parsedExec.avgPrice || 0,
        quantity: parsedExec.lastQty || parsedExec.filledQty || 0,
        status: parsedExec.status,
        orderId: parsedExec.orderId,
        betOrderId: parsedExec.betOrderId,
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
    onConnect: () => {
      // Subscribe to all markets on connect
      console.log('Order Book Rebuilder connected, subscribing to all markets');
      orderRebuilder.sendMessage({
        type: 'subscribe',
        market: 'ALL', // Subscribe to all markets
        allChanges: false
      });
    },
    reconnectInterval: 5000
  });

  const tempOrders = useIndependentWebSocket(environment?.tempOrdersUrl, {
    onMessage: handleTempOrdersMessage,
    onConnect: () => {
      // Subscribe to order updates on connect
      console.log('Temp Orders connected, subscribing to order updates');
      tempOrders.sendMessage({
        action: 'subscribe',
        type: 'orders'
      });
    },
    reconnectInterval: 10000
  });

  // Subscribe to a specific market for order book rebuilder
  const subscribeToMarket = useCallback((market) => {
    if (orderRebuilder.isConnected && market) {
      console.log(`Subscribing to market: ${market}`);
      orderRebuilder.sendMessage({
        type: 'subscribe',
        market: market,
        allChanges: false
      });
      setSubscribedMarkets(prev => new Set(prev).add(market));
    }
  }, [orderRebuilder]);

  // Unsubscribe from a specific market
  const unsubscribeFromMarket = useCallback((market) => {
    if (orderRebuilder.isConnected && market) {
      console.log(`Unsubscribing from market: ${market}`);
      orderRebuilder.sendMessage({
        type: 'unsubscribe',
        market: market
      });
      setSubscribedMarkets(prev => {
        const next = new Set(prev);
        next.delete(market);
        return next;
      });
    }
  }, [orderRebuilder]);

  return {
    mockServer,
    marketDataServer,
    orderRebuilder,
    tempOrders,
    lastExecution,
    marketData,
    orderBooks,
    subscribedMarkets,
    subscribeToMarket,
    unsubscribeFromMarket
  };
};