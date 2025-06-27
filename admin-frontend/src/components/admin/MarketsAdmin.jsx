import { useState } from 'react';
import './MarketsAdmin.css';

export const MarketsAdmin = ({ onMarketSelect, selectedMarket }) => {
  // Mock markets - in real implementation, these would come from API/WebSocket
  const markets = [
    'MARKET_MAKER',
    'TRUMP-2024',
    'WEATHER-NYC',
    'SPORTS-NBA',
    'CRYPTO-BTC',
    'STOCKS-SPY',
    'ELECTION-SENATE',
    'TECH-AAPL'
  ];

  const handleMarketChange = (event) => {
    const market = event.target.value;
    if (onMarketSelect) {
      onMarketSelect(market);
    }
  };

  return (
    <div className="markets-admin">
      <div className="markets-header">
        <h3>Market Administration</h3>
        <div className="market-selector">
          <label htmlFor="market-select">Select Market:</label>
          <select 
            id="market-select"
            value={selectedMarket || ''} 
            onChange={handleMarketChange}
            className="market-dropdown"
          >
            <option value="">-- Select Market --</option>
            {markets.map(market => (
              <option key={market} value={market}>{market}</option>
            ))}
          </select>
        </div>
      </div>
      
      {selectedMarket && (
        <div className="market-info">
          <div className="market-details">
            <h4>Market: {selectedMarket}</h4>
            <div className="market-stats">
              <span className="stat">Status: Active</span>
              <span className="stat">Volume: 1,234</span>
              <span className="stat">Open Interest: 567</span>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};