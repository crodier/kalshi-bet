import React from 'react'
import './SystemStats.css'

const SystemStats = ({ stats, onRefresh }) => {
  if (!stats) {
    return (
      <div className="card">
        <div className="card-header">
          <h2 className="card-title">System Statistics</h2>
          <button className="refresh-button" onClick={onRefresh}>
            Refresh
          </button>
        </div>
        <div className="loading">Loading statistics...</div>
      </div>
    )
  }

  return (
    <div className="card">
      <div className="card-header">
        <h2 className="card-title">System Statistics</h2>
        <button className="refresh-button" onClick={onRefresh}>
          Refresh
        </button>
      </div>
      
      <div className="stats-grid">
        <div className="stat-item">
          <div className="stat-label">Total Markets</div>
          <div className="stat-value">{stats.totalMarkets}</div>
        </div>
        
        <div className="stat-item">
          <div className="stat-label">Active Markets</div>
          <div className="stat-value">{stats.activeMarkets}</div>
        </div>
        
        <div className="stat-item">
          <div className="stat-label">Bootstrapped Markets</div>
          <div className="stat-value">{stats.bootstrappedMarkets}</div>
        </div>
        
        <div className="stat-item">
          <div className="stat-label">Messages/sec</div>
          <div className="stat-value">{stats.messagesPerSecond}</div>
        </div>
        
        <div className="stat-item">
          <div className="stat-label">Average Latency</div>
          <div className="stat-value">{stats.averageLatency}ms</div>
        </div>
        
        <div className="stat-item">
          <div className="stat-label">Max Latency</div>
          <div className="stat-value">{stats.maxLatency}ms</div>
        </div>
        
        <div className="stat-item">
          <div className="stat-label">Messages Received</div>
          <div className="stat-value">{stats.totalMessagesReceived?.toLocaleString()}</div>
        </div>
        
        <div className="stat-item">
          <div className="stat-label">Messages Published</div>
          <div className="stat-value">{stats.totalMessagesPublished?.toLocaleString()}</div>
        </div>
        
        <div className="stat-item">
          <div className="stat-label">Messages Skipped</div>
          <div className="stat-value">{stats.totalMessagesSkipped?.toLocaleString()}</div>
        </div>
        
        <div className="stat-item">
          <div className="stat-label">Skip Rate</div>
          <div className="stat-value">{stats.skipRate}%</div>
        </div>
        
        <div className="stat-item">
          <div className="stat-label">WebSocket Status</div>
          <div className={`stat-value status-${stats.webSocketStatus?.toLowerCase()}`}>
            {stats.webSocketStatus}
          </div>
        </div>
        
        <div className="stat-item">
          <div className="stat-label">Last Update</div>
          <div className="stat-value">
            {stats.lastUpdateTime ? new Date(stats.lastUpdateTime).toLocaleTimeString() : 'Never'}
          </div>
        </div>
      </div>
      
      <div className="performance-metrics">
        <h3>Performance Metrics</h3>
        <div className="metrics-row">
          <div className="metric">
            <span className="metric-label">Max Throughput:</span>
            <span className="metric-value">{stats.maxThroughput} msg/sec</span>
          </div>
          <div className="metric">
            <span className="metric-label">Uptime:</span>
            <span className="metric-value">{stats.uptime}</span>
          </div>
        </div>
      </div>
    </div>
  )
}

export default SystemStats