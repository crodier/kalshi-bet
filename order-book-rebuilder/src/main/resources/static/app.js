// Order Book Viewer Application
class OrderBookApp {
    constructor() {
        this.currentMarket = 'SELF_TEST_MARKET';
        this.websocket = null;
        this.reconnectAttempts = 0;
        this.maxReconnectAttempts = 5;
        this.reconnectDelay = 3000;
        
        this.init();
    }
    
    init() {
        // Load initial data
        this.loadOrderBook();
        this.loadMarketsList();
        this.loadStats();
        
        // Set up auto-refresh
        setInterval(() => {
            if (!this.websocket || this.websocket.readyState !== WebSocket.OPEN) {
                this.loadOrderBook();
                this.loadStats();
            }
        }, 5000);
        
        // Set up periodic market list refresh
        setInterval(() => {
            this.loadMarketsList();
        }, 30000);
    }
    
    async loadOrderBook() {
        if (!this.currentMarket) return;
        
        try {
            const response = await fetch(`/trade-api/v2/markets/${this.currentMarket}/orderbook?depth=10`);
            if (response.ok) {
                const data = await response.json();
                this.displayOrderBook(data.orderbook);
                this.updateMetadata();
            } else {
                this.displayError('Order book not found');
            }
        } catch (error) {
            console.error('Error loading order book:', error);
            this.displayError('Failed to load order book');
        }
    }
    
    async loadMarketsList() {
        try {
            const response = await fetch('/api/v1/orderbook/all?depth=1');
            if (response.ok) {
                const markets = await response.json();
                this.displayMarketsList(markets);
            }
        } catch (error) {
            console.error('Error loading markets list:', error);
        }
    }
    
    async loadStats() {
        try {
            const response = await fetch('/api/v1/orderbook/stats');
            if (response.ok) {
                const stats = await response.json();
                this.displayStats(stats);
            }
        } catch (error) {
            console.error('Error loading stats:', error);
        }
    }
    
    displayOrderBook(orderbook) {
        document.getElementById('orderBookTitle').textContent = `Order Book - ${orderbook.market_ticker}`;
        
        // Display YES side
        const yesSideEl = document.getElementById('yesSideBook');
        const previousYesLevels = this.getCurrentLevels(yesSideEl);
        yesSideEl.innerHTML = '';
        
        if (orderbook.yes && orderbook.yes.length > 0) {
            orderbook.yes.forEach((level) => {
                const levelEl = document.createElement('div');
                const price = Array.isArray(level) ? level[0] : level.price;
                const size = Array.isArray(level) ? level[1] : level.size;
                
                // Check if this level changed
                const wasUpdated = this.checkLevelUpdate(previousYesLevels, price, size);
                levelEl.className = wasUpdated ? 'book-level updated' : 'book-level';
                
                // Build timing information if available
                let timingInfo = '';
                if (!Array.isArray(level) && level.timestamp) {
                    const updateTime = new Date(level.timestamp).toLocaleTimeString('en-US', { hour12: false, hour: '2-digit', minute: '2-digit', second: '2-digit', fractionalSecondDigits: 3 });
                    const kafkaTime = level.kafkaReceivedTimestamp ? new Date(level.kafkaReceivedTimestamp).toLocaleTimeString('en-US', { hour12: false, hour: '2-digit', minute: '2-digit', second: '2-digit', fractionalSecondDigits: 3 }) : '-';
                    const serverTime = level.orderServerTimestamp ? new Date(level.orderServerTimestamp).toLocaleTimeString('en-US', { hour12: false, hour: '2-digit', minute: '2-digit', second: '2-digit', fractionalSecondDigits: 3 }) : '-';
                    const kafkaLatency = level.latencyFromKafka || 0;
                    const totalLatency = level.totalLatency || 0;
                    
                    timingInfo = `
                        <div class="timing-info">
                            <div class="timing-row">Updated: ${updateTime}</div>
                            <div class="timing-row">Kafka: ${kafkaTime}</div>
                            <div class="timing-row">Server: ${serverTime}</div>
                            <div class="timing-row">Kafka Lat: ${kafkaLatency}ms</div>
                            <div class="timing-row">Total Lat: ${totalLatency}ms</div>
                        </div>
                    `;
                }
                
                levelEl.innerHTML = `
                    <div class="level-main">
                        <span class="price">${price}¢</span>
                        <span class="size">${size.toLocaleString()}</span>
                    </div>
                    ${timingInfo}
                `;
                yesSideEl.appendChild(levelEl);
            });
        } else {
            yesSideEl.innerHTML = '<div class="empty-book">No orders</div>';
        }
        
        // Display NO side
        const noSideEl = document.getElementById('noSideBook');
        const previousNoLevels = this.getCurrentLevels(noSideEl);
        noSideEl.innerHTML = '';
        
        if (orderbook.no && orderbook.no.length > 0) {
            orderbook.no.forEach((level) => {
                const levelEl = document.createElement('div');
                const price = Array.isArray(level) ? level[0] : level.price;
                const size = Array.isArray(level) ? level[1] : level.size;
                
                // Check if this level changed
                const wasUpdated = this.checkLevelUpdate(previousNoLevels, price, size);
                levelEl.className = wasUpdated ? 'book-level updated' : 'book-level';
                
                // Build timing information if available
                let timingInfo = '';
                if (!Array.isArray(level) && level.timestamp) {
                    const updateTime = new Date(level.timestamp).toLocaleTimeString('en-US', { hour12: false, hour: '2-digit', minute: '2-digit', second: '2-digit', fractionalSecondDigits: 3 });
                    const kafkaTime = level.kafkaReceivedTimestamp ? new Date(level.kafkaReceivedTimestamp).toLocaleTimeString('en-US', { hour12: false, hour: '2-digit', minute: '2-digit', second: '2-digit', fractionalSecondDigits: 3 }) : '-';
                    const serverTime = level.orderServerTimestamp ? new Date(level.orderServerTimestamp).toLocaleTimeString('en-US', { hour12: false, hour: '2-digit', minute: '2-digit', second: '2-digit', fractionalSecondDigits: 3 }) : '-';
                    const kafkaLatency = level.latencyFromKafka || 0;
                    const totalLatency = level.totalLatency || 0;
                    
                    timingInfo = `
                        <div class="timing-info">
                            <div class="timing-row">Updated: ${updateTime}</div>
                            <div class="timing-row">Kafka: ${kafkaTime}</div>
                            <div class="timing-row">Server: ${serverTime}</div>
                            <div class="timing-row">Kafka Lat: ${kafkaLatency}ms</div>
                            <div class="timing-row">Total Lat: ${totalLatency}ms</div>
                        </div>
                    `;
                }
                
                levelEl.innerHTML = `
                    <div class="level-main">
                        <span class="price">${price}¢</span>
                        <span class="size">${size.toLocaleString()}</span>
                    </div>
                    ${timingInfo}
                `;
                noSideEl.appendChild(levelEl);
            });
        } else {
            noSideEl.innerHTML = '<div class="empty-book">No orders</div>';
        }
    }
    
