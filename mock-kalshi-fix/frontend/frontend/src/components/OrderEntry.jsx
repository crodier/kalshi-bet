import React, { useState } from 'react';
import { orderAPI } from '../services/api';
import './OrderEntry.css';

const OrderEntry = ({ marketTicker, onOrderPlaced }) => {
  const [orderForm, setOrderForm] = useState({
    side: 'yes',
    price: '',
    count: '',
    type: 'limit'
  });
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');

  const handleInputChange = (e) => {
    const { name, value } = e.target;
    setOrderForm(prev => ({
      ...prev,
      [name]: value
    }));
    setError('');
    setSuccess('');
  };

  const validateForm = () => {
    if (!marketTicker) {
      setError('Please select a market');
      return false;
    }
    
    const price = parseInt(orderForm.price);
    if (isNaN(price) || price < 1 || price > 99) {
      setError('Price must be between 1 and 99 cents');
      return false;
    }
    
    const count = parseInt(orderForm.count);
    if (isNaN(count) || count <= 0) {
      setError('Quantity must be greater than 0');
      return false;
    }
    
    return true;
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    
    if (!validateForm()) return;
    
    setLoading(true);
    setError('');
    setSuccess('');
    
    try {
      const orderData = {
        market_ticker: marketTicker,
        side: orderForm.side,
        action: 'buy',  // Always buy in buy-only architecture
        type: orderForm.type,
        price: parseInt(orderForm.price),
        count: parseInt(orderForm.count),
        time_in_force: 'GTC'
      };
      
      const response = await orderAPI.createOrder(orderData);
      
      setSuccess(`Order placed successfully! Order ID: ${response.data.order.id}`);
      
      // Reset form
      setOrderForm(prev => ({
        ...prev,
        price: '',
        count: ''
      }));
      
      if (onOrderPlaced) {
        onOrderPlaced(response.data.order);
      }
    } catch (err) {
      // Extract error message from the new error response format
      const errorMessage = err.response?.data?.message || 
                         err.response?.data?.rootCause || 
                         err.message || 
                         'Failed to place order';
      setError(errorMessage);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="order-entry">
      <h3>Place Order</h3>
      {marketTicker ? (
        <form onSubmit={handleSubmit}>
          <div className="market-info">
            Market: <strong>{marketTicker}</strong>
          </div>
          
          <div className="form-row">
            <div className="form-group">
              <label>Side</label>
              <select name="side" value={orderForm.side} onChange={handleInputChange}>
                <option value="yes">Buy YES</option>
                <option value="no">Buy NO</option>
              </select>
            </div>
          </div>
          
          <div className="form-row">
            <div className="form-group">
              <label>Price (¢)</label>
              <input
                type="number"
                name="price"
                value={orderForm.price}
                onChange={handleInputChange}
                min="1"
                max="99"
                placeholder="1-99"
                required
              />
            </div>
            
            <div className="form-group">
              <label>Quantity</label>
              <input
                type="number"
                name="count"
                value={orderForm.count}
                onChange={handleInputChange}
                min="1"
                placeholder="Enter quantity"
                required
              />
            </div>
          </div>
          
          {error && <div className="error-message">{error}</div>}
          {success && <div className="success-message">{success}</div>}
          
          <button 
            type="submit" 
            disabled={loading}
            className={`submit-btn buy`}
          >
            {loading ? 'Placing Order...' : `BUY ${orderForm.side.toUpperCase()}`}
          </button>
        </form>
      ) : (
        <div className="no-market">Select a market to place an order</div>
      )}
    </div>
  );
};

export default OrderEntry;