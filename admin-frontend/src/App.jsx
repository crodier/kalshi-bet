import { EnvironmentSelector } from './components/admin/EnvironmentSelector.jsx';
import { SystemHealthDashboard } from './components/admin/SystemHealthDashboard.jsx';
import { useEnvironmentConfig } from './hooks/useEnvironmentConfig.js';
import { useWebSocketConnections } from './hooks/useWebSocketConnections.js';
import './App.css';

function App() {
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

        {/* TODO: Add Markets Admin Component */}
        {/* TODO: Add Order Book Display Panel */}
        {/* TODO: Add Executions Grid */}
      </main>
    </div>
  );
}

export default App;