    displayMarketsList(markets) {
        const listEl = document.getElementById('marketsList');
        listEl.innerHTML = '';
        
        if (markets && markets.length > 0) {
            markets.forEach(market => {
                const itemEl = document.createElement('div');
                itemEl.className = 'market-item';
                itemEl.onclick = () => this.selectMarket(market.marketTicker);
                
                const bestYes = market.bestBid || market.bestYes;
                const bestNo = market.bestAsk || market.bestNo;
                
                itemEl.innerHTML = `
                    <div class="market-ticker">${market.marketTicker}</div>
                    <div class="market-info">
                        Yes: ${bestYes ? bestYes.price + '¢' : '-'} | 
                        No: ${bestNo ? bestNo.price + '¢' : '-'} |
                        Updated: ${new Date(market.lastUpdateTimestamp).toLocaleTimeString()}
                    </div>
                `;
                listEl.appendChild(itemEl);
            });
        } else {
            listEl.innerHTML = '<div class="loading">No markets available</div>';
        }
    }
    
    displayStats(stats) {
        document.getElementById('totalBooks').textContent = stats.totalOrderBooks || 0;
        document.getElementById('activeMarkets').textContent = stats.activeMarkets || 0;
        document.getElementById('closedMarkets').textContent = stats.closedMarkets || 0;
    }
    
    displayError(message) {
        const yesSideEl = document.getElementById('yesSideBook');
        const noSideEl = document.getElementById('noSideBook');
        
        yesSideEl.innerHTML = `<div class="empty-book">${message}</div>`;
        noSideEl.innerHTML = `<div class="empty-book">${message}</div>`;
    }
    
    updateMetadata() {
        document.getElementById('lastUpdate').textContent = `Last Update: ${new Date().toLocaleTimeString()}`;
        document.getElementById('latency').textContent = 'Latency: <100ms';
    }
    
    getCurrentLevels(containerEl) {
        const levels = new Map();
        const levelElements = containerEl.querySelectorAll('.book-level');
        levelElements.forEach(el => {
            const priceEl = el.querySelector('.price');
            const sizeEl = el.querySelector('.size');
            if (priceEl && sizeEl) {
                const price = parseInt(priceEl.textContent.replace('¢', ''));
                const size = parseInt(sizeEl.textContent.replace(/,/g, ''));
                levels.set(price, size);
            }
        });
        return levels;
    }
    
