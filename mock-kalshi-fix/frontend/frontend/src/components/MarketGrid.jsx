import React, { useState, useEffect } from 'react';
import { marketAPI } from '../services/api';
import websocketService from '../services/websocket';
import './MarketGrid.css';

const MarketGrid = ({ onMarketSelect }) => {
  const [markets, setMarkets] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [selectedMarket, setSelectedMarket] = useState(null);
  const [tickerSubscriptions, setTickerSubscriptions] = useState([]);
  const [flashingMarkets, setFlashingMarkets] = useState(new Set());
  const [flashingCells, setFlashingCells] = useState({}); // Track which cells are flashing

  useEffect(() => {
    fetchMarkets();
  }, []);

  // Subscribe to WebSocket updates whenever markets change
  useEffect(() => {
    if (markets.length === 0) return;

    // Unsubscribe from previous subscriptions
    tickerSubscriptions.forEach(subId => {
      websocketService.unsubscribe(subId);
    });

    // Subscribe to both ticker and orderbook updates for all markets
    const subscriptions = [];
    const marketTickers = markets.map(m => m.ticker);
    
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
            handleOrderbookUpdate(ticker, message.msg);
          }
        }
      );
      subscriptions.push(orderbookSubId);
    });
    
    setTickerSubscriptions(subscriptions);
    
    // Cleanup subscriptions on unmount
    return () => {
      subscriptions.forEach(subId => {
        websocketService.unsubscribe(subId);
      });
    };
  }, [markets]);

  const fetchMarkets = async () => {
    try {
      setLoading(true);
      // Try to fetch markets from API first
      try {
        const response = await marketAPI.getMarkets();
        if (response.data && response.data.markets) {
          // Map API response to our format
          const apiMarkets = response.data.markets.map(market => ({
            ticker: market.ticker,
            name: market.title || market.ticker,
            lastPrice: market.last_price ? Math.round(market.last_price / 100) : 50,
            yesBid: market.yes_bid ? Math.round(market.yes_bid / 100) : null,
            noBid: market.no_bid ? Math.round(market.no_bid / 100) : null,
            volume: market.volume || 0
          }));
          setMarkets(apiMarkets);
        } else {
          // Fallback to mock markets
          setDefaultMarkets();
        }
      } catch (apiError) {
        // If API fails, use mock markets
        setDefaultMarkets();
      }
      setLoading(false);
    } catch (err) {
      setError(err.message);
      setLoading(false);
    }
  };
  
  const setDefaultMarkets = () => {
    const mockMarkets = [
      { ticker: 'DUMMY_TEST', name: 'Dummy Test Market', lastPrice: 50, yesBid: null, noBid: null, volume: 0 },
      { ticker: 'TRUMPWIN-24NOV05', name: 'Trump Win Nov 2024', lastPrice: 50, yesBid: null, noBid: null, volume: 0 },
      { ticker: 'BTCZ-23DEC31-B50000', name: 'Bitcoin Above 50k Dec 2023', lastPrice: 50, yesBid: null, noBid: null, volume: 0 },
      { ticker: 'INXD-23DEC29-B5000', name: 'S&P 500 Above 5000 Dec 2023', lastPrice: 50, yesBid: null, noBid: null, volume: 0 },
      { ticker: 'MARKET_MAKER', name: 'Market Maker Test', lastPrice: 50, yesBid: null, noBid: null, volume: 0 }
    ];
    setMarkets(mockMarkets);
  };
  
  const handleOrderbookUpdate = (ticker, orderbookData) => {
    // Extract best YES buy and best NO buy from orderbook
    const currentMarket = markets.find(m => m.ticker === ticker);
    if (!currentMarket) return;
    
    let bestYesBuy = null;
    let bestNoBuy = null;
    
    // Get best YES buy (first entry in yes array)
    if (orderbookData.yes && orderbookData.yes.length > 0) {
      bestYesBuy = orderbookData.yes[0][0]; // Price is first element
    }
    
    // Get best NO buy (first entry in no array)
    if (orderbookData.no && orderbookData.no.length > 0) {
      bestNoBuy = orderbookData.no[0][0]; // Price is first element
    }
    
    const cellsToFlash = {};
    
    // Check if YES buy changed
    if (bestYesBuy !== null && bestYesBuy !== currentMarket.yesBid) {
      cellsToFlash.bid = true;
    }
    
    // Check if NO buy changed
    if (bestNoBuy !== null && bestNoBuy !== currentMarket.noBid) {
      cellsToFlash.ask = true;
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
    
    // Update market with new best prices
    setMarkets(prevMarkets => 
      prevMarkets.map(market => 
        market.ticker === ticker
          ? { 
              ...market, 
              yesBid: bestYesBuy !== null ? bestYesBuy : market.yesBid,
              noBid: bestNoBuy !== null ? bestNoBuy : market.noBid
            }
          : market
      )
    );
  };
  
  const handleTickerUpdate = (tickerData) => {
    // Handle ticker updates for last price and volume only
    const currentMarket = markets.find(m => m.ticker === tickerData.marketTicker);
    if (!currentMarket) return;
    
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

    setMarkets(prevMarkets => 
      prevMarkets.map(market => 
        market.ticker === tickerData.marketTicker
          ? { 
              ...market, 
              lastPrice: newPrice !== null ? newPrice : market.lastPrice,
              volume: newVolume !== undefined ? newVolume : market.volume
            }
          : market
      )
    );
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
          <div className="grid-cell">Last</div>
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
                {market.yesBid !== null ? `${market.yesBid}¢` : '-'}
              </div>
              <div className={`grid-cell ask ${cellFlash.ask ? 'flash-red' : ''}`}>
                {market.noBid !== null ? `${market.noBid}¢` : '-'}
              </div>
              <div className={`grid-cell price ${cellFlash.price ? 'flash-update' : ''}`}>
                {market.lastPrice}¢
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