import React, { useState, useEffect } from 'react';
import { marketAPI } from '../services/api';
import { useMarketData } from '../contexts/MarketDataContext';
import './OrderBook.css';

const OrderBook = ({ marketTicker }) => {
  const { subscribeToMarket, getMarketData } = useMarketData();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [flashingLevels, setFlashingLevels] = useState({ bids: new Set(), asks: new Set() });
  const [levelTimestamps, setLevelTimestamps] = useState({ bids: {}, asks: {} });
  const [updateStatus, setUpdateStatus] = useState({ 
    type: '', 
    timestamp: null, 
    details: '',
    messageCount: 0,
    deltaCount: 0,
    snapshotCount: 0,
    lastDelta: null
  });

  useEffect(() => {
    if (!marketTicker) return;

    // Subscribe to market data through the shared context
    subscribeToMarket(marketTicker);
    
    // Fetch initial orderbook
    fetchOrderbook();
  }, [marketTicker, subscribeToMarket]);

  // Update orderbook from shared market data with flashing
  const [orderbook, setOrderbook] = useState({ bids: [], asks: [] });
  
  useEffect(() => {
    if (!marketTicker) return;
    
    const marketData = getMarketData(marketTicker);
    if (marketData.orderbook) {
      const orderbookData = marketData.orderbook;
      
      // Process the orderbook data
      const bids = [];
      const asks = [];
      const newFlashingBids = new Set();
      const newFlashingAsks = new Set();
      const now = new Date();
      
      // Process YES side (Buy YES orders) - these are bids
      const yesOrders = orderbookData.yes || [];
      yesOrders.forEach((level) => {
        const [price, quantity] = level;
        bids.push({ price, quantity });
        
        // Check if this level changed from previous orderbook
        const existingBid = orderbook.bids.find(b => b.price === price);
        if (!existingBid || existingBid.quantity !== quantity) {
          newFlashingBids.add(price);
        }
      });
      
      // Process NO side (Buy NO orders)
      const noOrders = orderbookData.no || [];
      noOrders.forEach((level) => {
        const [noPrice, quantity] = level;
        asks.push({ price: noPrice, quantity });
        
        // Check if this level changed from previous orderbook
        const existingAsk = orderbook.asks.find(a => a.price === noPrice);
        if (!existingAsk || existingAsk.quantity !== quantity) {
          newFlashingAsks.add(noPrice);
        }
      });
      
      // Sort both sides descending (highest first)
      bids.sort((a, b) => b.price - a.price);
      asks.sort((a, b) => b.price - a.price);
      
      setOrderbook({ bids, asks });
      
      // Flash changed levels
      if (orderbookData.updateType === 'snapshot') {
        // For snapshots, flash everything briefly
        setFlashingLevels({ bids: new Set(bids.map(b => b.price)), asks: new Set(asks.map(a => a.price)) });
      } else {
        // For deltas, flash only changed levels
        setFlashingLevels({ bids: newFlashingBids, asks: newFlashingAsks });
      }
      
      // Remove flashing after animation
      setTimeout(() => {
        setFlashingLevels({ bids: new Set(), asks: new Set() });
      }, 2000);
      
      // Update status based on update type
      const isSnapshot = orderbookData.updateType === 'snapshot';
      const isDelta = orderbookData.updateType === 'delta';
      
      setUpdateStatus(prev => ({
        type: orderbookData.updateType || 'snapshot',
        timestamp: now,
        details: isDelta && orderbookData.deltaInfo ? 
          `${orderbookData.deltaInfo.action} ${orderbookData.deltaInfo.quantity} @ ${orderbookData.deltaInfo.price}¢` : 
          (isDelta ? `Delta Update - ${newFlashingBids.size + newFlashingAsks.size} levels changed` : 'Full Snapshot'),
        messageCount: prev.messageCount + 1,
        deltaCount: isDelta ? prev.deltaCount + 1 : prev.deltaCount,
        snapshotCount: isSnapshot ? prev.snapshotCount + 1 : prev.snapshotCount,
        lastDelta: isDelta && orderbookData.deltaInfo ? {
          side: orderbookData.deltaInfo.side,
          action: orderbookData.deltaInfo.action,
          delta: orderbookData.deltaInfo.quantity,
          price: orderbookData.deltaInfo.price
        } : prev.lastDelta
      }));
      
      setLoading(false);
    }
  }, [marketTicker, getMarketData]);

  const fetchOrderbook = async () => {
    try {
      setLoading(true);
      console.log('Fetching orderbook for market:', marketTicker);
      const response = await marketAPI.getOrderbook(marketTicker);
      console.log('Orderbook response:', response);
      // Data is now handled by the shared context
      setLoading(false);
    } catch (err) {
      console.error('Error fetching orderbook:', err);
      console.error('Error details:', err.response);
      setError(err.response?.data?.message || err.message);
      setLoading(false);
    }
  };


  if (!marketTicker) {
    return <div className="orderbook-empty">Select a market to view orderbook</div>;
  }

  if (loading) return <div className="loading">Loading orderbook...</div>;
  if (error) return <div className="error">Error loading orderbook: {error}</div>;

  return (
    <div className="orderbook">
      <div className="orderbook-status">
        <div className="status-line">
          <span className={`status-type ${updateStatus.type === 'orderbook_snapshot' ? 'snapshot' : 'delta'}`}>
            {updateStatus.type === 'orderbook_snapshot' ? '📊 SNAPSHOT' : '🔄 DELTA'}
          </span>
          <span className="status-details">
            {updateStatus.type === 'orderbook_snapshot' ? 
              `Timestamp: ${updateStatus.timestamp?.toLocaleTimeString('en-US', { 
                hour12: false, 
                hour: '2-digit', 
                minute: '2-digit', 
                second: '2-digit',
                fractionalSecondDigits: 3
              })}` :
              updateStatus.details
            } • Messages: {updateStatus.messageCount} • 
            Deltas: {updateStatus.deltaCount} • Snapshots: {updateStatus.snapshotCount}
          </span>
        </div>
      </div>
      
      {/* Dedicated Delta Indicators Area */}
      <div className="delta-indicators-area">
        {updateStatus.lastDelta && (
          <div className={`delta-indicator delta-${updateStatus.lastDelta.side}`}>
            <span className="delta-side">{updateStatus.lastDelta.side.toUpperCase()}:</span>
            <span className="delta-action">{updateStatus.lastDelta.action}</span>
            <span className="delta-quantity">{Math.abs(updateStatus.lastDelta.delta)}</span>
            <span className="delta-price">@ {updateStatus.lastDelta.price}¢</span>
          </div>
        )}
      </div>
      
      <h3>Order Book - {marketTicker}</h3>
      <div className="orderbook-container">
        <div className="orderbook-side bids">
          <h4>Yes (Buy)</h4>
          <div className="orderbook-header">
            <span>Price</span>
            <span>Quantity</span>
          </div>
          <div className="orderbook-levels">
            {orderbook.bids.map((level, index) => (
              <div key={`bid-${index}`} className={`level bid ${flashingLevels.bids.has(level.price) ? 'flash-green' : ''}`}>
                <span className="price">{level.price}¢</span>
                <span className="quantity">{level.quantity}</span>
              </div>
            ))}
            {orderbook.bids.length === 0 && (
              <div className="no-orders">No buy orders</div>
            )}
          </div>
        </div>
        
        <div className="orderbook-side asks">
          <h4>No (Buy)</h4>
          <div className="orderbook-header">
            <span>Price</span>
            <span>Quantity</span>
          </div>
          <div className="orderbook-levels">
            {orderbook.asks.map((level, index) => (
              <div key={`ask-${index}`} className={`level ask ${flashingLevels.asks.has(level.price) ? 'flash-red' : ''}`}>
                <span className="price">{level.price}¢</span>
                <span className="quantity">{level.quantity}</span>
              </div>
            ))}
            {orderbook.asks.length === 0 && (
              <div className="no-orders">No sell orders</div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
};

export default OrderBook;