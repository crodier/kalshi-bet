import React from 'react';
import './ConnectionStatus.css';

const ConnectionStatus = ({ isConnected, isConnecting, error, wasConnected }) => {
  if (isConnected) {
    return null; // Don't show banner when connected
  }

  const getStatusText = () => {
    if (isConnecting) {
      return 'Connecting to server...';
    }
    if (wasConnected) {
      return 'Disconnected from server (was connected)';
    }
    return 'Disconnected';
  };

  return (
    <div className={`connection-status ${error ? 'error' : 'warning'}`}>
      <div className="connection-status-content">
        <span className="connection-status-icon">⚠️</span>
        <span className="connection-status-text">
          {getStatusText()}
        </span>
        {error && (
          <span className="connection-status-error">({error})</span>
        )}
      </div>
    </div>
  );
};

export default ConnectionStatus;