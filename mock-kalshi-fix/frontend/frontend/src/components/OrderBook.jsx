import React, { useState, useEffect } from 'react';
import { marketAPI } from '../services/api';
import websocketService from '../services/websocket';
import './OrderBook.css';

const OrderBook = ({ marketTicker }) => {
  const [orderbook, setOrderbook] = useState({ bids: [], asks: [] });
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [subscriptionId, setSubscriptionId] = useState(null);
  const [flashingLevels, setFlashingLevels] = useState({ bids: new Set(), asks: new Set() });
  const [levelTimestamps, setLevelTimestamps] = useState({ bids: {}, asks: {} }); // Track timestamps for each price level
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

    // Fetch initial orderbook
    fetchOrderbook();

    // Subscribe to WebSocket updates
    const subId = subscribeToOrderbook();
    setSubscriptionId(subId);

    // Cleanup on unmount or market change
    return () => {
      if (subId) {
        websocketService.unsubscribe(subId);
      }
    };
  }, [marketTicker]);

  const fetchOrderbook = async () => {
    try {
      setLoading(true);
      console.log('Fetching orderbook for market:', marketTicker);
      const response = await marketAPI.getOrderbook(marketTicker);
      console.log('Orderbook response:', response);
      const data = response.data;
      
      // Process the orderbook data
      processOrderbookSnapshot(data.orderbook);
      setLoading(false);
    } catch (err) {
      console.error('Error fetching orderbook:', err);
      console.error('Error details:', err.response);
      setError(err.response?.data?.message || err.message);
      setLoading(false);
    }
  };

  const subscribeToOrderbook = () => {
    return websocketService.subscribe(
      ['orderbook_snapshot', 'orderbook_delta'],
      [marketTicker],
      (message) => {
        if (message.msg && message.msg.market_ticker === marketTicker) {
          // Update status based on message type
          const now = new Date();
          
          if (message.type === 'orderbook_snapshot') {
            setUpdateStatus(prev => ({
              type: message.type,
              timestamp: now,
              details: 'Full Snapshot',
              messageCount: prev.messageCount + 1,
              deltaCount: prev.deltaCount,
              snapshotCount: prev.snapshotCount + 1,
              lastDelta: null
            }));
            processOrderbookSnapshot(message.msg);
          } else if (message.type === 'orderbook_delta') {
            const deltaDetails = {
              price: message.msg.price,
              delta: message.msg.delta,
              side: message.msg.side,
              action: message.msg.delta > 0 ? 'ADD' : 'REMOVE'
            };
            
            setUpdateStatus(prev => ({
              type: message.type,
              timestamp: now,
              details: `${deltaDetails.action} ${Math.abs(deltaDetails.delta)} @ ${deltaDetails.price}¢`,
              messageCount: prev.messageCount + 1,
              deltaCount: prev.deltaCount + 1,
              snapshotCount: prev.snapshotCount,
              lastDelta: deltaDetails
            }));
            processOrderbookDelta(message.msg);
          }
        }
      }
    );
  };

  const processOrderbookSnapshot = (orderbookData) => {
    // The API now returns data in Kalshi format with separated YES and NO sides
    // Structure: {"yes": [[price, quantity], ...], "no": [[price, quantity], ...]}
    // YES side contains Buy YES orders (bids)
    // NO side contains Buy NO orders
    
    const bids = [];
    const asks = [];
    const newFlashingBids = new Set();
    const newFlashingAsks = new Set();
    const now = new Date();
    const newBidTimestamps = {};
    const newAskTimestamps = {};
    
    // Process YES side (Buy YES orders) - these are bids
    const yesOrders = orderbookData.yes || [];
    yesOrders.forEach((level) => {
      const [price, quantity] = level;
      bids.push({ price, quantity });
      
      // Check if this level changed
      const existingBid = orderbook.bids.find(b => b.price === price);
      if (!existingBid || existingBid.quantity !== quantity) {
        newFlashingBids.add(price);
        newBidTimestamps[price] = now;
      } else {
        // Keep existing timestamp if level didn't change
        newBidTimestamps[price] = levelTimestamps.bids[price] || now;
      }
    });
    
    // Process NO side (Buy NO orders)
    // In buy-only architecture, we display NO orders at their actual price
    const noOrders = orderbookData.no || [];
    noOrders.forEach((level) => {
      const [noPrice, quantity] = level;
      // Display Buy NO orders at their actual price
      asks.push({ price: noPrice, quantity });
      
      // Check if this level changed
      const existingAsk = orderbook.asks.find(a => a.price === noPrice);
      if (!existingAsk || existingAsk.quantity !== quantity) {
        newFlashingAsks.add(noPrice);
        newAskTimestamps[noPrice] = now;
      } else {
        // Keep existing timestamp if level didn't change
        newAskTimestamps[noPrice] = levelTimestamps.asks[noPrice] || now;
      }
    });

    // Sort both sides descending (highest first) since they are both BUY orders
    bids.sort((a, b) => b.price - a.price);  // Buy YES - highest first
    asks.sort((a, b) => b.price - a.price);  // Buy NO - highest first

    setOrderbook({ bids, asks });
    setLevelTimestamps({ bids: newBidTimestamps, asks: newAskTimestamps });
    
    // For snapshots, flash everything briefly
    setFlashingLevels({ bids: new Set(bids.map(b => b.price)), asks: new Set(asks.map(a => a.price)) });
    
    setTimeout(() => {
      setFlashingLevels({ bids: new Set(), asks: new Set() });
    }, 2000); // Match CSS animation duration
  };
  
  const processOrderbookDelta = (deltaData) => {
    // Delta contains: price, delta (quantity change), side
    const { price, delta, side } = deltaData;
    const now = new Date();
    
    setOrderbook(prev => {
      const newBids = [...prev.bids];
      const newAsks = [...prev.asks];
      const newFlashingBids = new Set();
      const newFlashingAsks = new Set();
      
      if (side === 'yes') {
        // Update YES side (bids)
        const existingIndex = newBids.findIndex(b => b.price === price);
        
        if (delta > 0) {
          // Add or increase quantity
          if (existingIndex >= 0) {
            newBids[existingIndex].quantity += delta;
          } else {
            newBids.push({ price, quantity: delta });
            newBids.sort((a, b) => b.price - a.price);
          }
          newFlashingBids.add(price);
        } else if (delta < 0) {
          // Decrease quantity
          if (existingIndex >= 0) {
            newBids[existingIndex].quantity += delta; // delta is negative
            if (newBids[existingIndex].quantity <= 0) {
              newBids.splice(existingIndex, 1);
            } else {
              newFlashingBids.add(price);
            }
          }
        }
      } else if (side === 'no') {
        // Update NO side (asks)
        const existingIndex = newAsks.findIndex(a => a.price === price);
        
        if (delta > 0) {
          // Add or increase quantity
          if (existingIndex >= 0) {
            newAsks[existingIndex].quantity += delta;
          } else {
            newAsks.push({ price, quantity: delta });
            newAsks.sort((a, b) => b.price - a.price);
          }
          newFlashingAsks.add(price);
        } else if (delta < 0) {
          // Decrease quantity
          if (existingIndex >= 0) {
            newAsks[existingIndex].quantity += delta; // delta is negative
            if (newAsks[existingIndex].quantity <= 0) {
              newAsks.splice(existingIndex, 1);
            } else {
              newFlashingAsks.add(price);
            }
          }
        }
      }
      
      // Flash only the changed level
      if (newFlashingBids.size > 0 || newFlashingAsks.size > 0) {
        setFlashingLevels({ bids: newFlashingBids, asks: newFlashingAsks });
        
        setTimeout(() => {
          setFlashingLevels({ bids: new Set(), asks: new Set() });
        }, 2000);
      }
      
      return { bids: newBids, asks: newAsks };
    });
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