import React, { useState, useEffect, useCallback, useRef } from 'react';
import { AgGridReact } from 'ag-grid-react';
import { ModuleRegistry, AllCommunityModule } from 'ag-grid-community';
import ordersWebSocket from '../services/ordersWebsocket';
import 'ag-grid-community/styles/ag-grid.css';
import 'ag-grid-community/styles/ag-theme-alpine.css';
import './OrdersPanel.css';

// Register AG Grid modules
ModuleRegistry.registerModules([AllCommunityModule]);

const AGGridOrdersPanel = ({ selectedMarket }) => {
  const gridRef = useRef();
  const [rowData, setRowData] = useState([]);
  const [connectionState, setConnectionState] = useState({
    isConnected: false,
    isConnecting: false,
    error: null
  });
  const [orderStats, setOrderStats] = useState({ 
    total: 0, 
    open: 0, 
    filled: 0,
    partial: 0,
    canceled: 0
  });

  // Column definitions for AG Grid
  const [columnDefs] = useState([
    { 
      headerName: 'Order ID', 
      field: 'order_id', 
      width: 120,
      pinned: 'left',
      cellRenderer: (params) => {
        const orderId = params.value;
        return orderId ? orderId.substring(4, 12) : '';
      }
    },
    { 
      headerName: 'Market', 
      field: 'market_ticker', 
      width: 100 
    },
    { 
      headerName: 'Side', 
      field: 'side', 
      width: 60,
      cellRenderer: (params) => {
        const side = params.value?.toUpperCase();
        return `<span class="side-${params.value}">${side}</span>`;
      }
    },
    { 
      headerName: 'Action', 
      field: 'action', 
      width: 70,
      cellRenderer: (params) => {
        const action = params.value?.toUpperCase();
        return `<span class="action-${params.value}">${action}</span>`;
      }
    },
    { 
      headerName: 'Status', 
      field: 'status', 
      width: 100,
      cellRenderer: (params) => {
        const status = params.value?.replace('_', ' ').toUpperCase();
        const statusClass = params.value?.replace('_', '-');
        return `<span class="status-${statusClass}">${status}</span>`;
      }
    },
    { 
      headerName: 'Type', 
      field: 'type', 
      width: 80 
    },
    { 
      headerName: 'Orig Qty', 
      field: 'original_quantity', 
      width: 80,
      type: 'numericColumn'
    },
    { 
      headerName: 'Filled', 
      field: 'filled_quantity', 
      width: 70,
      type: 'numericColumn',
      cellRenderer: (params) => {
        const filled = params.value || 0;
        const original = params.data.original_quantity || 0;
        const className = filled > 0 ? 'qty-partial' : '';
        return `<span class="${className}">${filled}</span>`;
      }
    },
    { 
      headerName: 'Remaining', 
      field: 'remaining_quantity', 
      width: 90,
      type: 'numericColumn'
    },
    { 
      headerName: 'Price', 
      field: 'price', 
      width: 80,
      type: 'numericColumn',
      valueFormatter: (params) => {
        return params.value ? `${params.value}¢` : '';
      }
    },
    { 
      headerName: 'Avg Fill', 
      field: 'avg_fill_price', 
      width: 80,
      type: 'numericColumn',
      valueFormatter: (params) => {
        return params.value ? `${params.value}¢` : '-';
      }
    },
    { 
      headerName: 'TIF', 
      field: 'time_in_force', 
      width: 60 
    },
    { 
      headerName: 'Created', 
      field: 'created_time', 
      width: 150,
      valueFormatter: (params) => {
        if (!params.value) return '';
        return new Date(params.value).toLocaleString();
      }
    },
    { 
      headerName: 'Updated', 
      field: 'updated_time', 
      width: 150,
      valueFormatter: (params) => {
        if (!params.value) return '';
        return new Date(params.value).toLocaleString();
      }
    }
  ]);

  // Default column properties
  const defaultColDef = {
    sortable: true,
    filter: true,
    resizable: true,
    suppressMenu: true
  };

  // Fetch all orders for the selected market
  const fetchOrdersForMarket = useCallback(async (marketTicker) => {
    if (!marketTicker) return;
    
    try {
      console.log(`Fetching orders for market: ${marketTicker}`);
      
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
      
      setRowData(transformedOrders);
      updateOrderStats(transformedOrders);
      
    } catch (error) {
      console.error('Failed to fetch orders:', error);
      setRowData([]);
      setOrderStats({ total: 0, open: 0, filled: 0, partial: 0, canceled: 0 });
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

  // Handle incoming order update messages
  const handleOrderUpdate = useCallback((message) => {
    if (message.type !== 'order_update') return;
    
    const orderUpdate = message.msg;
    console.log('Received order update:', orderUpdate);

    // Update row data
    setRowData(prevData => {
      const existingIndex = prevData.findIndex(row => row.order_id === orderUpdate.order_id);
      
      if (existingIndex >= 0) {
        // Update existing order
        const updatedData = [...prevData];
        updatedData[existingIndex] = {
          ...updatedData[existingIndex],
          ...orderUpdate,
          updated_time: new Date().toISOString()
        };
        
        console.log(`Updated existing order: ${orderUpdate.order_id}`);
        updateOrderStats(updatedData);
        return updatedData;
      } else {
        // Add new order
        const newOrder = {
          ...orderUpdate,
          created_time: new Date().toISOString(),
          updated_time: new Date().toISOString()
        };
        
        const updatedData = [newOrder, ...prevData];
        console.log(`Added new order: ${orderUpdate.order_id}`);
        
        updateOrderStats(updatedData);
        return updatedData;
      }
    });
  }, [updateOrderStats]);

  // Subscribe to order updates via dedicated WebSocket
  const subscribeToOrderUpdates = useCallback((marketTicker) => {
    if (!marketTicker) return;

    console.log(`Subscribing to order updates for market: ${marketTicker}`);
    ordersWebSocket.subscribe(marketTicker, handleOrderUpdate);
  }, [handleOrderUpdate]);

  // Unsubscribe from order updates
  const unsubscribeFromOrderUpdates = useCallback((marketTicker) => {
    if (!marketTicker) return;

    console.log(`Unsubscribing from order updates for market: ${marketTicker}`);
    ordersWebSocket.unsubscribe(marketTicker);
  }, []);

  // Initialize orders WebSocket connection
  useEffect(() => {
    ordersWebSocket.connect();
    
    const unsubscribe = ordersWebSocket.onConnectionStatusChange((state) => {
      setConnectionState(state);
    });
    
    return () => {
      unsubscribe();
    };
  }, []);

  // Handle market selection changes
  useEffect(() => {
    if (selectedMarket && connectionState.isConnected) {
      fetchOrdersForMarket(selectedMarket);
      subscribeToOrderUpdates(selectedMarket);
    } else if (!selectedMarket) {
      setRowData([]);
      setOrderStats({ total: 0, open: 0, filled: 0, partial: 0, canceled: 0 });
    }
    
    return () => {
      if (selectedMarket) {
        unsubscribeFromOrderUpdates(selectedMarket);
      }
    };
  }, [selectedMarket, connectionState.isConnected, fetchOrdersForMarket, subscribeToOrderUpdates, unsubscribeFromOrderUpdates]);

  // Grid ready handler
  const onGridReady = useCallback((params) => {
    console.log('AG Grid ready');
    // Auto-size all columns
    params.api.sizeColumnsToFit();
  }, []);

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
        <div className={`connection-status ${connectionState.isConnected ? 'connected' : connectionState.isConnecting ? 'connecting' : 'disconnected'}`}>
          {connectionState.isConnected ? '🔗 Live' : connectionState.isConnecting ? '⏳ Connecting...' : '❌ Offline'}
        </div>
      </div>
      
      <div className="ag-theme-alpine orders-grid" style={{ height: '400px', width: '100%' }}>
        <AgGridReact
          ref={gridRef}
          rowData={rowData}
          columnDefs={columnDefs}
          defaultColDef={defaultColDef}
          onGridReady={onGridReady}
          animateRows={true}
          rowSelection="single"
          getRowId={(params) => params.data.order_id}
          enableCellTextSelection={true}
          ensureDomOrder={true}
          suppressRowClickSelection={true}
        />
      </div>
    </div>
  );
};

export default AGGridOrdersPanel;