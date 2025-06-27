import { useState } from 'react';
import { EnvironmentSelector } from './components/admin/EnvironmentSelector.jsx';
import { SystemHealthDashboard } from './components/admin/SystemHealthDashboard.jsx';
import { MarketsAdmin } from './components/admin/MarketsAdmin.jsx';
import { OrdersPanel } from './components/grids/OrdersPanel.jsx';
import { useEnvironmentConfig } from './hooks/useEnvironmentConfig.js';
import { useWebSocketConnections } from './hooks/useWebSocketConnections.js';
import './App.css';

function App() {
  const [selectedMarket, setSelectedMarket] = useState(null);
  const [selectedOrder, setSelectedOrder] = useState(null);

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

        {selectedMarket && (
          <div className="trading-panels">
            <div className="orders-section">
              <OrdersPanel
                selectedMarket={selectedMarket}
                connections={connections}
                onOrderSelect={setSelectedOrder}
              />
            </div>
            
            {/* TODO: Add Order Book Display Panel */}
            {/* TODO: Add Executions Grid */}
          </div>
        )}
      </main>
    </div>
  );
}

export default App;