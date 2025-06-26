import React, { useState, useEffect } from 'react';
import { marketAPI } from '../services/api';
import websocketService from '../services/websocket';
import './OrderBook.css';

const OrderBook = ({ marketTicker }) => {
  const [orderbook, setOrderbook] = useState({ bids: [], asks: [] });
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [subscriptionId, setSubscriptionId] = useState(null);

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
      processOrderbook(data.orderbook);
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
          processOrderbook(message.msg);
        }
      }
    );
  };

  const processOrderbook = (orderbookData) => {
    // The API now returns data in Kalshi format with separated YES and NO sides
    // Structure: {"yes": [[price, quantity], ...], "no": [[price, quantity], ...]}
    // YES side contains Buy YES orders (bids)
    // NO side contains Buy NO orders
    
    const bids = [];
    const asks = [];
    
    // Process YES side (Buy YES orders) - these are bids
    const yesOrders = orderbookData.yes || [];
    yesOrders.forEach((level) => {
      const [price, quantity] = level;
      bids.push({ price, quantity });
    });
    
    // Process NO side (Buy NO orders)
    // In buy-only architecture, we display NO orders at their actual price
    const noOrders = orderbookData.no || [];
    noOrders.forEach((level) => {
      const [noPrice, quantity] = level;
      // Display Buy NO orders at their actual price
      asks.push({ price: noPrice, quantity });
    });

    // Sort bids descending (highest first) and asks ascending (lowest first)
    bids.sort((a, b) => b.price - a.price);
    asks.sort((a, b) => a.price - b.price);

    setOrderbook({ bids, asks });
  };

  if (!marketTicker) {
    return <div className="orderbook-empty">Select a market to view orderbook</div>;
  }

  if (loading) return <div className="loading">Loading orderbook...</div>;
  if (error) return <div className="error">Error loading orderbook: {error}</div>;

  return (
    <div className="orderbook">
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
              <div key={`bid-${index}`} className="level bid">
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
              <div key={`ask-${index}`} className="level ask">
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