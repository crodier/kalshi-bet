import React, { useState, useEffect, useRef, useCallback } from 'react';
import { marketAPI } from '../services/api';
import websocketService from '../services/websocket';
import './MarketGrid.css';

const MarketGrid = ({ onMarketSelect }) => {
  const [markets, setMarkets] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [selectedMarket, setSelectedMarket] = useState(null);
  const tickerSubscriptionsRef = useRef([]);
  const isSubscribedRef = useRef(false);
  const [flashingMarkets, setFlashingMarkets] = useState(new Set());
  const [flashingCells, setFlashingCells] = useState({}); // Track which cells are flashing
  const [marketTimestamps, setMarketTimestamps] = useState({}); // Track timestamps for each market

  useEffect(() => {
    fetchMarkets();
  }, []);

  // Handle WebSocket subscriptions with proper cleanup
  const subscribeToMarkets = useCallback((marketList) => {
    // Clear any existing subscriptions
    tickerSubscriptionsRef.current.forEach(subId => {
      websocketService.unsubscribe(subId);
    });
    tickerSubscriptionsRef.current = [];
    
    const subscriptions = [];
    const marketTickers = marketList.map(m => m.ticker);
    
    marketTickers.forEach(ticker => {
      // Subscribe to ticker updates
      const tickerSubId = websocketService.subscribe(
        ['ticker'],
        [ticker],
        (message) => {
          if (message.msg && message.msg.marketTicker === ticker) {
            handleTickerUpdate(message.msg);
          }
        }
      );
      subscriptions.push(tickerSubId);
      
      // Subscribe to orderbook updates to get best prices
      const orderbookSubId = websocketService.subscribe(
        ['orderbook_snapshot', 'orderbook_delta'],
        [ticker],
        (message) => {
          if (message.msg && message.msg.market_ticker === ticker) {
            if (message.type === 'orderbook_snapshot') {
              handleOrderbookSnapshot(ticker, message.msg);
            } else if (message.type === 'orderbook_delta') {
              handleOrderbookDelta(ticker, message.msg);
            }
          }
        }
      );
      subscriptions.push(orderbookSubId);
      
      // Subscribe to trades/executions
      const tradesSubId = websocketService.subscribe(
        ['trade', 'execution'],
        [ticker],
        (message) => {
          if (message.msg && (message.msg.market_ticker === ticker || message.msg.ticker === ticker)) {
            handleTradeExecution(ticker, message.msg);
          }
        }
      );
      subscriptions.push(tradesSubId);
    });
    
    tickerSubscriptionsRef.current = subscriptions;
    isSubscribedRef.current = true;
  }, []);
  
  // Clean up subscriptions on unmount
  useEffect(() => {
    return () => {
      tickerSubscriptionsRef.current.forEach(subId => {
        websocketService.unsubscribe(subId);
      });
    };
  }, []);

  const fetchMarkets = async () => {
    try {
      setLoading(true);
      // Try to fetch markets from API first
      try {
        const response = await marketAPI.getMarkets();
        if (response.data && response.data.markets) {
          // Map API response to our format
          const now = new Date();
          const apiMarkets = response.data.markets.map(market => ({
            ticker: market.ticker,
            name: market.title || market.ticker,
            lastPrice: market.last_price ? Math.round(market.last_price / 100) : 50,
            yesBid: market.yes_bid ? Math.round(market.yes_bid / 100) : null,
            noBid: market.no_bid ? Math.round(market.no_bid / 100) : null,
            volume: market.volume || 0,
            yesBidTimestamp: market.yes_bid ? now : null,
            noBidTimestamp: market.no_bid ? now : null,
            lastBidUpdate: null,
            lastAskUpdate: null,
            lastExecution: null
          }));
          setMarkets(apiMarkets);
          
          // Subscribe to markets only after initial fetch
          if (!isSubscribedRef.current) {
            subscribeToMarkets(apiMarkets);
          }
        } else {
          // Fallback to mock markets
          const mockMarkets = getDefaultMarkets();
          setMarkets(mockMarkets);
          if (!isSubscribedRef.current) {
            subscribeToMarkets(mockMarkets);
          }
        }
      } catch (apiError) {
        // If API fails, use mock markets
        const mockMarkets = getDefaultMarkets();
        setMarkets(mockMarkets);
        if (!isSubscribedRef.current) {
          subscribeToMarkets(mockMarkets);
        }
      }
      setLoading(false);
    } catch (err) {
      setError(err.message);
      setLoading(false);
    }
  };
  
  const getDefaultMarkets = () => {
    const now = new Date();
    return [
      { ticker: 'DUMMY_TEST', name: 'Dummy Test Market', lastPrice: 50, yesBid: null, noBid: null, volume: 0, 
        yesBidTimestamp: null, noBidTimestamp: null, lastBidUpdate: null, lastAskUpdate: null, lastExecution: null },
      { ticker: 'TRUMPWIN-24NOV05', name: 'Trump Win Nov 2024', lastPrice: 50, yesBid: null, noBid: null, volume: 0,
        yesBidTimestamp: null, noBidTimestamp: null, lastBidUpdate: null, lastAskUpdate: null, lastExecution: null },
      { ticker: 'BTCZ-23DEC31-B50000', name: 'Bitcoin Above 50k Dec 2023', lastPrice: 50, yesBid: null, noBid: null, volume: 0,
        yesBidTimestamp: null, noBidTimestamp: null, lastBidUpdate: null, lastAskUpdate: null, lastExecution: null },
      { ticker: 'INXD-23DEC29-B5000', name: 'S&P 500 Above 5000 Dec 2023', lastPrice: 50, yesBid: null, noBid: null, volume: 0,
        yesBidTimestamp: null, noBidTimestamp: null, lastBidUpdate: null, lastAskUpdate: null, lastExecution: null },
      { ticker: 'MARKET_MAKER', name: 'Market Maker Test', lastPrice: 50, yesBid: null, noBid: null, volume: 0,
        yesBidTimestamp: null, noBidTimestamp: null, lastBidUpdate: null, lastAskUpdate: null, lastExecution: null }
    ];
  };
  
  const handleOrderbookSnapshot = useCallback((ticker, orderbookData) => {
    // Extract best YES buy and best NO buy from orderbook
    setMarkets(prevMarkets => {
      const currentMarket = prevMarkets.find(m => m.ticker === ticker);
      if (!currentMarket) return prevMarkets;
      
      let bestYesBuy = null;
      let bestNoBuy = null;
      const now = new Date();
      
      // Get best YES buy (highest price on YES side)
      if (orderbookData.yes && orderbookData.yes.length > 0) {
        // Sort YES orders by price descending to get best (highest) bid
        const sortedYes = [...orderbookData.yes].sort((a, b) => b[0] - a[0]);
        bestYesBuy = sortedYes[0][0]; // Highest price
      }
      
      // Get best NO buy (highest price on NO side)
      if (orderbookData.no && orderbookData.no.length > 0) {
        // Sort NO orders by price descending to get best (highest) bid
        const sortedNo = [...orderbookData.no].sort((a, b) => b[0] - a[0]);
        bestNoBuy = sortedNo[0][0]; // Highest price
      }
      
      const cellsToFlash = {};
      let lastBidUpdate = currentMarket.lastBidUpdate;
      let lastAskUpdate = currentMarket.lastAskUpdate;
      
      // Always update last bid/ask info for any YES/NO changes in the snapshot
      if (orderbookData.yes && orderbookData.yes.length > 0) {
        cellsToFlash.lastBid = true;
        lastBidUpdate = {
          price: bestYesBuy,
          timestamp: now,
          side: 'yes',
          type: 'snapshot',
          action: 'UPDATE',
          quantity: orderbookData.yes[0][1] // quantity from first level
        };
      }
      
      if (orderbookData.no && orderbookData.no.length > 0) {
        cellsToFlash.lastAsk = true;
        lastAskUpdate = {
          price: bestNoBuy,
          timestamp: now,
          side: 'no',
          type: 'snapshot',
          action: 'UPDATE',
          quantity: orderbookData.no[0][1] // quantity from first level
        };
      }
      
      // Check if top of book (best prices) changed - only update timestamps when best prices change
      let yesBidTimestamp = currentMarket.yesBidTimestamp;
      let noBidTimestamp = currentMarket.noBidTimestamp;
      
      if (bestYesBuy !== null && bestYesBuy !== currentMarket.yesBid) {
        cellsToFlash.bid = true;
        yesBidTimestamp = now; // Update timestamp only when best price changes
      }
      
      if (bestNoBuy !== null && bestNoBuy !== currentMarket.noBid) {
        cellsToFlash.ask = true;
        noBidTimestamp = now; // Update timestamp only when best price changes
      }
      
      // Flash specific cells that changed
      if (Object.keys(cellsToFlash).length > 0) {
        const flashKey = `${ticker}`;
        setFlashingCells(prev => ({
          ...prev,
          [flashKey]: cellsToFlash
        }));
        
        // Remove flash after animation
        setTimeout(() => {
          setFlashingCells(prev => {
            const newFlashing = { ...prev };
            delete newFlashing[flashKey];
            return newFlashing;
          });
        }, 2000);
      }
      
      // Return updated markets with new best prices and timestamps
      return prevMarkets.map(market => 
        market.ticker === ticker
          ? { 
              ...market, 
              yesBid: bestYesBuy !== null ? bestYesBuy : market.yesBid,
              noBid: bestNoBuy !== null ? bestNoBuy : market.noBid,
              yesBidTimestamp,
              noBidTimestamp,
              lastBidUpdate,
              lastAskUpdate,
              lastExecution: market.lastExecution
            }
          : market
      );
    });
  }, []);
  
  const handleOrderbookDelta = useCallback((ticker, deltaData) => {
    // Handle individual delta updates for best price tracking
    setMarkets(prevMarkets => {
      const currentMarket = prevMarkets.find(m => m.ticker === ticker);
      if (!currentMarket) return prevMarkets;
      
      // Delta format: { price: number, delta: number, side: 'yes'|'no' }
      const { price, delta, side } = deltaData;
      
      // For MarketGrid, track ALL delta updates and check if they affect best prices
      const cellsToFlash = {};
      let needsUpdate = true; // Always update for any delta
      let lastBidUpdate = currentMarket.lastBidUpdate;
      let lastAskUpdate = currentMarket.lastAskUpdate;
      let yesBidTimestamp = currentMarket.yesBidTimestamp;
      let noBidTimestamp = currentMarket.noBidTimestamp;
      const now = new Date();
      
      if (side === 'yes') {
        // This affects YES side (bids) - always track the update
        cellsToFlash.lastBid = true;
        lastBidUpdate = {
          price: price,
          timestamp: now,
          side: 'yes',
          type: 'delta',
          action: delta > 0 ? 'ADD' : 'REMOVE',
          quantity: Math.abs(delta)
        };
        
        // Check if this affects the best bid (top of book)
        if (delta > 0) {
          // Adding liquidity - might be new best bid
          if (!currentMarket.yesBid || price > currentMarket.yesBid) {
            cellsToFlash.bid = true;
            yesBidTimestamp = now; // Update timestamp only when best price changes
          }
        } else {
          // Removing liquidity - might affect best bid if it was the best
          if (currentMarket.yesBid === price) {
            cellsToFlash.bid = true;
            yesBidTimestamp = now; // Update timestamp only when best price changes
          }
        }
      } else if (side === 'no') {
        // This affects NO side (asks) - always track the update
        cellsToFlash.lastAsk = true;
        lastAskUpdate = {
          price: price,
          timestamp: now,
          side: 'no',
          type: 'delta',
          action: delta > 0 ? 'ADD' : 'REMOVE',
          quantity: Math.abs(delta)
        };
        
        // Check if this affects the best ask (top of book)
        if (delta > 0) {
          // Adding liquidity - might be new best ask
          if (!currentMarket.noBid || price > currentMarket.noBid) {
            cellsToFlash.ask = true;
            noBidTimestamp = now; // Update timestamp only when best price changes
          }
        } else {
          // Removing liquidity - might affect best ask if it was the best
          if (currentMarket.noBid === price) {
            cellsToFlash.ask = true;
            noBidTimestamp = now; // Update timestamp only when best price changes
          }
        }
      }
      
      // Flash cells and update market data if needed
      if (needsUpdate) {
        const flashKey = `${ticker}`;
        setFlashingCells(prev => ({
          ...prev,
          [flashKey]: cellsToFlash
        }));
        
        // Remove flash after animation
        setTimeout(() => {
          setFlashingCells(prev => {
            const newFlashing = { ...prev };
            delete newFlashing[flashKey];
            return newFlashing;
          });
        }, 2000);
        
        // Return updated markets with new prices and timestamps based on delta
        return prevMarkets.map(market => 
          market.ticker === ticker
            ? { 
                ...market, 
                yesBid: side === 'yes' && delta > 0 ? Math.max(market.yesBid || 0, price) : market.yesBid,
                noBid: side === 'no' && delta > 0 ? Math.max(market.noBid || 0, price) : market.noBid,
                yesBidTimestamp,
                noBidTimestamp,
                lastBidUpdate,
                lastAskUpdate,
                lastExecution: market.lastExecution
              }
            : market
        );
      }
      
      return prevMarkets;
    });
  }, []);
  
  const handleTradeExecution = useCallback((ticker, tradeData) => {
    const now = new Date();
    
    setMarkets(prevMarkets => {
      const currentMarket = prevMarkets.find(m => m.ticker === ticker);
      if (!currentMarket) return prevMarkets;
      
      // Extract execution details from trade data
      const execution = {
        price: tradeData.price ? Math.round(tradeData.price / 100) : tradeData.execution_price || tradeData.last_price,
        size: tradeData.size || tradeData.quantity || tradeData.count || 1,
        timestamp: now,
        side: tradeData.side || 'unknown'
      };
      
      // Flash the execution cell
      const flashKey = `${ticker}`;
      setFlashingCells(prev => ({
        ...prev,
        [flashKey]: { ...prev[flashKey], execution: true }
      }));
      
      // Remove flash after animation
      setTimeout(() => {
        setFlashingCells(prev => {
          const newFlashing = { ...prev };
          if (newFlashing[flashKey]) {
            delete newFlashing[flashKey].execution;
            if (Object.keys(newFlashing[flashKey]).length === 0) {
              delete newFlashing[flashKey];
            }
          }
          return newFlashing;
        });
      }, 2000);
      
      // Update market with execution data
      return prevMarkets.map(market => 
        market.ticker === ticker
          ? { ...market, lastExecution: execution }
          : market
      );
    });
  }, []);
  
  const handleTickerUpdate = useCallback((tickerData) => {
    // Handle ticker updates for last price and volume only
    setMarkets(prevMarkets => {
      const currentMarket = prevMarkets.find(m => m.ticker === tickerData.marketTicker);
      if (!currentMarket) return prevMarkets;
      
      const newPrice = tickerData.lastPrice ? Math.round(tickerData.lastPrice / 100) : null;
      const newVolume = tickerData.volume;
      
      const cellsToFlash = {};
      
      // Check each field for changes
      if (newPrice !== null && newPrice !== currentMarket.lastPrice) {
        cellsToFlash.price = true;
      }
      if (newVolume !== undefined && newVolume !== currentMarket.volume) {
        cellsToFlash.volume = true;
      }
      
      // Flash specific cells that changed
      if (Object.keys(cellsToFlash).length > 0) {
        const flashKey = `${tickerData.marketTicker}`;
        setFlashingCells(prev => ({
          ...prev,
          [flashKey]: { ...prev[flashKey], ...cellsToFlash }
        }));
        
        // Remove flash after animation
        setTimeout(() => {
          setFlashingCells(prev => {
            const newFlashing = { ...prev };
            if (newFlashing[flashKey]) {
              delete newFlashing[flashKey].price;
              delete newFlashing[flashKey].volume;
              if (Object.keys(newFlashing[flashKey]).length === 0) {
                delete newFlashing[flashKey];
              }
            }
            return newFlashing;
          });
        }, 2000);
      }

      return prevMarkets.map(market => 
        market.ticker === tickerData.marketTicker
          ? { 
              ...market, 
              lastPrice: newPrice !== null ? newPrice : market.lastPrice,
              volume: newVolume !== undefined ? newVolume : market.volume
            }
          : market
      );
    });
  }, []);

  const handleMarketClick = (market) => {
    setSelectedMarket(market.ticker);
    if (onMarketSelect) {
      onMarketSelect(market);
    }
  };

  if (loading) return <div className="loading">Loading markets...</div>;
  if (error) return <div className="error">Error loading markets: {error}</div>;

  return (
    <div className="market-grid">
      <h2>Markets</h2>
      <div className="grid-container">
        <div className="grid-header">
          <div className="grid-cell">Market</div>
          <div className="grid-cell">Yes Buy Best</div>
          <div className="grid-cell">No Buy Best</div>
          <div className="grid-cell">Last Bid Update</div>
          <div className="grid-cell">Last Ask Update</div>
          <div className="grid-cell">Last Execution</div>
          <div className="grid-cell">Volume</div>
        </div>
        {markets.map((market) => {
          const cellFlash = flashingCells[market.ticker] || {};
          return (
            <div
              key={market.ticker}
              className={`grid-row ${selectedMarket === market.ticker ? 'selected' : ''}`}
              onClick={() => handleMarketClick(market)}
            >
              <div className="grid-cell market-name">
                <div className="ticker">{market.ticker}</div>
                <div className="name">{market.name}</div>
              </div>
              <div className={`grid-cell bid ${cellFlash.bid ? 'flash-green' : ''}`}>
                <div className="price-value">{market.yesBid !== null ? `${market.yesBid}¢` : '-'}</div>
                {market.yesBidTimestamp && (
                  <div className="timestamp">{market.yesBidTimestamp.toLocaleTimeString('en-US', { 
                    hour12: false, 
                    hour: '2-digit', 
                    minute: '2-digit', 
                    second: '2-digit',
                    fractionalSecondDigits: 1
                  })}</div>
                )}
              </div>
              <div className={`grid-cell ask ${cellFlash.ask ? 'flash-red' : ''}`}>
                <div className="price-value">{market.noBid !== null ? `${market.noBid}¢` : '-'}</div>
                {market.noBidTimestamp && (
                  <div className="timestamp">{market.noBidTimestamp.toLocaleTimeString('en-US', { 
                    hour12: false, 
                    hour: '2-digit', 
                    minute: '2-digit', 
                    second: '2-digit',
                    fractionalSecondDigits: 1
                  })}</div>
                )}
              </div>
              <div className={`grid-cell last-bid-update ${cellFlash.lastBid ? 'flash-blue' : ''}`}>
                {market.lastBidUpdate ? (
                  <div>
                    <div className="update-action">{market.lastBidUpdate.action} {market.lastBidUpdate.quantity} @ {market.lastBidUpdate.price}¢</div>
                    <div className="timestamp">{market.lastBidUpdate.timestamp.toLocaleTimeString('en-US', { 
                      hour12: false, 
                      hour: '2-digit', 
                      minute: '2-digit', 
                      second: '2-digit',
                      fractionalSecondDigits: 1
                    })}</div>
                  </div>
                ) : '-'}
              </div>
              <div className={`grid-cell last-ask-update ${cellFlash.lastAsk ? 'flash-orange' : ''}`}>
                {market.lastAskUpdate ? (
                  <div>
                    <div className="update-action">{market.lastAskUpdate.action} {market.lastAskUpdate.quantity} @ {market.lastAskUpdate.price}¢</div>
                    <div className="timestamp">{market.lastAskUpdate.timestamp.toLocaleTimeString('en-US', { 
                      hour12: false, 
                      hour: '2-digit', 
                      minute: '2-digit', 
                      second: '2-digit',
                      fractionalSecondDigits: 1
                    })}</div>
                  </div>
                ) : '-'}
              </div>
              <div className={`grid-cell last-execution ${cellFlash.execution ? 'flash-purple' : ''}`}>
                {market.lastExecution ? (
                  <div>
                    <div className="execution-price">{market.lastExecution.price}¢ × {market.lastExecution.size}</div>
                    <div className="timestamp">{market.lastExecution.timestamp.toLocaleTimeString('en-US', { 
                      hour12: false, 
                      hour: '2-digit', 
                      minute: '2-digit', 
                      second: '2-digit',
                      fractionalSecondDigits: 1
                    })}</div>
                  </div>
                ) : '-'}
              </div>
              <div className={`grid-cell volume ${cellFlash.volume ? 'flash-update' : ''}`}>
                {market.volume.toLocaleString()}
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
};

export default MarketGrid;