import React from 'react'
import './ConnectionStatus.css'

const ConnectionStatus = ({ status }) => {
  const getStatusText = () => {
    switch (status) {
      case 'connected':
        return 'Connected'
      case 'connecting':
        return 'Connecting...'
      case 'disconnected':
        return 'Disconnected'
      case 'error':
        return 'Connection Error'
      default:
        return 'Unknown'
    }
  }

  return (
    <div className="connection-status">
      <div className={`status-indicator ${status}`}></div>
      <span>{getStatusText()}</span>
    </div>
  )
}

export default ConnectionStatus