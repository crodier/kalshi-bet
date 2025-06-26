import React, { useState, useEffect, useCallback } from 'react';
import ordersWebSocket from '../services/ordersWebsocket';
import './OrdersPanel.css';

const SimpleOrdersPanel = ({ selectedMarket }) => {
  const [orders, setOrders] = useState([]);
  const [isConnected, setIsConnected] = useState(false);
  const [orderStats, setOrderStats] = useState({ total: 0, open: 0, filled: 0 });
  const [connectionState, setConnectionState] = useState({
    isConnected: false,
    isConnecting: false,
    error: null
  });

  // Fetch all orders for the selected market
  const fetchOrdersForMarket = useCallback(async (marketTicker) => {
    if (!marketTicker) return;
    
    try {
      console.log(`Fetching orders for market: ${marketTicker}`);
      
      // Use the existing orders endpoint with market filter
      const response = await fetch(`/trade-api/v2/portfolio/orders?ticker=${marketTicker}&limit=1000`);
      if (!response.ok) {
        throw new Error(`Failed to fetch orders: ${response.status}`);
      }
      
      const data = await response.json();
      const ordersList = data.orders || [];
      
      console.log(`Loaded ${ordersList.length} orders for market ${marketTicker}`);
      
      // Transform orders to match our WebSocket message format
      const transformedOrders = ordersList.map(order => ({
        order_id: order.id,
        user_id: order.user_id,
        market_ticker: order.symbol,
        side: order.side,
        action: 'buy', // Default since we don't have action in the order response
        type: order.order_type,
        original_quantity: order.quantity,
        filled_quantity: order.filled_quantity,
        remaining_quantity: order.remaining_quantity,
        price: order.price,
        avg_fill_price: order.avg_fill_price,
        status: order.status,
        time_in_force: order.time_in_force,
        created_time: new Date(order.created_time).toISOString(),
        updated_time: new Date(order.updated_time).toISOString(),
        update_type: 'EXISTING'
      }));
      
      setOrders(transformedOrders);
      updateOrderStats(transformedOrders);
      
    } catch (error) {
      console.error('Failed to fetch orders:', error);
      setOrders([]);
      setOrderStats({ total: 0, open: 0, filled: 0 });
    }
  }, []);

  // Update order statistics
  const updateOrderStats = useCallback((ordersList) => {
    const stats = {
      total: ordersList.length,
      open: ordersList.filter(o => o.status === 'open').length,
      filled: ordersList.filter(o => o.status === 'filled').length,
      partial: ordersList.filter(o => o.status === 'partially_filled').length,
      canceled: ordersList.filter(o => o.status === 'canceled').length
    };
    setOrderStats(stats);
  }, []);

  // Subscribe to order updates via dedicated WebSocket
  const subscribeToOrderUpdates = useCallback((marketTicker) => {
    if (!marketTicker) return;

    console.log(`Subscribing to order updates for market: ${marketTicker}`);
    
    // Subscribe and pass the handler
    ordersWebSocket.subscribe(marketTicker, handleOrderUpdate);
  }, [handleOrderUpdate]);

  // Unsubscribe from order updates
  const unsubscribeFromOrderUpdates = useCallback((marketTicker) => {
    if (!marketTicker) return;

    console.log(`Unsubscribing from order updates for market: ${marketTicker}`);
    ordersWebSocket.unsubscribe(marketTicker);
  }, []);

  // Handle incoming order update messages
  const handleOrderUpdate = useCallback((message) => {
    if (message.type !== 'order_update') return;
    
    const orderUpdate = message.msg;
    console.log('Received order update:', orderUpdate);

    setOrders(prevOrders => {
      const existingIndex = prevOrders.findIndex(order => order.order_id === orderUpdate.order_id);
      
      if (existingIndex >= 0) {
        // Update existing order
        const updatedOrders = [...prevOrders];
        updatedOrders[existingIndex] = {
          ...updatedOrders[existingIndex],
          ...orderUpdate,
          updated_time: new Date().toISOString()
        };
        
        console.log(`Updated existing order: ${orderUpdate.order_id}`);
        updateOrderStats(updatedOrders);
        return updatedOrders;
      } else {
        // Add new order
        const newOrder = {
          ...orderUpdate,
          created_time: new Date().toISOString(),
          updated_time: new Date().toISOString()
        };
        
        const updatedOrders = [newOrder, ...prevOrders];
        console.log(`Added new order: ${orderUpdate.order_id}`);
        
        updateOrderStats(updatedOrders);
        return updatedOrders;
      }
    });
  }, [updateOrderStats]);

  // Initialize orders WebSocket connection
  useEffect(() => {
    ordersWebSocket.connect();
    
    const unsubscribe = ordersWebSocket.onConnectionStatusChange((state) => {
      setConnectionState(state);
      setIsConnected(state.isConnected);
    });
    
    return () => {
      unsubscribe();
    };
  }, []);

  // Effect to handle market selection changes
  useEffect(() => {
    // Store previous market for cleanup
    let previousMarket = null;
    
    return () => {
      if (previousMarket) {
        unsubscribeFromOrderUpdates(previousMarket);
      }
    };
  }, [unsubscribeFromOrderUpdates]);
  
  useEffect(() => {
    if (selectedMarket && connectionState.isConnected) {
      fetchOrdersForMarket(selectedMarket);
      subscribeToOrderUpdates(selectedMarket);
    } else if (!selectedMarket) {
      setOrders([]);
      setOrderStats({ total: 0, open: 0, filled: 0 });
    }
    
    return () => {
      if (selectedMarket) {
        unsubscribeFromOrderUpdates(selectedMarket);
      }
    };
  }, [selectedMarket, connectionState.isConnected, fetchOrdersForMarket, subscribeToOrderUpdates, unsubscribeFromOrderUpdates]);

  if (!selectedMarket) {
    return (
      <div className="orders-panel">
        <div className="orders-header">
          <h3>Orders</h3>
          <div className="connection-status disconnected">
            Select a market to view orders
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="orders-panel">
      <div className="orders-header">
        <h3>Orders - {selectedMarket}</h3>
        <div className="orders-stats">
          <span className="stat">Total: {orderStats.total}</span>
          <span className="stat">Open: {orderStats.open}</span>
          <span className="stat">Filled: {orderStats.filled}</span>
          {orderStats.partial > 0 && <span className="stat">Partial: {orderStats.partial}</span>}
          {orderStats.canceled > 0 && <span className="stat">Canceled: {orderStats.canceled}</span>}
        </div>
        <div className={`connection-status ${isConnected ? 'connected' : 'disconnected'}`}>
          {isConnected ? '🔗 Live' : '❌ Offline'}
        </div>
      </div>
      
      <div className="simple-orders-table" style={{ height: '400px', overflow: 'auto', background: 'white', border: '1px solid #ddd' }}>
        <table style={{ width: '100%', borderCollapse: 'collapse' }}>
          <thead style={{ position: 'sticky', top: 0, background: '#f8f9fa' }}>
            <tr>
              <th style={{ padding: '8px', border: '1px solid #ddd', textAlign: 'left' }}>Order ID</th>
              <th style={{ padding: '8px', border: '1px solid #ddd', textAlign: 'left' }}>Side</th>
              <th style={{ padding: '8px', border: '1px solid #ddd', textAlign: 'left' }}>Action</th>
              <th style={{ padding: '8px', border: '1px solid #ddd', textAlign: 'left' }}>Status</th>
              <th style={{ padding: '8px', border: '1px solid #ddd', textAlign: 'right' }}>Qty</th>
              <th style={{ padding: '8px', border: '1px solid #ddd', textAlign: 'right' }}>Filled</th>
              <th style={{ padding: '8px', border: '1px solid #ddd', textAlign: 'right' }}>Price</th>
              <th style={{ padding: '8px', border: '1px solid #ddd', textAlign: 'left' }}>Type</th>
              <th style={{ padding: '8px', border: '1px solid #ddd', textAlign: 'left' }}>Created</th>
            </tr>
          </thead>
          <tbody>
            {orders.length === 0 ? (
              <tr>
                <td colSpan="9" style={{ padding: '20px', textAlign: 'center', color: '#666' }}>
                  {isConnected ? 'No orders found for this market' : 'Loading orders...'}
                </td>
              </tr>
            ) : (
              orders.map((order, index) => (
                <tr key={order.order_id} style={{ backgroundColor: index % 2 === 0 ? '#f9f9f9' : 'white' }}>
                  <td style={{ padding: '6px 8px', border: '1px solid #eee', fontFamily: 'monospace', fontSize: '0.85em' }}>
                    {order.order_id.substring(4, 12)}
                  </td>
                  <td style={{ padding: '6px 8px', border: '1px solid #eee' }}>
                    <span className={order.side === 'yes' ? 'side-yes' : 'side-no'}>
                      {order.side.toUpperCase()}
                    </span>
                  </td>
                  <td style={{ padding: '6px 8px', border: '1px solid #eee' }}>
                    <span className={order.action === 'buy' ? 'action-buy' : 'action-sell'}>
                      {order.action.toUpperCase()}
                    </span>
                  </td>
                  <td style={{ padding: '6px 8px', border: '1px solid #eee' }}>
                    <span className={`status-${order.status.replace('_', '-')}`}>
                      {order.status.replace('_', ' ').toUpperCase()}
                    </span>
                  </td>
                  <td style={{ padding: '6px 8px', border: '1px solid #eee', textAlign: 'right' }}>
                    {order.original_quantity}
                  </td>
                  <td style={{ padding: '6px 8px', border: '1px solid #eee', textAlign: 'right' }}>
                    <span className={order.filled_quantity > 0 ? 'qty-partial' : ''}>
                      {order.filled_quantity}
                    </span>
                  </td>
                  <td style={{ padding: '6px 8px', border: '1px solid #eee', textAlign: 'right' }}>
                    {order.price}¢
                  </td>
                  <td style={{ padding: '6px 8px', border: '1px solid #eee' }}>
                    {order.type}
                  </td>
                  <td style={{ padding: '6px 8px', border: '1px solid #eee', fontSize: '0.85em' }}>
                    {new Date(order.created_time).toLocaleString()}
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
};

export default SimpleOrdersPanel;