    checkLevelUpdate(previousLevels, price, size) {
        if (!previousLevels.has(price)) {
            return true; // New level
        }
        return previousLevels.get(price) !== size; // Size changed
    }
    
    selectMarket(ticker) {
        this.currentMarket = ticker;
        document.getElementById('marketTicker').value = ticker;
        this.loadOrderBook();
    }
    
    // WebSocket functionality
    connectWebSocket() {
        if (this.websocket && this.websocket.readyState === WebSocket.OPEN) {
            return;
        }
        
        try {
            this.websocket = new WebSocket(`ws://${window.location.host}/trade-api/ws/v2`);
            
            this.websocket.onopen = () => {
                console.log('WebSocket connected');
                this.updateConnectionStatus(true);
                this.reconnectAttempts = 0;
                
                // Subscribe to current market
                if (this.currentMarket) {
                    this.subscribeToMarket(this.currentMarket);
                }
            };
            
            this.websocket.onmessage = (event) => {
                try {
                    const message = JSON.parse(event.data);
                    this.handleWebSocketMessage(message);
                } catch (error) {
                    console.error('Error parsing WebSocket message:', error);
                }
            };
            
            this.websocket.onclose = () => {
                console.log('WebSocket disconnected');
                this.updateConnectionStatus(false);
                this.scheduleReconnect();
            };
            
            this.websocket.onerror = (error) => {
                console.error('WebSocket error:', error);
                this.updateConnectionStatus(false);
            };
            
        } catch (error) {
            console.error('Error creating WebSocket:', error);
            this.scheduleReconnect();
        }
    }
    
    disconnectWebSocket() {
        if (this.websocket) {
            this.websocket.close();
            this.websocket = null;
        }
        this.updateConnectionStatus(false);
    }
    
    subscribeToMarket(marketTicker) {
        if (!this.websocket || this.websocket.readyState !== WebSocket.OPEN) {
            return;
        }
        
        const subscribeMessage = {
            id: Date.now(),
            cmd: 'subscribe',
            params: {
                channels: ['orderbook_snapshot', 'orderbook_delta'],
                market_tickers: [marketTicker]
            }
        };
        
        this.websocket.send(JSON.stringify(subscribeMessage));
    }
    
    handleWebSocketMessage(message) {
        console.log('WebSocket message:', message);
        
        switch (message.type) {
            case 'orderbook_snapshot':
            case 'orderbook_delta':
                if (message.msg && message.msg.market_ticker === this.currentMarket) {
                    // Reload order book for now (could implement incremental updates)
                    this.loadOrderBook();
                }
                break;
            case 'response':
                console.log('Subscription response:', message);
                break;
            case 'error':
                console.error('WebSocket error message:', message);
                break;
        }
    }
    
    scheduleReconnect() {
        if (this.reconnectAttempts >= this.maxReconnectAttempts) {
            console.log('Max reconnect attempts reached');
            return;
        }
        
        this.reconnectAttempts++;
        setTimeout(() => {
            console.log(`Reconnect attempt ${this.reconnectAttempts}`);
            this.connectWebSocket();
        }, this.reconnectDelay * this.reconnectAttempts);
    }
    
    updateConnectionStatus(connected) {
        const statusEl = document.getElementById('connectionStatus');
        const btnEl = document.getElementById('connectBtn');
        
        if (connected) {
            statusEl.textContent = 'Connected';
            statusEl.className = 'status connected';
            btnEl.textContent = 'Disconnect';
        } else {
            statusEl.textContent = 'Disconnected';
            statusEl.className = 'status disconnected';
            btnEl.textContent = 'Connect';
        }
    }
}

// Global functions
function loadOrderBook() {
    const ticker = document.getElementById('marketTicker').value.trim();
    if (ticker) {
        app.currentMarket = ticker;
        app.loadOrderBook();
        
        // Resubscribe if WebSocket is connected
        if (app.websocket && app.websocket.readyState === WebSocket.OPEN) {
            app.subscribeToMarket(ticker);
        }
    }
}

function refreshAll() {
    app.loadOrderBook();
    app.loadMarketsList();
    app.loadStats();
}

function toggleWebSocket() {
    if (app.websocket && app.websocket.readyState === WebSocket.OPEN) {
        app.disconnectWebSocket();
    } else {
        app.connectWebSocket();
    }
}

// Initialize app when page loads
const app = new OrderBookApp();