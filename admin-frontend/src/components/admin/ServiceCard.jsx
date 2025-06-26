import './ServiceCard.css';

export const ServiceCard = ({ 
  serviceName, 
  connection, 
  lastMarket = '--', 
  lastExecution = null 
}) => {
  const getStatusIcon = (state) => {
    switch (state) {
      case 'connected': return '🟢';
      case 'connecting': return '🟡';
      case 'error': return '🔴';
      case 'never-connected': return '⚪';
      default: return '🔴';
    }
  };

  const getStatusText = (state) => {
    switch (state) {
      case 'connected': return 'Connected';
      case 'connecting': return 'Connecting';
      case 'error': return 'Error';
      case 'never-connected': return 'Never Connected';
      default: return 'Disconnected';
    }
  };

  const formatDuration = (seconds) => {
    if (!seconds) return '--';
    
    const hours = Math.floor(seconds / 3600);
    const minutes = Math.floor((seconds % 3600) / 60);
    const secs = seconds % 60;
    
    if (hours > 0) {
      return `${hours}h ${minutes}m`;
    } else if (minutes > 0) {
      return `${minutes}m ${secs}s`;
    } else {
      return `${secs}s`;
    }
  };

  const formatTime = (timestamp) => {
    if (!timestamp) return '--';
    return new Date(timestamp).toLocaleTimeString();
  };

  const calculateUptime = () => {
    if (!connection.connectTime) return 0;
    return Math.round(((connection.uptime || 0) / (connection.uptime || 1)) * 100);
  };

  const formatExecution = (execution) => {
    if (!execution) return '--';
    return `${execution.action} ${execution.side}@${execution.price}¢×${execution.quantity}`;
  };

  return (
    <div className={`service-card ${connection.connectionState}`}>
      <div className="service-header">
        <span className="status-icon">{getStatusIcon(connection.connectionState)}</span>
        <h3 className="service-name">{serviceName}</h3>
      </div>
      
      <div className="service-metrics">
        <div className="metric-row">
          <span className="metric-label">Status:</span>
          <span className="metric-value">{getStatusText(connection.connectionState)}</span>
        </div>
        
        <div className="metric-row">
          <span className="metric-label">Uptime:</span>
          <span className="metric-value">{calculateUptime()}%</span>
        </div>
        
        <div className="metric-row">
          <span className="metric-label">Duration:</span>
          <span className="metric-value">{formatDuration(connection.uptime)}</span>
        </div>
        
        <div className="metric-row">
          <span className="metric-label">Throughput:</span>
          <span className="metric-value">{connection.throughput || 0} msg/s</span>
        </div>
        
        <div className="metric-row">
          <span className="metric-label">Last Msg:</span>
          <span className="metric-value">{formatTime(connection.lastMessageTime)}</span>
        </div>
        
        <div className="metric-row">
          <span className="metric-label">Market:</span>
          <span className="metric-value">{lastMarket}</span>
        </div>
        
        <div className="metric-row execution">
          <span className="metric-label">Execution:</span>
          <span className="metric-value execution-text">{formatExecution(lastExecution)}</span>
        </div>
      </div>
    </div>
  );
};