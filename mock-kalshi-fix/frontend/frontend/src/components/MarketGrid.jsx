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

  useEffect(() => {
    fetchMarkets();
    
    // Subscribe to ticker updates for all markets
    const marketTickers = ['DUMMY_TEST', 'TRUMPWIN-24NOV05', 'BTCZ-23DEC31-B50000', 'INXD-23DEC29-B5000'];
    const subscriptions = [];
    
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
  }, []);

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
      { ticker: 'DUMMY_TEST', name: 'Dummy Test Market', lastPrice: 50, volume: 0 },
      { ticker: 'TRUMPWIN-24NOV05', name: 'Trump Win Nov 2024', lastPrice: 50, volume: 0 },
      { ticker: 'BTCZ-23DEC31-B50000', name: 'Bitcoin Above 50k Dec 2023', lastPrice: 50, volume: 0 },
      { ticker: 'INXD-23DEC29-B5000', name: 'S&P 500 Above 5000 Dec 2023', lastPrice: 50, volume: 0 }
    ];
    setMarkets(mockMarkets);
  };
  
  const handleTickerUpdate = (tickerData) => {
    setMarkets(prevMarkets => 
      prevMarkets.map(market => 
        market.ticker === tickerData.marketTicker
          ? { 
              ...market, 
              lastPrice: tickerData.lastPrice ? Math.round(tickerData.lastPrice / 100) : market.lastPrice,
              volume: tickerData.volume 
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
          <div className="grid-cell">Last Price</div>
          <div className="grid-cell">Volume</div>
        </div>
        {markets.map((market) => (
          <div
            key={market.ticker}
            className={`grid-row ${selectedMarket === market.ticker ? 'selected' : ''}`}
            onClick={() => handleMarketClick(market)}
          >
            <div className="grid-cell market-name">
              <div className="ticker">{market.ticker}</div>
              <div className="name">{market.name}</div>
            </div>
            <div className="grid-cell price">{market.lastPrice}¢</div>
            <div className="grid-cell volume">{market.volume.toLocaleString()}</div>
          </div>
        ))}
      </div>
    </div>
  );
};

export default MarketGrid;