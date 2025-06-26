import React, { useState, useEffect, useRef, useCallback } from 'react';
import { marketAPI } from '../services/api';
import { useMarketData } from '../contexts/MarketDataContext';
import './MarketGrid.css';

const MarketGrid = ({ onMarketSelect }) => {
  const { subscribeToMarket, marketData } = useMarketData();
  const [markets, setMarkets] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [selectedMarket, setSelectedMarket] = useState(null);
  const [flashingCells, setFlashingCells] = useState({}); // Track which cells are flashing

  useEffect(() => {
    fetchMarkets();
  }, []);

  // Subscribe to all markets for data updates
  useEffect(() => {
    if (markets.length > 0) {
      markets.forEach(market => {
        subscribeToMarket(market.ticker);
      });
    }
  }, [markets, subscribeToMarket]);

  // Update markets with shared market data
  useEffect(() => {
    if (markets.length > 0) {
      const hasUpdates = Object.keys(marketData).some(ticker => {
        const sharedData = marketData[ticker];
        const market = markets.find(m => m.ticker === ticker);
        if (!market || !sharedData) return false;
        
        // Check if any data has changed
        return (
          (sharedData.bestPrices?.yesBuy !== market.yesBid) ||
          (sharedData.bestPrices?.noBuy !== market.noBid) ||
          (sharedData.lastBidUpdate && sharedData.lastBidUpdate !== market.lastBidUpdate) ||
          (sharedData.lastAskUpdate && sharedData.lastAskUpdate !== market.lastAskUpdate) ||
          (sharedData.lastExecution && sharedData.lastExecution !== market.lastExecution) ||
          (sharedData.ticker?.lastPrice !== market.lastPrice) ||
          (sharedData.ticker?.volume !== market.volume)
        );
      });

      if (hasUpdates) {
        setMarkets(prevMarkets => {
          return prevMarkets.map(market => {
            const sharedData = marketData[market.ticker];
            if (!sharedData) return market;

            const now = new Date();
            let updates = {};
            let cellsToFlash = {};

            // Update best prices with flashing (only flash when best price actually changes)
            if (sharedData.bestPrices) {
              if (sharedData.bestPrices.yesBuy !== market.yesBid) {
                updates.yesBid = sharedData.bestPrices.yesBuy;
                updates.yesBidTimestamp = sharedData.bestPrices.yesBuyTimestamp;
                // Only flash if this is actually a better (higher) price or a change in the best price
                cellsToFlash.bid = true;
              }
              if (sharedData.bestPrices.noBuy !== market.noBid) {
                updates.noBid = sharedData.bestPrices.noBuy;
                updates.noBidTimestamp = sharedData.bestPrices.noBuyTimestamp;
                // Only flash if this is actually a better (higher) NO price or a change in the best price
                cellsToFlash.ask = true;
              }
            }

            // Update bid/ask update info with flashing
            if (sharedData.lastBidUpdate && JSON.stringify(sharedData.lastBidUpdate) !== JSON.stringify(market.lastBidUpdate)) {
              updates.lastBidUpdate = sharedData.lastBidUpdate;
              cellsToFlash.lastBid = true;
            }
            if (sharedData.lastAskUpdate && JSON.stringify(sharedData.lastAskUpdate) !== JSON.stringify(market.lastAskUpdate)) {
              updates.lastAskUpdate = sharedData.lastAskUpdate;
              cellsToFlash.lastAsk = true;
            }

            // Update executions with flashing
            if (sharedData.lastExecution && JSON.stringify(sharedData.lastExecution) !== JSON.stringify(market.lastExecution)) {
              updates.lastExecution = sharedData.lastExecution;
              cellsToFlash.execution = true;
            }

            // Update ticker data with flashing
            if (sharedData.ticker) {
              if (sharedData.ticker.lastPrice !== market.lastPrice) {
                updates.lastPrice = sharedData.ticker.lastPrice;
                cellsToFlash.price = true;
              }
              if (sharedData.ticker.volume !== market.volume) {
                updates.volume = sharedData.ticker.volume;
                cellsToFlash.volume = true;
              }
            }

            // Flash cells if there are updates
            if (Object.keys(cellsToFlash).length > 0) {
              const flashKey = market.ticker;
              setFlashingCells(prev => ({
                ...prev,
                [flashKey]: { ...prev[flashKey], ...cellsToFlash }
              }));

              setTimeout(() => {
                setFlashingCells(prev => {
                  const newFlashing = { ...prev };
                  if (newFlashing[flashKey]) {
                    Object.keys(cellsToFlash).forEach(key => {
                      delete newFlashing[flashKey][key];
                    });
                    if (Object.keys(newFlashing[flashKey]).length === 0) {
                      delete newFlashing[flashKey];
                    }
                  }
                  return newFlashing;
                });
              }, 2000);
            }

            return Object.keys(updates).length > 0 ? { ...market, ...updates } : market;
          });
        });
      }
    }
  }, [marketData]);

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
        } else {
          // Fallback to mock markets
          const mockMarkets = getDefaultMarkets();
          setMarkets(mockMarkets);
        }
      } catch (apiError) {
        // If API fails, use mock markets
        const mockMarkets = getDefaultMarkets();
        setMarkets(mockMarkets);
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