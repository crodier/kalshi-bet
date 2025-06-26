import React, { useState, useEffect, useRef } from 'react'
import './OrderBookViewer.css'

const OrderBookViewer = ({ marketTicker, orderBookData, connectionStatus }) => {
  const [flashingLevels, setFlashingLevels] = useState({ yes: new Set(), no: new Set() })
  const [updateStats, setUpdateStats] = useState({
    messageCount: 0,
    lastUpdate: null,
    receivedTimestamp: null,
    publishedTimestamp: null,
    latency: null
  })
  const previousDataRef = useRef(null)

  useEffect(() => {
    if (orderBookData && previousDataRef.current) {
      // Calculate latency if timestamps are available
      let latency = null
      if (orderBookData.receivedTimestamp && orderBookData.processedTimestamp) {
        latency = orderBookData.processedTimestamp - orderBookData.receivedTimestamp
      } else if (orderBookData.processingLatency) {
        latency = orderBookData.processingLatency
      }

      setUpdateStats({
        messageCount: updateStats.messageCount + 1,
        lastUpdate: new Date(),
        receivedTimestamp: orderBookData.receivedTimestamp,
        publishedTimestamp: orderBookData.processedTimestamp,
        latency: latency
      })

      // Detect flashing levels by comparing with previous data
      const newFlashing = { yes: new Set(), no: new Set() }
      
      if (previousDataRef.current) {
        // Compare YES side levels (new InternalOrderBook format)
        if (orderBookData.yesSide?.levels && previousDataRef.current.yesSide?.levels) {
          Object.entries(orderBookData.yesSide.levels).forEach(([price, levelData]) => {
            const prevLevelData = previousDataRef.current.yesSide.levels[price]
            if (!prevLevelData || prevLevelData.quantity !== levelData.quantity) {
              newFlashing.yes.add(parseInt(price))
            }
          })
        }

        // Compare NO side levels (new InternalOrderBook format)
        if (orderBookData.noSide?.levels && previousDataRef.current.noSide?.levels) {
          Object.entries(orderBookData.noSide.levels).forEach(([price, levelData]) => {
            const prevLevelData = previousDataRef.current.noSide.levels[price]
            if (!prevLevelData || prevLevelData.quantity !== levelData.quantity) {
              newFlashing.no.add(parseInt(price))
            }
          })
        }
      }

      setFlashingLevels(newFlashing)

      // Clear flashing after animation duration
      setTimeout(() => {
        setFlashingLevels({ yes: new Set(), no: new Set() })
      }, 1000)
    }

    previousDataRef.current = orderBookData
  }, [orderBookData])

  if (!marketTicker) {
    return (
      <div className="card">
        <div className="card-header">
          <h2 className="card-title">Order Book Viewer</h2>
        </div>
        <div className="no-selection">
          Select a market from the filter to view its order book
        </div>
      </div>
    )
  }

  if (connectionStatus !== 'connected') {
    return (
      <div className="card">
        <div className="card-header">
          <h2 className="card-title">Order Book Viewer</h2>
        </div>
        <div className="connection-warning">
          WebSocket not connected. Please check connection status.
        </div>
      </div>
    )
  }

  const formatTimestamp = (timestamp) => {
    if (!timestamp) return 'N/A'
    return new Date(timestamp).toLocaleTimeString([], { 
      hour12: false,
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit',
      fractionalSecondDigits: 3
    })
  }

  const renderOrderBookSide = (side, sideData, sideFlashing) => {
    if (!sideData || !sideData.levels) return null

    const levels = Object.entries(sideData.levels)
      .map(([price, levelData]) => ({ 
        price: parseInt(price), 
        quantity: levelData.quantity,
        lastUpdateTimestamp: levelData.lastUpdateTimestamp,
        lastUpdateType: levelData.lastUpdateType,
        age: levelData.age,
        isStale: levelData.isStale
      }))
      .sort((a, b) => b.price - a.price) // Sort descending for bids

    return (
      <div className={`orderbook-side ${side}`}>
        <h4>{side === 'yes' ? 'Yes (Buy)' : 'No (Buy)'} - Best: {sideData.bestPrice ? `${sideData.bestPrice}¢` : 'N/A'}</h4>
        <div className="orderbook-header">
          <span>Price</span>
          <span>Quantity</span>
          <span>Last Update</span>
          <span>Type</span>
          <span>Age</span>
        </div>
        <div className="orderbook-levels">
          {levels.map((level) => (
            <div 
              key={`${side}-${level.price}`} 
              className={`level ${side} ${sideFlashing.has(level.price) ? 'flash' : ''} ${level.isStale ? 'stale' : ''}`}
            >
              <span className="price">{level.price}¢</span>
              <span className="quantity">{level.quantity}</span>
              <span className="timestamp level-update">
                {formatTimestamp(level.lastUpdateTimestamp)}
              </span>
              <span className="update-type">{level.lastUpdateType}</span>
              <span className="age">{level.age}ms</span>
            </div>
          ))}
          {levels.length === 0 && (
            <div className="no-orders">No {side} orders</div>
          )}
        </div>
      </div>
    )
  }

  return (
    <div className="card">
      <div className="card-header">
        <h2 className="card-title">Order Book Viewer</h2>
      </div>

      <div className="market-info">
        <h3>{marketTicker}</h3>
        <div className="update-info">
          <div className="stats-row">
            <span>Messages: {updateStats.messageCount}</span>
            <span>Last Update: {updateStats.lastUpdate ? updateStats.lastUpdate.toLocaleTimeString() : 'Never'}</span>
          </div>
          {updateStats.latency !== null && (
            <div className="latency-info">
              <span>Processing Latency: {updateStats.latency}ms</span>
            </div>
          )}
        </div>
      </div>

      {!orderBookData && (
        <div className="loading">Waiting for order book data...</div>
      )}

      {orderBookData && (
        <div className="orderbook-container">
          {renderOrderBookSide('yes', orderBookData.yesSide, flashingLevels.yes)}
          {renderOrderBookSide('no', orderBookData.noSide, flashingLevels.no)}
        </div>
      )}
    </div>
  )
}

export default OrderBookViewer