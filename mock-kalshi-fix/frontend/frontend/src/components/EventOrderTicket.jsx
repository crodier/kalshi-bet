import React, { useState, useEffect } from 'react';
import { marketAPI, orderAPI } from '../services/api';
import websocketService from '../services/websocket';
import './EventOrderTicket.css';

const EventOrderTicket = ({ marketTicker, userId = 'user123' }) => {
  const [topOfBook, setTopOfBook] = useState({
    yesTop: null,
    noTop: null
  });
  const [orderForm, setOrderForm] = useState({
    side: 'yes',
    quantity: 1,
    price: null
  });
  const [loading, setLoading] = useState(false);
  const [message, setMessage] = useState(null);
  const [subscriptionId, setSubscriptionId] = useState(null);

  useEffect(() => {
    if (!marketTicker) return;

    fetchTopOfBook();
    const subId = subscribeToOrderbook();
    setSubscriptionId(subId);

    return () => {
      if (subId) {
        websocketService.unsubscribe(subId);
      }
    };
  }, [marketTicker]);

  const fetchTopOfBook = async () => {
    try {
      const response = await marketAPI.getOrderbook(marketTicker);
      const data = response.data;
      processTopOfBook(data.orderbook);
    } catch (err) {
      console.error('Error fetching orderbook:', err);
    }
  };

  const subscribeToOrderbook = () => {
    return websocketService.subscribe(
      ['orderbook_snapshot', 'orderbook_delta'],
      [marketTicker],
      (message) => {
        if (message.msg && message.msg.market_ticker === marketTicker) {
          processTopOfBook(message.msg);
        }
      }
    );
  };

  const processTopOfBook = (orderbookData) => {
    const yesOrders = orderbookData.yes || [];
    const noOrders = orderbookData.no || [];
    
    console.log('Processing orderbook:', { yesOrders, noOrders });
    
    // For crossing the market:
    // To buy YES immediately: need to cross with the best NO order
    // The crossing price for YES = 100 - (best NO price)
    const crossingYes = noOrders.length > 0 ? {
      price: 100 - noOrders[0][0],  // If NO is at 25¢, YES crosses at 75¢
      quantity: noOrders[0][1],
      originalNoPrice: noOrders[0][0]
    } : null;
    
    // To buy NO immediately: need to cross with the best YES order  
    // The crossing price for NO = 100 - (best YES price)
    const crossingNo = yesOrders.length > 0 ? {
      price: 100 - yesOrders[0][0],  // If YES is at 60¢, NO crosses at 40¢
      quantity: yesOrders[0][1],
      originalYesPrice: yesOrders[0][0]
    } : null;
    
    console.log('Crossing prices:', { crossingYes, crossingNo });
    
    setTopOfBook({ yesTop: crossingYes, noTop: crossingNo });
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
      
      // Refresh orderbook
      fetchTopOfBook();
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