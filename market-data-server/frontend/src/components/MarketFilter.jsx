import React, { useState, useCallback } from 'react'
import './MarketFilter.css'

const MarketFilter = ({ markets, selectedMarket, onFilter, onMarketSelect }) => {
  const [searchTerm, setSearchTerm] = useState('')

  const handleSearchChange = useCallback((e) => {
    const value = e.target.value
    setSearchTerm(value)
    onFilter(value)
  }, [onFilter])

  const handleMarketClick = (marketTicker) => {
    onMarketSelect(marketTicker)
  }

  return (
    <div className="card">
      <div className="card-header">
        <h2 className="card-title">Market Filter</h2>
        <span className="market-count">{markets.length} markets</span>
      </div>
      
      <div className="search-container">
        <input
          type="text"
          placeholder="Search markets (e.g., TRUMP, ELECTION, MARKET_MAKER)..."
          value={searchTerm}
          onChange={handleSearchChange}
          className="search-input"
        />
      </div>
      
      <div className="markets-list">
        {markets.length === 0 && searchTerm && (
          <div className="no-results">
            No markets found matching "{searchTerm}"
          </div>
        )}
        
        {markets.length === 0 && !searchTerm && (
          <div className="no-results">
            Start typing to search for markets
          </div>
        )}
        
        {markets.map((market) => (
          <div
            key={market.ticker}
            className={`market-item ${selectedMarket === market.ticker ? 'selected' : ''}`}
            onClick={() => handleMarketClick(market.ticker)}
          >
            <div className="market-header">
              <span className="market-ticker">{market.ticker}</span>
              <span className="market-status">
                {market.isActive ? 'Active' : 'Inactive'}
              </span>
            </div>
            {market.title && (
              <div className="market-title">{market.title}</div>
            )}
            <div className="market-stats">
              <span>Last Update: {new Date(market.lastUpdate).toLocaleTimeString()}</span>
              <span>Messages: {market.messageCount}</span>
            </div>
          </div>
        ))}
      </div>
    </div>
  )
}

export default MarketFilter