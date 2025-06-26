import { useState, useEffect } from 'react';
import { getEnvironmentConfig } from '../services/environment/environmentConfig.js';

export const useEnvironmentConfig = () => {
  const [currentEnvironment, setCurrentEnvironment] = useState('Local');
  const [customUrls, setCustomUrls] = useState({});
  const [environmentConfig, setEnvironmentConfig] = useState(getEnvironmentConfig('Local'));

  // Load saved configuration from localStorage
  useEffect(() => {
    const savedEnvironment = localStorage.getItem('admin-frontend-environment');
    const savedCustomUrls = localStorage.getItem('admin-frontend-custom-urls');
    
    if (savedEnvironment) {
      setCurrentEnvironment(savedEnvironment);
    }
    
    if (savedCustomUrls) {
      try {
        setCustomUrls(JSON.parse(savedCustomUrls));
      } catch (e) {
        console.warn('Failed to parse saved custom URLs:', e);
      }
    }
  }, []);

  // Update environment config when environment or custom URLs change
  useEffect(() => {
    const baseConfig = getEnvironmentConfig(currentEnvironment);
    const finalConfig = {
      ...baseConfig,
      ...customUrls
    };
    setEnvironmentConfig(finalConfig);
  }, [currentEnvironment, customUrls]);

  const changeEnvironment = (newEnvironment) => {
    setCurrentEnvironment(newEnvironment);
    localStorage.setItem('admin-frontend-environment', newEnvironment);
  };

  const updateCustomUrl = (service, url) => {
    const newCustomUrls = {
      ...customUrls,
      [service]: url || undefined // Remove if empty
    };
    
    // Clean up undefined values
    Object.keys(newCustomUrls).forEach(key => {
      if (newCustomUrls[key] === undefined) {
        delete newCustomUrls[key];
      }
    });
    
    setCustomUrls(newCustomUrls);
    localStorage.setItem('admin-frontend-custom-urls', JSON.stringify(newCustomUrls));
  };

  const resetToDefaults = () => {
    setCustomUrls({});
    localStorage.removeItem('admin-frontend-custom-urls');
  };

  const saveConfiguration = () => {
    // Already auto-saved to localStorage
    return true;
  };

  return {
    currentEnvironment,
    environmentConfig,
    customUrls,
    changeEnvironment,
    updateCustomUrl,
    resetToDefaults,
    saveConfiguration
  };
};