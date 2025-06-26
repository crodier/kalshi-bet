import axios from 'axios';

const API_BASE_URL = 'http://localhost:9090/trade-api/v2';

const api = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    'Content-Type': 'application/json'
  }
});

// Order API endpoints
export const orderAPI = {
  createOrder: (orderData) => api.post('/portfolio/orders', orderData),
  getOrders: () => api.get('/portfolio/orders'),
  getOrderById: (orderId) => api.get(`/portfolio/orders/${orderId}`),
  cancelOrder: (orderId) => api.delete(`/portfolio/orders/${orderId}`),
  createBatchOrders: (orders) => api.post('/portfolio/batch_orders', orders)
};

// Portfolio API endpoints
export const portfolioAPI = {
  getBalance: () => api.get('/portfolio/balance'),
  getPositions: () => api.get('/portfolio/positions'),
  getFills: () => api.get('/portfolio/fills')
};

// Market Data API endpoints
export const marketAPI = {
  getOrderbook: (marketTicker) => {
    const url = `/markets/${marketTicker}/orderbook`;
    console.log('Fetching orderbook from:', API_BASE_URL + url);
    return api.get(url);
  },
  getTrades: (marketTicker) => api.get('/markets/trades', { params: { ticker: marketTicker } }),
  getMarkets: () => api.get('/markets') // This endpoint might need to be implemented
};

export default api;