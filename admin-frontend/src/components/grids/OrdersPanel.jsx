import React, { useState, useEffect, useCallback, useRef, useMemo } from 'react';
import { AgGridReact } from 'ag-grid-react';
import { formatOrderStatus, orderUpdateToExecutionReport } from '../../utils/kalshi-fix-utils.js';
import './OrdersPanel.css';

export const OrdersPanel = ({ 
  selectedMarket, 
  connections,
  onOrderSelect,
  executionReports = [] 
}) => {
  const [orders, setOrders] = useState(new Map()); // Map of betOrderId -> order
  const [filteredOrders, setFilteredOrders] = useState([]);
  const [orderStats, setOrderStats] = useState({
    total: 0,
    new: 0,
    filled: 0,
    partiallyFilled: 0,
    canceled: 0,
    rejected: 0
  });

  const gridRef = useRef(null);

  // Column definitions with FIX domain model fields
  const columnDefs = [
    { 
      headerName: 'Order ID', 
      field: 'betOrderId', 
      width: 120,
      pinned: 'left',
      cellRenderer: (params) => {
        const orderId = params.value;
        return orderId ? orderId.substring(0, 8) + '...' : '';
      },
      tooltipField: 'betOrderId'
    },
    { 
      headerName: 'Market', 
      field: 'symbol', 
      width: 150,
      cellClass: 'market-cell'
    },
    { 
      headerName: 'Side', 
      field: 'side', 
      width: 70,
      cellClass: (params) => params.value === 'BUY' ? 'buy-side' : 'sell-side'
    },
    { 
      headerName: 'Status', 
      field: 'ordStatus', 
      width: 110,
      cellRenderer: (params) => {
        const status = formatOrderStatus(params.value);
        return `
          <div class="status-cell">
            <span class="status-icon">${status.icon}</span>
            <span class="status-label" style="color: ${status.color}">${status.label}</span>
          </div>
        `;
      }
    },
    { 
      headerName: 'Type', 
      field: 'ordType', 
      width: 80,
      valueFormatter: (params) => {
        const typeMap = { 'MARKET': 'MKT', 'LIMIT': 'LMT' };
        return typeMap[params.value] || params.value;
      }
    },
    { 
      headerName: 'TIF', 
      field: 'timeInForce', 
      width: 60,
      tooltipField: 'timeInForce'
    },
    { 
      headerName: 'Price', 
      field: 'price', 
      width: 80,
      type: 'numericColumn',
      cellClass: 'price-cell',
      valueFormatter: (params) => {
        if (!params.value) return '';
        return params.value < 1 ? `${(params.value * 100).toFixed(0)}¢` : `$${params.value.toFixed(2)}`;
      }
    },
    { 
      headerName: 'Qty', 
      field: 'orderQty', 
      width: 80,
      type: 'numericColumn',
      cellClass: 'qty-cell'
    },
    { 
      headerName: 'Filled', 
      field: 'cumQty', 
      width: 80,
      type: 'numericColumn',
      cellClass: (params) => {
        const filled = params.value || 0;
        const original = params.data.orderQty || 0;
        if (filled === 0) return 'qty-none';
        if (filled === original) return 'qty-full';
        return 'qty-partial';
      }
    },
    { 
      headerName: 'Leaves', 
      field: 'leavesQty', 
      width: 80,
      type: 'numericColumn',
      cellClass: 'qty-cell'
    },
    { 
      headerName: 'Avg Price', 
      field: 'avgPx', 
      width: 100,
      type: 'numericColumn',
      cellClass: 'price-cell',
      valueFormatter: (params) => {
        if (!params.value) return '';
        return params.value < 1 ? `${(params.value * 100).toFixed(1)}¢` : `$${params.value.toFixed(3)}`;
      }
    },
    { 
      headerName: 'Created', 
      field: 'transactTime', 
      width: 120,
      valueFormatter: (params) => {
        if (!params.value) return '';
        return new Date(params.value).toLocaleTimeString('en-US', {
          hour12: false,
          hour: '2-digit',
          minute: '2-digit',
          second: '2-digit'
        });
      }
    },
    { 
      headerName: 'Updated', 
      field: 'lastUpdateTime', 
      width: 120,
      valueFormatter: (params) => {
        if (!params.value) return '';
        return new Date(params.value).toLocaleTimeString('en-US', {
          hour12: false,
          hour: '2-digit',
          minute: '2-digit',
          second: '2-digit'
        });
      }
    },
    { 
      headerName: 'ClOrdID', 
      field: 'clOrdID', 
      width: 150,
      hide: true, // Hidden by default, can be shown via column menu
      tooltipField: 'clOrdID'
    },
    { 
      headerName: 'Exchange ID', 
      field: 'orderID', 
      width: 120,
      hide: true,
      tooltipField: 'orderID'
    }
  ];

  const defaultColDef = {
    sortable: true,
    filter: true,
    resizable: true,
    suppressMenu: false,
    floatingFilter: true
  };

  // Process execution reports into orders
  useEffect(() => {
    const ordersMap = new Map();
    
    // Process all execution reports to build/update order state
    executionReports.forEach(execReport => {
      const betOrderId = execReport.betOrderId;
      if (!betOrderId) return;
      
      const existingOrder = ordersMap.get(betOrderId);
      
      // Create or update order based on execution report
      const order = {
        betOrderId,
        symbol: execReport.symbol || execReport.instrument?.symbol,
        side: execReport.side,
        ordStatus: execReport.ordStatus,
        ordType: execReport.ordType || execReport.newOrder?.orderType,
        timeInForce: execReport.timeInForce || execReport.newOrder?.timeInForce,
        price: execReport.price || execReport.newOrder?.price,
        orderQty: execReport.orderQty || execReport.newOrder?.quantity,
        cumQty: execReport.cumQty || 0,
        leavesQty: execReport.leavesQty || execReport.orderQty || 0,
        avgPx: execReport.avgPx,
        clOrdID: execReport.clOrdID,
        orderID: execReport.orderID,
        transactTime: execReport.transactTime || execReport.timestamp,
        lastUpdateTime: execReport.timestamp || Date.now(),
        text: execReport.text,
        ordRejReason: execReport.ordRejReason,
        executions: [...(existingOrder?.executions || []), execReport]
      };
      
      ordersMap.set(betOrderId, order);
    });
    
    setOrders(ordersMap);
  }, [executionReports]);

  // Handle WebSocket order updates from temp-orders
  useEffect(() => {
    if (!connections.tempOrders) return;
    
    const handleMessage = (message) => {
      if (message.type === 'ORDER_UPDATE' && message.data) {
        const execReportLike = orderUpdateToExecutionReport(message.data);
        const betOrderId = message.data.betOrderId;
        
        setOrders(prev => {
          const newOrders = new Map(prev);
          const existingOrder = newOrders.get(betOrderId) || {};
          
          // Update order with new information
          const updatedOrder = {
            ...existingOrder,
            betOrderId,
            symbol: message.data.order.symbol,
            side: message.data.order.side,
            ordStatus: execReportLike.ordStatus,
            ordType: message.data.order.orderType,
            timeInForce: message.data.order.timeInForce,
            price: message.data.order.price ? parseFloat(message.data.order.price) : undefined,
            orderQty: parseFloat(message.data.order.quantity),
            clOrdID: message.data.order.clOrdId,
            orderID: message.data.order.orderId,
            lastUpdateTime: Date.now(),
            event: message.data.event
          };
          
          newOrders.set(betOrderId, updatedOrder);
          return newOrders;
        });
      }
    };
    
    // Note: In real implementation, add message handler to connection
    // For now, this is handled by the useWebSocketConnections hook
    
  }, [connections.tempOrders]);

  // Filter orders based on selected market
  useEffect(() => {
    const allOrders = Array.from(orders.values());
    
    if (selectedMarket) {
      setFilteredOrders(
        allOrders
          .filter(order => order.symbol === selectedMarket)
          .sort((a, b) => b.lastUpdateTime - a.lastUpdateTime)
          .slice(0, 1000) // Limit to 1000 most recent
      );
    } else {
      // Show all orders if no market selected, limited to 1000
      setFilteredOrders(
        allOrders
          .sort((a, b) => b.lastUpdateTime - a.lastUpdateTime)
          .slice(0, 1000)
      );
    }
  }, [orders, selectedMarket]);

  // Calculate order statistics
  useEffect(() => {
    const stats = filteredOrders.reduce((acc, order) => {
      acc.total++;
      switch (order.ordStatus) {
        case 'NEW':
        case 'PENDING_NEW':
          acc.new++;
          break;
        case 'FILLED':
          acc.filled++;
          break;
        case 'PARTIALLY_FILLED':
          acc.partiallyFilled++;
          break;
        case 'CANCELED':
        case 'PENDING_CANCEL':
          acc.canceled++;
          break;
        case 'REJECTED':
          acc.rejected++;
          break;
      }
      return acc;
    }, { total: 0, new: 0, filled: 0, partiallyFilled: 0, canceled: 0, rejected: 0 });
    
    setOrderStats(stats);
  }, [filteredOrders]);

  const onGridReady = useCallback((params) => {
    // Auto-size columns to fit content
    params.api.sizeColumnsToFit();
  }, []);

  const onRowClicked = useCallback((event) => {
    if (onOrderSelect) {
      onOrderSelect(event.data);
    }
  }, [onOrderSelect]);

  // Flash cells when order updates
  useEffect(() => {
    if (gridRef.current?.api && filteredOrders.length > 0) {
      const lastOrder = filteredOrders[0];
      const rowNode = gridRef.current.api.getRowNode(lastOrder.betOrderId);
      if (rowNode) {
        gridRef.current.api.flashCells({
          rowNodes: [rowNode],
          flashDelay: 300,
          fadeDelay: 1000
        });
      }
    }
  }, [filteredOrders]);

  return (
    <div className="orders-panel">
      <div className="orders-header">
        <h3>Orders {selectedMarket ? `- ${selectedMarket}` : '(All Markets)'}</h3>
        <div className="orders-stats">
          <span className="stat-item">Total: {orderStats.total}</span>
          <span className="stat-item new">New: {orderStats.new}</span>
          <span className="stat-item filled">Filled: {orderStats.filled}</span>
          {orderStats.partiallyFilled > 0 && (
            <span className="stat-item partial">Partial: {orderStats.partiallyFilled}</span>
          )}
          <span className="stat-item canceled">Canceled: {orderStats.canceled}</span>
          {orderStats.rejected > 0 && (
            <span className="stat-item rejected">Rejected: {orderStats.rejected}</span>
          )}
        </div>
        <div className="connection-status">
          {connections.tempOrders?.isConnected ? (
            <span className="connected">🟢 Live</span>
          ) : (
            <span className="disconnected">🔴 Disconnected</span>
          )}
        </div>
      </div>
      
      <div className="orders-grid-container ag-theme-alpine-dark">
        <AgGridReact
          ref={gridRef}
          rowData={filteredOrders}
          columnDefs={columnDefs}
          defaultColDef={defaultColDef}
          onGridReady={onGridReady}
          onRowClicked={onRowClicked}
          animateRows={true}
          rowSelection="single"
          getRowId={(params) => params.data.betOrderId}
          enableCellTextSelection={true}
          ensureDomOrder={true}
          suppressRowClickSelection={false}
          rowHeight={36}
          headerHeight={40}
          floatingFiltersHeight={40}
          pagination={true}
          paginationPageSize={50}
          paginationPageSizeSelector={[50, 100, 200, 500]}
          enableCellChangeFlash={true}
        />
      </div>
    </div>
  );
};