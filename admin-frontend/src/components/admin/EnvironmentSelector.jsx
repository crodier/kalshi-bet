import { useState } from 'react';
import { validateWebSocketUrl } from '../../services/environment/environmentConfig.js';
import './EnvironmentSelector.css';

export const EnvironmentSelector = ({ 
  currentEnvironment, 
  environmentConfig, 
  customUrls, 
  onEnvironmentChange, 
  onCustomUrlChange 
}) => {
  const [localCustomUrls, setLocalCustomUrls] = useState(customUrls);

  const environments = ['Local', 'Test', 'Dev', 'QA', 'Prod'];
  
  const services = [
    { key: 'mockServerUrl', label: 'Mock Server' },
    { key: 'marketDataUrl', label: 'Market Data' },
    { key: 'orderRebuilderUrl', label: 'Order Rebuilder' },
    { key: 'tempOrdersUrl', label: 'Temp Orders' }
  ];

  const handleCustomUrlChange = (serviceKey, value) => {
    setLocalCustomUrls(prev => ({
      ...prev,
      [serviceKey]: value
    }));
  };

  const applyConfiguration = () => {
    Object.entries(localCustomUrls).forEach(([serviceKey, url]) => {
      onCustomUrlChange(serviceKey, url);
    });
  };

  const resetToDefaults = () => {
    setLocalCustomUrls({});
    services.forEach(service => {
      onCustomUrlChange(service.key, '');
    });
  };

  return (
    <div className="environment-selector">
      <div className="environment-header">
        <div className="environment-controls">
          <label htmlFor="environment-dropdown">Environment:</label>
          <select 
            id="environment-dropdown"
            value={currentEnvironment} 
            onChange={(e) => onEnvironmentChange(e.target.value)}
            className="environment-dropdown"
          >
            {environments.map(env => (
              <option key={env} value={env}>{env}</option>
            ))}
          </select>
          <button onClick={applyConfiguration} className="apply-btn">Apply</button>
          <button onClick={resetToDefaults} className="reset-btn">Reset</button>
        </div>
      </div>
      
      <div className="service-urls">
        {services.map(service => {
          const baseUrl = environmentConfig[service.key] || '';
          const customUrl = localCustomUrls[service.key] || '';
          const isCustom = customUrl && customUrl !== baseUrl;
          const isValidCustom = !customUrl || validateWebSocketUrl(customUrl);
          
          return (
            <div key={service.key} className="service-url-row">
              <label className="service-label">{service.label}:</label>
              <span className="base-url">{baseUrl}</span>
              <div className="custom-url-container">
                <input
                  type="text"
                  placeholder="Custom URL..."
                  value={customUrl}
                  onChange={(e) => handleCustomUrlChange(service.key, e.target.value)}
                  className={`custom-url-input ${!isValidCustom ? 'invalid' : ''} ${isCustom ? 'active' : ''}`}
                />
                {!isValidCustom && <span className="validation-error">Invalid WebSocket URL</span>}
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
};