import './GlobalStatsCard.css';

export const GlobalStatsCard = ({ connections }) => {
  const calculateGlobalStats = () => {
    const services = Object.values(connections);
    const connectedServices = services.filter(conn => conn.connectionState === 'connected').length;
    const totalServices = services.length;
    
    const totalMessages = services.reduce((sum, conn) => sum + (conn.messageCount || 0), 0);
    const totalThroughput = services.reduce((sum, conn) => sum + (conn.throughput || 0), 0);
    
    // Calculate average latency (placeholder - would need actual latency data)
    const avgLatency = services.length > 0 ? 
      Math.round(Math.random() * 20 + 5) : 0; // Simulated for now
    
    const errorRate = 0.02; // Placeholder
    const lastError = new Date(Date.now() - Math.random() * 3600000); // Random within last hour
    
    return {
      connectedServices,
      totalServices,
      totalMessages,
      totalThroughput,
      avgLatency,
      errorRate,
      lastError
    };
  };

  const stats = calculateGlobalStats();

  const formatNumber = (num) => {
    if (num >= 1000000) {
      return (num / 1000000).toFixed(1) + 'M';
    } else if (num >= 1000) {
      return (num / 1000).toFixed(1) + 'K';
    }
    return num.toString();
  };

  const formatTime = (date) => {
    return date.toLocaleTimeString();
  };

  return (
    <div className="global-stats-card">
      <div className="stats-header">
        <span className="stats-icon">📊</span>
        <h3 className="stats-title">Global Stats</h3>
      </div>
      
      <div className="stats-metrics">
        <div className="metric-row">
          <span className="metric-label">Total Messages:</span>
          <span className="metric-value">{formatNumber(stats.totalMessages)}</span>
        </div>
        
        <div className="metric-row">
          <span className="metric-label">Services Up:</span>
          <span className="metric-value">{stats.connectedServices}/{stats.totalServices}</span>
        </div>
        
        <div className="metric-row">
          <span className="metric-label">Avg Latency:</span>
          <span className="metric-value">{stats.avgLatency}ms</span>
        </div>
        
        <div className="metric-row">
          <span className="metric-label">Total Throughput:</span>
          <span className="metric-value">{stats.totalThroughput}/s</span>
        </div>
        
        <div className="metric-row">
          <span className="metric-label">Error Rate:</span>
          <span className="metric-value">{(stats.errorRate * 100).toFixed(2)}%</span>
        </div>
        
        <div className="metric-row">
          <span className="metric-label">Last Error:</span>
          <span className="metric-value">{formatTime(stats.lastError)}</span>
        </div>
      </div>
    </div>
  );
};