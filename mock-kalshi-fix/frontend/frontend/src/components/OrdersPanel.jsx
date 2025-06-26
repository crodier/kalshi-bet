import React, { useState, useEffect, useRef, useCallback } from 'react';
import { AgGridReact } from 'ag-grid-react';
import 'ag-grid-community/styles/ag-grid.css';
import 'ag-grid-community/styles/ag-theme-alpine.css';
import './OrdersPanel.css';

const OrdersPanel = ({ selectedMarket, websocket }) => {
  const [rowData, setRowData] = useState([]);
  const [isConnected, setIsConnected] = useState(false);
  const [orderStats, setOrderStats] = useState({ total: 0, open: 0, filled: 0 });
  const gridRef = useRef();
  const subscriptionRef = useRef(null);

  // AG Grid column definitions
  const columnDefs = [
    {
      headerName: 'Order ID',
      field: 'order_id',
      width: 150,
      pinned: 'left',
      cellRenderer: (params) => {
        const shortId = params.value ? params.value.substring(4, 12) : '';
        return `<span title="${params.value}">${shortId}</span>`;
      }
    },
    {
      headerName: 'Market',
      field: 'market_ticker',
      width: 120,
      filter: true
    },
    {
      headerName: 'Side',
      field: 'side',
      width: 60,
      cellRenderer: (params) => {
        const side = params.value;
        const className = side === 'yes' ? 'side-yes' : 'side-no';
        return `<span class="${className}">${side.toUpperCase()}</span>`;
      }
    },
    {
      headerName: 'Action',
      field: 'action',
      width: 70,
      cellRenderer: (params) => {
        const action = params.value;
        const className = action === 'buy' ? 'action-buy' : 'action-sell';
        return `<span class="${className}">${action.toUpperCase()}</span>`;
      }
    },
    {
      headerName: 'Type',
      field: 'type',
      width: 80
    },
    {
      headerName: 'Status',
      field: 'status',
      width: 100,
      cellRenderer: (params) => {
        const status = params.value;
        let className = 'status-open';
        if (status === 'filled') className = 'status-filled';
        else if (status === 'partially_filled') className = 'status-partial';
        else if (status === 'canceled') className = 'status-canceled';
        
        return `<span class="${className}">${status.replace('_', ' ').toUpperCase()}</span>`;
      }
    },
    {
      headerName: 'Orig Qty',
      field: 'original_quantity',
      width: 90,
      type: 'numericColumn'
    },
    {
      headerName: 'Filled',
      field: 'filled_quantity',
      width: 80,
      type: 'numericColumn',
      cellRenderer: (params) => {
        const filled = params.value || 0;
        const original = params.data.original_quantity || 0;
        const isPartial = filled > 0 && filled < original;
        const className = isPartial ? 'qty-partial' : '';
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
      width: 90,
      type: 'numericColumn',
      valueFormatter: (params) => {
        return params.value ? `${params.value}¢` : '';
      }
    },
    {
      headerName: 'Time in Force',
      field: 'time_in_force',
      width: 100
    },
    {
      headerName: 'Created',
      field: 'created_time',
      width: 140,
      valueFormatter: (params) => {
        if (!params.value) return '';
        const date = new Date(params.value);
        return date.toLocaleString();
      }
    },
    {
      headerName: 'Updated',
      field: 'updated_time',
      width: 140,
      valueFormatter: (params) => {
        if (!params.value) return '';
        const date = new Date(params.value);
        return date.toLocaleString();
      }
    },
    {
      headerName: 'Update Type',
      field: 'update_type',
      width: 100,
      cellRenderer: (params) => {
        const type = params.value;
        if (!type) return '';
        
        let className = 'update-type';
        if (type === 'NEW') className += ' update-new';
        else if (type === 'FILL') className += ' update-fill';
        else if (type === 'CANCEL') className += ' update-cancel';
        
        return `<span class="${className}">${type}</span>`;
      }
    }
  ];

  // Default column properties
  const defaultColDef = {
    sortable: true,
    resizable: true,
    filter: false,
    floatingFilter: false
  };

  // Grid options
  const gridOptions = {
    animateRows: true,
    enableCellChangeFlash: true,
    suppressRowClickSelection: true,
    rowSelection: 'multiple',
    pagination: true,
    paginationPageSize: 50,
    getRowId: (params) => params.data.order_id
  };

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
      const orders = data.orders || [];
      
      console.log(`Loaded ${orders.length} orders for market ${marketTicker}`);
      
      // Transform orders to match our WebSocket message format
      const transformedOrders = orders.map(order => ({
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
      setOrderStats({ total: 0, open: 0, filled: 0 });
    }
  }, []);

  // Update order statistics
  const updateOrderStats = useCallback((orders) => {
    const stats = {
      total: orders.length,
      open: orders.filter(o => o.status === 'open').length,
      filled: orders.filter(o => o.status === 'filled').length,
      partial: orders.filter(o => o.status === 'partially_filled').length,
      canceled: orders.filter(o => o.status === 'canceled').length
    };
    setOrderStats(stats);
  }, []);

  // Subscribe to order updates WebSocket
  const subscribeToOrderUpdates = useCallback((marketTicker) => {
    if (!websocket || !marketTicker) return;

    console.log(`Subscribing to order updates for market: ${marketTicker}`);
    
    const subscribeMessage = {
      cmd: "subscribe",
      id: Date.now(),
      params: {
        channels: ["orders"],
        market_tickers: [marketTicker]
      }
    };

    // Store subscription for cleanup
    subscriptionRef.current = {
      marketTicker,
      subscriptionId: subscribeMessage.id
    };

    websocket.send(JSON.stringify(subscribeMessage));
    setIsConnected(true);
  }, [websocket]);

  // Unsubscribe from order updates
  const unsubscribeFromOrderUpdates = useCallback(() => {
    if (!websocket || !subscriptionRef.current) return;

    console.log(`Unsubscribing from order updates for market: ${subscriptionRef.current.marketTicker}`);
    
    const unsubscribeMessage = {
      cmd: "unsubscribe",
      id: Date.now(),
      sid: subscriptionRef.current.subscriptionId
    };

    websocket.send(JSON.stringify(unsubscribeMessage));
    subscriptionRef.current = null;
    setIsConnected(false);
  }, [websocket]);

  // Handle incoming order update messages
  const handleOrderUpdate = useCallback((message) => {
    if (message.type !== 'order_update') return;
    
    const orderUpdate = message.msg;
    console.log('Received order update:', orderUpdate);

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
        
        // Flash the updated row
        setTimeout(() => {
          if (gridRef.current?.api) {
            gridRef.current.api.flashCells({
              rowNodes: [gridRef.current.api.getRowNode(orderUpdate.order_id)]
            });
          }
        }, 100);
        
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

  // Effect to handle market selection changes
  useEffect(() => {
    if (selectedMarket) {
      // Unsubscribe from previous market
      unsubscribeFromOrderUpdates();
      
      // Fetch orders and subscribe to new market
      fetchOrdersForMarket(selectedMarket);
      subscribeToOrderUpdates(selectedMarket);
    } else {
      unsubscribeFromOrderUpdates();
      setRowData([]);
      setOrderStats({ total: 0, open: 0, filled: 0 });
    }
  }, [selectedMarket, fetchOrdersForMarket, subscribeToOrderUpdates, unsubscribeFromOrderUpdates]);

  // Effect to handle WebSocket messages
  useEffect(() => {
    if (!websocket) return;

    const handleMessage = (event) => {
      try {
        const message = JSON.parse(event.data);
        handleOrderUpdate(message);
      } catch (error) {
        console.error('Failed to parse WebSocket message:', error);
      }
    };

    websocket.addEventListener('message', handleMessage);
    
    return () => {
      websocket.removeEventListener('message', handleMessage);
    };
  }, [websocket, handleOrderUpdate]);

  // Cleanup on unmount
  useEffect(() => {
    return () => {
      unsubscribeFromOrderUpdates();
    };
  }, [unsubscribeFromOrderUpdates]);

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
      
      <div className="ag-theme-alpine orders-grid" style={{ height: '400px', width: '100%' }}>
        <AgGridReact
          ref={gridRef}
          rowData={rowData}
          columnDefs={columnDefs}
          defaultColDef={defaultColDef}
          gridOptions={gridOptions}
          suppressMenuHide={true}
          onGridReady={(params) => {
            console.log('Orders grid ready');
            params.api.sizeColumnsToFit();
          }}
        />
      </div>
    </div>
  );
};

export default OrdersPanel;