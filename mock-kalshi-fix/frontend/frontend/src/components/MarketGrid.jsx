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

    // Subscribe to ticker updates for all markets
    const subscriptions = [];
    const marketTickers = markets.map(m => m.ticker);
    
    marketTickers.forEach(ticker => {
      const subId = websocketService.subscribe(
        ['ticker'],
        [ticker],
        (message) => {
          if (message.msg && message.msg.marketTicker === ticker) {
            handleTickerUpdate(message.msg);
          }
        }
      );
      subscriptions.push(subId);
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
            yesAsk: market.yes_ask ? Math.round(market.yes_ask / 100) : null,
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
      { ticker: 'DUMMY_TEST', name: 'Dummy Test Market', lastPrice: 50, yesBid: null, yesAsk: null, volume: 0 },
      { ticker: 'TRUMPWIN-24NOV05', name: 'Trump Win Nov 2024', lastPrice: 50, yesBid: null, yesAsk: null, volume: 0 },
      { ticker: 'BTCZ-23DEC31-B50000', name: 'Bitcoin Above 50k Dec 2023', lastPrice: 50, yesBid: null, yesAsk: null, volume: 0 },
      { ticker: 'INXD-23DEC29-B5000', name: 'S&P 500 Above 5000 Dec 2023', lastPrice: 50, yesBid: null, yesAsk: null, volume: 0 },
      { ticker: 'MARKET_MAKER', name: 'Market Maker Test', lastPrice: 50, yesBid: null, yesAsk: null, volume: 0 }
    ];
    setMarkets(mockMarkets);
  };
  
  const handleTickerUpdate = (tickerData) => {
    // Check if values changed
    const currentMarket = markets.find(m => m.ticker === tickerData.marketTicker);
    if (!currentMarket) return;
    
    const newPrice = tickerData.lastPrice ? Math.round(tickerData.lastPrice / 100) : null;
    const newBid = tickerData.bestBid ? Math.round(tickerData.bestBid / 100) : null;
    const newAsk = tickerData.bestAsk ? Math.round(tickerData.bestAsk / 100) : null;
    const newVolume = tickerData.volume;
    
    const cellsToFlash = {};
    
    // Check each field for changes
    if (newPrice !== null && newPrice !== currentMarket.lastPrice) {
      cellsToFlash.price = true;
    }
    if (newBid !== null && newBid !== currentMarket.yesBid) {
      cellsToFlash.bid = true;
    }
    if (newAsk !== null && newAsk !== currentMarket.yesAsk) {
      cellsToFlash.ask = true;
    }
    if (newVolume !== undefined && newVolume !== currentMarket.volume) {
      cellsToFlash.volume = true;
    }
    
    // Flash specific cells that changed
    if (Object.keys(cellsToFlash).length > 0) {
      const flashKey = `${tickerData.marketTicker}`;
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

    setMarkets(prevMarkets => 
      prevMarkets.map(market => 
        market.ticker === tickerData.marketTicker
          ? { 
              ...market, 
              lastPrice: newPrice !== null ? newPrice : market.lastPrice,
              yesBid: newBid !== null ? newBid : market.yesBid,
              yesAsk: newAsk !== null ? newAsk : market.yesAsk,
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
          <div className="grid-cell">Bid</div>
          <div className="grid-cell">Ask</div>
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
                {market.yesAsk !== null ? `${market.yesAsk}¢` : '-'}
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