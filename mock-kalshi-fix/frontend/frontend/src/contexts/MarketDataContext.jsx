import React, { createContext, useContext, useState, useEffect, useCallback } from 'react';
import websocketService from '../services/websocket';

const MarketDataContext = createContext();

export const useMarketData = () => {
  const context = useContext(MarketDataContext);
  if (!context) {
    throw new Error('useMarketData must be used within a MarketDataProvider');
  }
  return context;
};

export const MarketDataProvider = ({ children }) => {
  const [marketData, setMarketData] = useState({});
  const [subscriptions, setSubscriptions] = useState(new Set());
  const subscriptionIdsRef = React.useRef([]);

  // Subscribe to a market's data
  const subscribeToMarket = useCallback((marketTicker) => {
    if (subscriptions.has(marketTicker)) {
      return; // Already subscribed
    }

    console.log('MarketDataContext: Subscribing to market:', marketTicker);
    
    // Subscribe to orderbook updates
    const orderbookSubId = websocketService.subscribe(
      ['orderbook_snapshot', 'orderbook_delta'],
      [marketTicker],
      (message) => {
        if (message.msg && message.msg.market_ticker === marketTicker) {
          if (message.type === 'orderbook_snapshot') {
            handleOrderbookSnapshot(marketTicker, message.msg);
          } else if (message.type === 'orderbook_delta') {
            handleOrderbookDelta(marketTicker, message.msg);
          }
        }
      }
    );

    // Subscribe to ticker updates
    const tickerSubId = websocketService.subscribe(
      ['ticker'],
      [marketTicker],
      (message) => {
        if (message.msg && message.msg.marketTicker === marketTicker) {
          handleTickerUpdate(marketTicker, message.msg);
        }
      }
    );

    // Subscribe to trades/executions
    const tradesSubId = websocketService.subscribe(
      ['trade', 'execution'],
      [marketTicker],
      (message) => {
        if (message.msg && (message.msg.market_ticker === marketTicker || message.msg.ticker === marketTicker)) {
          handleTradeExecution(marketTicker, message.msg);
        }
      }
    );

    subscriptionIdsRef.current.push(orderbookSubId, tickerSubId, tradesSubId);
    setSubscriptions(prev => new Set([...prev, marketTicker]));
  }, [subscriptions]);

  const handleOrderbookSnapshot = useCallback((marketTicker, orderbookData) => {
    const now = new Date();
    
    setMarketData(prevData => {
      const currentMarket = prevData[marketTicker] || {};
      
      // Calculate best prices and crossing prices
      const yesOrders = orderbookData.yes || [];
      const noOrders = orderbookData.no || [];
      
      let bestYesBuy = null;
      let bestNoBuy = null;
      
      if (yesOrders.length > 0) {
        const sortedYes = [...yesOrders].sort((a, b) => b[0] - a[0]);
        bestYesBuy = sortedYes[0][0];
      }
      
      if (noOrders.length > 0) {
        const sortedNo = [...noOrders].sort((a, b) => b[0] - a[0]);
        bestNoBuy = sortedNo[0][0];
      }
      
      // Calculate crossing prices for EventOrderTicket
      const crossingYes = noOrders.length > 0 ? {
        price: 100 - noOrders[0][0],
        quantity: noOrders[0][1],
        originalNoPrice: noOrders[0][0]
      } : null;
      
      const crossingNo = yesOrders.length > 0 ? {
        price: 100 - yesOrders[0][0],
        quantity: yesOrders[0][1],
        originalYesPrice: yesOrders[0][0]
      } : null;
      
      return {
        ...prevData,
        [marketTicker]: {
          ...currentMarket,
          orderbook: {
            yes: yesOrders,
            no: noOrders,
            lastUpdate: now,
            updateType: 'snapshot',
            snapshotInfo: {
              yesLevels: yesOrders.length,
              noLevels: noOrders.length
            }
          },
          bestPrices: {
            yesBuy: bestYesBuy,
            noBuy: bestNoBuy,
            yesBuyTimestamp: bestYesBuy !== currentMarket.bestPrices?.yesBuy ? now : currentMarket.bestPrices?.yesBuyTimestamp,
            noBuyTimestamp: bestNoBuy !== currentMarket.bestPrices?.noBuy ? now : currentMarket.bestPrices?.noBuyTimestamp
          },
          crossingPrices: {
            yes: crossingYes,
            no: crossingNo
          },
          lastBidUpdate: yesOrders.length > 0 ? {
            price: bestYesBuy,
            timestamp: now,
            side: 'yes',
            type: 'snapshot',
            action: 'UPDATE',
            quantity: yesOrders[0][1]
          } : currentMarket.lastBidUpdate,
          lastAskUpdate: noOrders.length > 0 ? {
            price: bestNoBuy,
            timestamp: now,
            side: 'no',
            type: 'snapshot', 
            action: 'UPDATE',
            quantity: noOrders[0][1]
          } : currentMarket.lastAskUpdate
        }
      };
    });
  }, []);

  const handleOrderbookDelta = useCallback((marketTicker, deltaData) => {
    const now = new Date();
    const { price, delta, side } = deltaData;
    
    setMarketData(prevData => {
      const currentMarket = prevData[marketTicker] || {};
      const currentOrderbook = currentMarket.orderbook || { yes: [], no: [] };
      
      // Update orderbook with delta
      const newYes = [...currentOrderbook.yes];
      const newNo = [...currentOrderbook.no];
      
      if (side === 'yes') {
        const existingIndex = newYes.findIndex(([p]) => p === price);
        if (delta > 0) {
          if (existingIndex >= 0) {
            newYes[existingIndex][1] += delta;
          } else {
            newYes.push([price, delta]);
            newYes.sort((a, b) => b[0] - a[0]);
          }
        } else if (existingIndex >= 0) {
          newYes[existingIndex][1] += delta;
          if (newYes[existingIndex][1] <= 0) {
            newYes.splice(existingIndex, 1);
          }
        }
      } else if (side === 'no') {
        const existingIndex = newNo.findIndex(([p]) => p === price);
        if (delta > 0) {
          if (existingIndex >= 0) {
            newNo[existingIndex][1] += delta;
          } else {
            newNo.push([price, delta]);
            newNo.sort((a, b) => b[0] - a[0]);
          }
        } else if (existingIndex >= 0) {
          newNo[existingIndex][1] += delta;
          if (newNo[existingIndex][1] <= 0) {
            newNo.splice(existingIndex, 1);
          }
        }
      }
      
      // Calculate new best prices (highest bids)
      const bestYesBuy = newYes.length > 0 ? Math.max(...newYes.map(([price]) => price)) : null;
      const bestNoBuy = newNo.length > 0 ? Math.max(...newNo.map(([price]) => price)) : null;
      
      // Check if best prices actually changed
      const currentBestYes = currentMarket.bestPrices?.yesBuy;
      const currentBestNo = currentMarket.bestPrices?.noBuy;
      const yesBestChanged = bestYesBuy !== currentBestYes;
      const noBestChanged = bestNoBuy !== currentBestNo;
      
      // Calculate crossing prices
      const crossingYes = newNo.length > 0 ? {
        price: 100 - newNo[0][0],
        quantity: newNo[0][1],
        originalNoPrice: newNo[0][0]
      } : null;
      
      const crossingNo = newYes.length > 0 ? {
        price: 100 - newYes[0][0],
        quantity: newYes[0][1],
        originalYesPrice: newYes[0][0]
      } : null;
      
      return {
        ...prevData,
        [marketTicker]: {
          ...currentMarket,
          orderbook: {
            yes: newYes,
            no: newNo,
            lastUpdate: now,
            updateType: 'delta',
            deltaInfo: {
              side: side,
              action: delta > 0 ? 'ADD' : 'REMOVE',
              price: price,
              quantity: Math.abs(delta)
            }
          },
          bestPrices: {
            yesBuy: bestYesBuy,
            noBuy: bestNoBuy,
            yesBuyTimestamp: bestYesBuy !== currentMarket.bestPrices?.yesBuy ? now : currentMarket.bestPrices?.yesBuyTimestamp,
            noBuyTimestamp: bestNoBuy !== currentMarket.bestPrices?.noBuy ? now : currentMarket.bestPrices?.noBuyTimestamp
          },
          crossingPrices: {
            yes: crossingYes,
            no: crossingNo
          },
          lastBidUpdate: side === 'yes' ? {
            price: price,
            timestamp: now,
            side: 'yes',
            type: 'delta',
            action: delta > 0 ? 'ADD' : 'REMOVE',
            quantity: Math.abs(delta)
          } : currentMarket.lastBidUpdate,
          lastAskUpdate: side === 'no' ? {
            price: price,
            timestamp: now,
            side: 'no',
            type: 'delta',
            action: delta > 0 ? 'ADD' : 'REMOVE',
            quantity: Math.abs(delta)
          } : currentMarket.lastAskUpdate
        }
      };
    });
  }, []);

  const handleTickerUpdate = useCallback((marketTicker, tickerData) => {
    setMarketData(prevData => {
      const currentMarket = prevData[marketTicker] || {};
      
      return {
        ...prevData,
        [marketTicker]: {
          ...currentMarket,
          ticker: {
            lastPrice: tickerData.lastPrice ? Math.round(tickerData.lastPrice / 100) : null,
            volume: tickerData.volume,
            lastUpdate: new Date()
          }
        }
      };
    });
  }, []);

  const handleTradeExecution = useCallback((marketTicker, tradeData) => {
    const now = new Date();
    
    setMarketData(prevData => {
      const currentMarket = prevData[marketTicker] || {};
      
      const execution = {
        price: tradeData.price ? Math.round(tradeData.price / 100) : tradeData.execution_price || tradeData.last_price,
        size: tradeData.size || tradeData.quantity || tradeData.count || 1,
        timestamp: now,
        side: tradeData.side || 'unknown'
      };
      
      return {
        ...prevData,
        [marketTicker]: {
          ...currentMarket,
          lastExecution: execution
        }
      };
    });
  }, []);

  const unsubscribeFromMarket = useCallback((marketTicker) => {
    console.log('MarketDataContext: Unsubscribing from market:', marketTicker);
    setSubscriptions(prev => {
      const newSubs = new Set(prev);
      newSubs.delete(marketTicker);
      return newSubs;
    });
  }, []);

  const getMarketData = useCallback((marketTicker) => {
    return marketData[marketTicker] || {};
  }, [marketData]);

  // Cleanup subscriptions on unmount
  useEffect(() => {
    return () => {
      subscriptionIdsRef.current.forEach(subId => {
        websocketService.unsubscribe(subId);
      });
    };
  }, []);

  const value = {
    marketData,
    subscribeToMarket,
    unsubscribeFromMarket,
    getMarketData
  };

  return (
    <MarketDataContext.Provider value={value}>
      {children}
    </MarketDataContext.Provider>
  );
};