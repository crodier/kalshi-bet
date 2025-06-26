import axios from 'axios'

const api = axios.create({
  baseURL: '/api',
  timeout: 10000,
  headers: {
    'Content-Type': 'application/json'
  }
})

// Response interceptor for error handling
api.interceptors.response.use(
  response => response,
  error => {
    console.error('API Error:', error)
    return Promise.reject(error)
  }
)

export const adminAPI = {
  // System statistics
  async getSystemStats() {
    const response = await api.get('/admin/stats')
    return response.data
  },

  // Market operations
  async getAllMarkets() {
    const response = await api.get('/admin/markets')
    return response.data
  },

  async searchMarkets(query) {
    const response = await api.get(`/admin/markets/search?q=${encodeURIComponent(query)}`)
    return response.data
  },

  async getMarketDetails(marketTicker) {
    const response = await api.get(`/admin/markets/${marketTicker}`)
    return response.data
  },

  // Order book data
  async getOrderBook(marketTicker) {
    const response = await api.get(`/admin/orderbook/${marketTicker}`)
    return response.data
  },

  // Health and status
  async getHealth() {
    const response = await api.get('/health')
    return response.data
  },

  // Configuration
  async getConfig() {
    const response = await api.get('/admin/config')
    return response.data
  },

  async updateConfig(config) {
    const response = await api.put('/admin/config', config)
    return response.data
  }
}

export const actuatorAPI = {
  // Spring Boot Actuator endpoints
  async getMetrics() {
    const response = await axios.get('/actuator/metrics')
    return response.data
  },

  async getSpecificMetric(metricName) {
    const response = await axios.get(`/actuator/metrics/${metricName}`)
    return response.data
  },

  async getHealth() {
    const response = await axios.get('/actuator/health')
    return response.data
  },

  async getInfo() {
    const response = await axios.get('/actuator/info')
    return response.data
  }
}

export default { adminAPI, actuatorAPI }