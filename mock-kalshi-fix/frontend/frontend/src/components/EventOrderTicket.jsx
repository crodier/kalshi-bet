import React, { useState, useEffect } from 'react';
import { marketAPI, orderAPI } from '../services/api';
import { useMarketData } from '../contexts/MarketDataContext';
import './EventOrderTicket.css';

const EventOrderTicket = ({ marketTicker, userId = 'user123' }) => {
  const { subscribeToMarket, getMarketData } = useMarketData();
  const [orderForm, setOrderForm] = useState({
    side: 'yes',
    quantity: 1,
    price: null
  });
  const [loading, setLoading] = useState(false);
  const [message, setMessage] = useState(null);

  useEffect(() => {
    if (!marketTicker) return;

    // Subscribe to market data through the shared context
    subscribeToMarket(marketTicker);
    
    // Also fetch initial data via REST
    fetchTopOfBook();
  }, [marketTicker, subscribeToMarket]);

  const fetchTopOfBook = async () => {
    try {
      const response = await marketAPI.getOrderbook(marketTicker);
      const data = response.data;
      // Initial data is now handled by the shared context, but we could still process it here if needed
      console.log('EventOrderTicket: Fetched initial orderbook data');
    } catch (err) {
      console.error('Error fetching orderbook:', err);
    }
  };

  // Get current market data from shared context
  const marketData = getMarketData(marketTicker);
  const topOfBook = {
    yesTop: marketData.crossingPrices?.yes || null,
    noTop: marketData.crossingPrices?.no || null
  };

  const handleSideSelect = (side) => {
    setOrderForm(prev => ({
      ...prev,
      side,
      price: side === 'yes' ? topOfBook.yesTop?.price : topOfBook.noTop?.price
    }));
  };

  const handleQuantityChange = (e) => {
    const value = parseInt(e.target.value) || 0;
    setOrderForm(prev => ({ ...prev, quantity: Math.max(1, value) }));
  };

  const handlePriceChange = (e) => {
    const value = parseInt(e.target.value) || 0;
    setOrderForm(prev => ({ ...prev, price: Math.min(99, Math.max(1, value)) }));
  };

  const calculateCost = () => {
    if (!orderForm.price) return 0;
    return (orderForm.price * orderForm.quantity / 100).toFixed(2);
  };

  const calculatePotentialReturn = () => {
    return orderForm.quantity.toFixed(2);
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!orderForm.price || orderForm.price < 1 || orderForm.price > 99) {
      setMessage({ type: 'error', text: 'Please enter a valid price between 1¢ and 99¢' });
      return;
    }

    setLoading(true);
    setMessage(null);

    try {
      const orderData = {
        market_ticker: marketTicker,
        side: orderForm.side,
        action: 'buy',
        type: 'limit',
        price: orderForm.price,
        count: orderForm.quantity,
        time_in_force: 'GTC'
      };

      await orderAPI.createOrder(orderData);
      setMessage({ type: 'success', text: `Buy ${orderForm.side.toUpperCase()} order placed successfully!` });
      
      // Reset form
      setOrderForm(prev => ({ ...prev, quantity: 1, price: null }));
      
      // Market data will be updated automatically via WebSocket
    } catch (err) {
      console.error('Error placing order:', err);
      setMessage({ type: 'error', text: err.response?.data?.error || 'Failed to place order' });
    } finally {
      setLoading(false);
    }
  };

  if (!marketTicker) {
    return <div className="event-order-ticket empty">Select a market to place orders</div>;
  }

  return (
    <div className="event-order-ticket">
      <h3 className="ticket-title">Market Cross Order</h3>
      
      <div className="side-selector">
        <button 
          className={`side-button yes ${orderForm.side === 'yes' ? 'active' : ''}`}
          onClick={() => handleSideSelect('yes')}
        >
          <div className="side-label">Buy YES</div>
          <div className="side-price">
            {topOfBook.yesTop ? `${topOfBook.yesTop.price}¢` : '--¢'}
          </div>
          {topOfBook.yesTop && (
            <>
              <div className="side-quantity">({topOfBook.yesTop.quantity} available)</div>
              <div className="crossing-info">Crosses NO @ {topOfBook.yesTop.originalNoPrice}¢</div>
            </>
          )}
        </button>
        
        <button 
          className={`side-button no ${orderForm.side === 'no' ? 'active' : ''}`}
          onClick={() => handleSideSelect('no')}
        >
          <div className="side-label">Buy NO</div>
          <div className="side-price">
            {topOfBook.noTop ? `${topOfBook.noTop.price}¢` : '--¢'}
          </div>
          {topOfBook.noTop && (
            <>
              <div className="side-quantity">({topOfBook.noTop.quantity} available)</div>
              <div className="crossing-info">Crosses YES @ {topOfBook.noTop.originalYesPrice}¢</div>
            </>
          )}
        </button>
      </div>

      <form onSubmit={handleSubmit} className="order-form">
        <div className="form-group">
          <label>Quantity</label>
          <input
            type="number"
            min="1"
            value={orderForm.quantity}
            onChange={handleQuantityChange}
            className="quantity-input"
          />
        </div>

        <div className="form-group">
          <label>Limit Price</label>
          <div className="price-input-wrapper">
            <input
              type="number"
              min="1"
              max="99"
              value={orderForm.price || ''}
              onChange={handlePriceChange}
              placeholder="Enter price"
              className="price-input"
            />
            <span className="price-unit">¢</span>
          </div>
        </div>

        <div className="order-summary">
          <div className="summary-row">
            <span>Cost:</span>
            <span className="summary-value">${calculateCost()}</span>
          </div>
          <div className="summary-row">
            <span>Potential Return:</span>
            <span className="summary-value">${calculatePotentialReturn()}</span>
          </div>
          <div className="summary-row">
            <span>Potential Profit:</span>
            <span className="summary-value profit">
              ${(calculatePotentialReturn() - calculateCost()).toFixed(2)}
            </span>
          </div>
        </div>

        <button 
          type="submit" 
          disabled={loading || !orderForm.price}
          className={`submit-button ${orderForm.side}`}
        >
          {loading ? 'Placing Order...' : `Buy ${orderForm.side.toUpperCase()}`}
        </button>
      </form>

      {message && (
        <div className={`message ${message.type}`}>
          {message.text}
        </div>
      )}
    </div>
  );
};

export default EventOrderTicket;