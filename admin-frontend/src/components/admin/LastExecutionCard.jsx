import './LastExecutionCard.css';

export const LastExecutionCard = ({ lastExecution }) => {
  const formatTime = (timestamp) => {
    if (!timestamp) return '--';
    return new Date(timestamp).toLocaleTimeString();
  };

  const formatPrice = (price) => {
    if (!price) return '--';
    return `${price}¢`;
  };

  return (
    <div className="last-execution-card">
      <div className="execution-header">
        <span className="execution-icon">⚡</span>
        <h3 className="execution-title">Last Execution</h3>
      </div>
      
      <div className="execution-metrics">
        {lastExecution ? (
          <>
            <div className="metric-row">
              <span className="metric-label">Time:</span>
              <span className="metric-value">{formatTime(lastExecution.timestamp)}</span>
            </div>
            
            <div className="metric-row">
              <span className="metric-label">Market:</span>
              <span className="metric-value">{lastExecution.market}</span>
            </div>
            
            <div className="metric-row">
              <span className="metric-label">Action:</span>
              <span className="metric-value">{lastExecution.action} {lastExecution.side}</span>
            </div>
            
            <div className="metric-row">
              <span className="metric-label">Price:</span>
              <span className="metric-value">{formatPrice(lastExecution.price)}</span>
            </div>
            
            <div className="metric-row">
              <span className="metric-label">Size:</span>
              <span className="metric-value">{lastExecution.quantity}</span>
            </div>
            
            <div className="metric-row">
              <span className="metric-label">Source:</span>
              <span className="metric-value">{lastExecution.source}</span>
            </div>
          </>
        ) : (
          <div className="no-execution">
            <span className="no-execution-text">No executions yet</span>
          </div>
        )}
      </div>
    </div>
  );
};