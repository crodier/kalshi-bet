export const environmentConfigs = {
  Local: {
    mockServerUrl: 'ws://localhost:9090/ws',
    marketDataUrl: 'ws://localhost:8080/ws',
    orderRebuilderUrl: 'ws://localhost:8081/ws',
    tempOrdersUrl: 'ws://localhost:8082/ws'
  },
  Test: {
    mockServerUrl: 'ws://localhost:19090/ws',
    marketDataUrl: 'ws://localhost:18080/ws',
    orderRebuilderUrl: 'ws://localhost:18081/ws',
    tempOrdersUrl: 'ws://localhost:18082/ws'
  },
  Dev: {
    mockServerUrl: 'wss://dev-mock.kalshi.com/ws',
    marketDataUrl: 'wss://dev-market-data.kalshi.com/ws',
    orderRebuilderUrl: 'wss://dev-order-rebuilder.kalshi.com/ws',
    tempOrdersUrl: 'wss://dev-temp-orders.kalshi.com/ws'
  },
  QA: {
    mockServerUrl: 'wss://qa-mock.kalshi.com/ws',
    marketDataUrl: 'wss://qa-market-data.kalshi.com/ws',
    orderRebuilderUrl: 'wss://qa-order-rebuilder.kalshi.com/ws',
    tempOrdersUrl: 'wss://qa-temp-orders.kalshi.com/ws'
  },
  Prod: {
    mockServerUrl: 'wss://mock.kalshi.com/ws',
    marketDataUrl: 'wss://market-data.kalshi.com/ws',
    orderRebuilderUrl: 'wss://order-rebuilder.kalshi.com/ws',
    tempOrdersUrl: 'wss://temp-orders.kalshi.com/ws'
  }
};

export const getEnvironmentConfig = (environment) => {
  return environmentConfigs[environment] || environmentConfigs.Local;
};

export const validateWebSocketUrl = (url) => {
  try {
    const urlObj = new URL(url);
    return urlObj.protocol === 'ws:' || urlObj.protocol === 'wss:';
  } catch {
    return false;
  }
};