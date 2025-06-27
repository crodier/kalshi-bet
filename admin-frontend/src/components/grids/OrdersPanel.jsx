import React, { useState, useEffect, useCallback, useRef } from 'react';
import { AgGridReact } from 'ag-grid-react';
import './OrdersPanel.css';

export const OrdersPanel = ({ 
  selectedMarket, 
  connections,
  onOrderSelect 
}) => {
  const [rowData, setRowData] = useState([]);
  const [isLoading, setIsLoading] = useState(false);
  const [orderStats, setOrderStats] = useState({
    total: 0,
    open: 0,
    filled: 0,
    canceled: 0
  });

  const gridRef = useRef(null);

  // Column definitions following the mock server pattern
  const columnDefs = [
    { 
      headerName: 'Order ID', 
      field: 'order_id', 
      width: 120,
      pinned: 'left',
      cellRenderer: (params) => {
        const orderId = params.value;
        return orderId ? orderId.substring(0, 8) + '...' : '';
      }
    },
    { 
      headerName: 'Market', 
      field: 'market_ticker', 
      width: 100,
      cellClass: 'market-cell'
    },
    { 
      headerName: 'Side', 
      field: 'side', 
      width: 60,
      cellRenderer: (params) => params.value?.toUpperCase(),
      cellClass: (params) => `side-${params.value}`
    },
    { 
      headerName: 'Action', 
      field: 'action', 
      width: 70,
      cellRenderer: (params) => params.value?.toUpperCase(),
      cellClass: (params) => `action-${params.value}`
    },
    { 
      headerName: 'Status', 
      field: 'status', 
      width: 100,
      cellRenderer: (params) => params.value?.replace('_', ' ').toUpperCase(),
      cellClass: (params) => `status-${params.value?.replace('_', '-')}`
    },
    { 
      headerName: 'Type', 
      field: 'type', 
      width: 80,
      cellRenderer: (params) => params.value?.toUpperCase()
    },
    { 
      headerName: 'Price', 
      field: 'price', 
      width: 80,
      cellRenderer: (params) => {
        const price = params.value;
        return price !== null && price !== undefined ? `${price}¢` : '-';
      },
      cellClass: 'price-cell'
    },
    { 
      headerName: 'Orig Qty', 
      field: 'original_quantity', 
      width: 90,
      cellClass: 'qty-cell'
    },
    { 
      headerName: 'Filled', 
      field: 'filled_quantity', 
      width: 80,
      cellClass: (params) => {
        const filled = params.value || 0;
        const original = params.data.original_quantity || 0;
        if (filled === 0) return 'qty-none';
        if (filled === original) return 'qty-full';
        return 'qty-partial';
      }
    },
    { 
      headerName: 'Remaining', 
      field: 'remaining_quantity', 
      width: 100,
      cellClass: 'qty-cell'
    },
    { 
      headerName: 'Avg Fill Price', 
      field: 'avg_fill_price', 
      width: 120,
      cellRenderer: (params) => {
        const price = params.value;
        return price !== null && price !== undefined ? `${price}¢` : '-';
      },
      cellClass: 'price-cell'
    },
    { 
      headerName: 'Created', 
      field: 'created_time', 
      width: 120,
      cellRenderer: (params) => {
        const timestamp = params.value;
        return timestamp ? new Date(timestamp).toLocaleTimeString() : '';
      }
    },
    { 
      headerName: 'Updated', 
      field: 'updated_time', 
      width: 120,
      cellRenderer: (params) => {
        const timestamp = params.value;
        return timestamp ? new Date(timestamp).toLocaleTimeString() : '';
      }
    }
  ];

  const defaultColDef = {
    sortable: true,
    filter: true,
    resizable: true,
    suppressMenu: true
  };

  // Handle WebSocket order updates from mock server
  const handleOrderUpdate = useCallback((message) => {
    if (message.type !== 'order_update') return;
    
    const orderUpdate = message.msg;
    if (!orderUpdate || orderUpdate.market_ticker !== selectedMarket) return;

    setRowData(prevData => {
      const existingIndex = prevData.findIndex(row => row.order_id === orderUpdate.order_id);
      
      if (existingIndex >= 0) {
        // Update existing order
        const updatedData = [...prevData];
        updatedData[existingIndex] = {
          ...updatedData[existingIndex],
          ...orderUpdate,
          updated_time: new Date().toISOString(),
          update_type: orderUpdate.update_type || 'UPDATE'
        };
        
        // Flash the updated row
        setTimeout(() => {
          if (gridRef.current?.api) {
            gridRef.current.api.flashCells({
              rowNodes: [gridRef.current.api.getRowNode(orderUpdate.order_id)]
            });
          }
        }, 100);
        
        return updatedData;
      } else {
        // Add new order
        const newOrder = { 
          ...orderUpdate, 
          created_time: new Date().toISOString(),
          updated_time: new Date().toISOString(),
          update_type: 'NEW'
        };
        return [newOrder, ...prevData];
      }
    });
  }, [selectedMarket]);

  // Subscribe to WebSocket updates when market changes
  useEffect(() => {
    if (!selectedMarket || !connections.mockServer.isConnected) {
      setRowData([]);
      return;
    }

    // Send subscription for order updates on this market
    const subscription = {
      id: Date.now(),
      cmd: 'subscribe',
      params: {
        channels: ['orders'],
        market_tickers: [selectedMarket]
      }
    };

    connections.mockServer.sendMessage(subscription);

    // Generate some mock orders for demo purposes
    generateMockOrders(selectedMarket);

    // Add message handler for this market
    const handleMessage = (message) => {
      handleOrderUpdate(message);
    };

    // Note: In a real implementation, you'd set up the WebSocket message handler here
    // For now, we'll rely on the existing connection's message handling

    return () => {
      // Cleanup subscription if needed
    };
  }, [selectedMarket, connections.mockServer.isConnected, handleOrderUpdate]);

  // Generate mock orders for demo
  const generateMockOrders = (market) => {
    const mockOrders = [];
    const statuses = ['open', 'filled', 'partially_filled', 'canceled'];
    const sides = ['yes', 'no'];
    const actions = ['buy', 'sell'];
    
    for (let i = 0; i < 10; i++) {
      const originalQty = 10 + Math.floor(Math.random() * 90);
      const filledQty = Math.floor(Math.random() * originalQty);
      const status = statuses[Math.floor(Math.random() * statuses.length)];
      
      mockOrders.push({
        order_id: `order_${Date.now()}_${i}`,
        user_id: `user_${Math.floor(Math.random() * 1000)}`,
        market_ticker: market,
        side: sides[Math.floor(Math.random() * sides.length)],
        action: actions[Math.floor(Math.random() * actions.length)],
        type: 'limit',
        original_quantity: originalQty,
        filled_quantity: status === 'filled' ? originalQty : filledQty,
        remaining_quantity: originalQty - filledQty,
        price: 45 + Math.floor(Math.random() * 10),
        avg_fill_price: filledQty > 0 ? 45 + Math.floor(Math.random() * 10) : null,
        status: status,
        time_in_force: 'GTC',
        created_time: new Date(Date.now() - Math.random() * 3600000).toISOString(),
        updated_time: new Date().toISOString(),
        update_type: 'EXISTING'
      });
    }
    
    setRowData(mockOrders);
  };

  // Calculate order statistics
  useEffect(() => {
    const stats = rowData.reduce((acc, order) => {
      acc.total++;
      acc[order.status] = (acc[order.status] || 0) + 1;
      return acc;
    }, { total: 0, open: 0, filled: 0, canceled: 0, partially_filled: 0 });
    
    setOrderStats(stats);
  }, [rowData]);

  const onGridReady = useCallback((params) => {
    // Grid is ready
  }, []);

  const onRowClicked = useCallback((event) => {
    if (onOrderSelect) {
      onOrderSelect(event.data);
    }
  }, [onOrderSelect]);

  if (!selectedMarket) {
    return (
      <div className="orders-panel">
        <div className="orders-header">
          <h3>Orders</h3>
        </div>
        <div className="no-market-selected">
          <p>Select a market to view orders</p>
        </div>
      </div>
    );
  }

  return (
    <div className="orders-panel">
      <div className="orders-header">
        <h3>Orders - {selectedMarket}</h3>
        <div className="orders-stats">
          <span className="stat-item">Total: {orderStats.total}</span>
          <span className="stat-item open">Open: {orderStats.open || 0}</span>
          <span className="stat-item filled">Filled: {orderStats.filled || 0}</span>
          <span className="stat-item canceled">Canceled: {orderStats.canceled || 0}</span>
          {orderStats.partially_filled > 0 && (
            <span className="stat-item partial">Partial: {orderStats.partially_filled}</span>
          )}
        </div>
        <div className="connection-status">
          {connections.mockServer.isConnected ? (
            <span className="connected">🟢 Live</span>
          ) : (
            <span className="disconnected">🔴 Disconnected</span>
          )}
        </div>
      </div>
      
      <div className="orders-grid-container ag-theme-alpine">
        <AgGridReact
          ref={gridRef}
          rowData={rowData}
          columnDefs={columnDefs}
          defaultColDef={defaultColDef}
          onGridReady={onGridReady}
          onRowClicked={onRowClicked}
          animateRows={true}
          rowSelection="single"
          getRowId={(params) => params.data.order_id}
          enableCellTextSelection={true}
          ensureDomOrder={true}
          suppressRowClickSelection={false}
          rowHeight={32}
          headerHeight={36}
        />
      </div>
    </div>
  );
};