import { ServiceCard } from './ServiceCard.jsx';
import { GlobalStatsCard } from './GlobalStatsCard.jsx';
import { LastExecutionCard } from './LastExecutionCard.jsx';
import './SystemHealthDashboard.css';

export const SystemHealthDashboard = ({ connections, lastExecution, marketData }) => {
  const getLastMarketForService = (serviceName) => {
    // Extract last updated market from marketData or orderBooks
    const markets = Object.keys(marketData);
    if (markets.length === 0) return '--';
    
    // Return most recently updated market
    let lastMarket = '--';
    let lastTime = 0;
    
    Object.entries(marketData).forEach(([ticker, data]) => {
      if (data.lastUpdated > lastTime) {
        lastTime = data.lastUpdated;
        lastMarket = ticker;
      }
    });
    
    return lastMarket;
  };

  const getLastExecutionForService = (serviceName) => {
    if (!lastExecution) return null;
    
    // Filter executions by source service
    if (lastExecution.source.toLowerCase().includes(serviceName.toLowerCase())) {
      return lastExecution;
    }
    
    return null;
  };

  return (
    <div className="system-health-dashboard">
      <div className="dashboard-header">
        <h2>System Health Dashboard</h2>
      </div>
      
      <div className="dashboard-grid">
        <ServiceCard 
          serviceName="Mock Server"
          connection={connections.mockServer}
          lastMarket={getLastMarketForService('mock')}
          lastExecution={getLastExecutionForService('mock')}
        />
        
        <ServiceCard 
          serviceName="Market Data"
          connection={connections.marketDataServer}
          lastMarket={getLastMarketForService('market')}
          lastExecution={getLastExecutionForService('market')}
        />
        
        <ServiceCard 
          serviceName="Order Rebuilder"
          connection={connections.orderRebuilder}
          lastMarket={getLastMarketForService('rebuilder')}
          lastExecution={getLastExecutionForService('rebuilder')}
        />
        
        <ServiceCard 
          serviceName="Temp Orders"
          connection={connections.tempOrders}
          lastMarket={getLastMarketForService('temp')}
          lastExecution={getLastExecutionForService('temp')}
        />
        
        <GlobalStatsCard connections={connections} />
        
        <LastExecutionCard lastExecution={lastExecution} />
      </div>
    </div>
  );
};