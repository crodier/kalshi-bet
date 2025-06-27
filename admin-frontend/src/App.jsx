import { useState, useEffect } from 'react';
import { EnvironmentSelector } from './components/admin/EnvironmentSelector.jsx';
import { SystemHealthDashboard } from './components/admin/SystemHealthDashboard.jsx';
import { MarketsAdmin } from './components/admin/MarketsAdmin.jsx';
import { OrdersPanel } from './components/grids/OrdersPanel.jsx';
import { ExecutionsGrid } from './components/grids/ExecutionsGrid.jsx';
import { useEnvironmentConfig } from './hooks/useEnvironmentConfig.js';
import { useWebSocketConnections } from './hooks/useWebSocketConnections.js';
import './App.css';

function App() {
  const [selectedMarket, setSelectedMarket] = useState(null);
  const [selectedOrder, setSelectedOrder] = useState(null);
  const [executionReports, setExecutionReports] = useState([]);

  const {
    currentEnvironment,
    environmentConfig,
    customUrls,
    changeEnvironment,
    updateCustomUrl
  } = useEnvironmentConfig();

  const {
    mockServer,
    marketDataServer,
    orderRebuilder,
    tempOrders,
    lastExecution,
    marketData,
    orderBooks
  } = useWebSocketConnections(environmentConfig);

  // Track execution reports
  useEffect(() => {
    if (lastExecution && lastExecution.betOrderId) {
      setExecutionReports(prev => {
        // Create a mock ExecutionReport from lastExecution
        const execReport = {
          execID: `exec_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`,
          orderID: lastExecution.orderId || '',
          betOrderId: lastExecution.betOrderId,
          execType: lastExecution.action?.includes('NEW') ? 'NEW' : 
                    lastExecution.action?.includes('CANCELED') ? 'CANCELED' :
                    lastExecution.action?.includes('FILLED') ? 'TRADE' : 'ORDER_STATUS',
          ordStatus: lastExecution.status || 'NEW',
          side: lastExecution.side?.toUpperCase() || 'BUY',
          symbol: lastExecution.market,
          quantity: lastExecution.quantity,
          price: lastExecution.price,
          cumQty: lastExecution.quantity,
          avgPx: lastExecution.price,
          timestamp: lastExecution.timestamp,
          source: lastExecution.source,
          text: lastExecution.action
        };
        
        // Add to beginning of array (most recent first)
        return [execReport, ...prev.slice(0, 999)]; // Keep last 1000
      });
    }
  }, [lastExecution]);

  const connections = {
    mockServer,
    marketDataServer,
    orderRebuilder,
    tempOrders
  };

  return (
    <div className="app">
      <header className="app-header">
        <h1>Kalshi Admin Frontend</h1>
        <p>Multi-Service Trading System Administration</p>
      </header>

      <main className="app-main">
        <EnvironmentSelector
          currentEnvironment={currentEnvironment}
          environmentConfig={environmentConfig}
          customUrls={customUrls}
          onEnvironmentChange={changeEnvironment}
          onCustomUrlChange={updateCustomUrl}
        />

        <SystemHealthDashboard
          connections={connections}
          lastExecution={lastExecution}
          marketData={marketData}
        />

        <MarketsAdmin
          selectedMarket={selectedMarket}
          onMarketSelect={setSelectedMarket}
        />

        <div className="trading-panels">
          <div className="orders-section">
            <OrdersPanel
              selectedMarket={selectedMarket}
              connections={connections}
              onOrderSelect={setSelectedOrder}
              executionReports={executionReports}
            />
          </div>
          
          {/* TODO: Add Order Book Display Panel */}
        </div>

        <div className="executions-section">
          <ExecutionsGrid 
            executions={executionReports}
            selectedOrder={selectedOrder}
            onClearOrderFilter={() => setSelectedOrder(null)}
            onExecutionClick={(exec) => {
              console.log('Execution clicked:', exec);
              // Could select the market or order associated with this execution
              if (exec.symbol) {
                setSelectedMarket(exec.symbol);
              }
            }}
          />
        </div>
      </main>
    </div>
  );
}

export default App;