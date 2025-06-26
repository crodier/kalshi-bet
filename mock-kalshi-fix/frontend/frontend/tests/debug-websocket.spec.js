import { test, expect } from '@playwright/test';

test.describe('Debug WebSocket Messages', () => {
  test('log all WebSocket messages for MARKET_MAKER', async ({ page }) => {
    // Set up console logging
    const wsMessages = [];
    page.on('console', msg => {
      const text = msg.text();
      if (text.includes('Processing orderbook') || text.includes('WebSocket message:')) {
        console.log('PAGE LOG:', text);
        wsMessages.push(text);
      }
    });
    
    // Navigate to the application
    await page.goto('http://localhost:5173');
    await page.waitForLoadState('networkidle');
    
    // Add logging to websocket in page context
    await page.evaluate(() => {
      // Intercept WebSocket messages
      const originalSend = WebSocket.prototype.send;
      const originalOnMessage = Object.getOwnPropertyDescriptor(WebSocket.prototype, 'onmessage');
      
      WebSocket.prototype.send = function(...args) {
        console.log('WebSocket SEND:', args[0]);
        return originalSend.apply(this, args);
      };
      
      Object.defineProperty(WebSocket.prototype, 'onmessage', {
        set: function(handler) {
          const wrappedHandler = (event) => {
            try {
              const data = JSON.parse(event.data);
              console.log('WebSocket message:', data);
            } catch (e) {
              console.log('WebSocket raw message:', event.data);
            }
            if (handler) handler(event);
          };
          originalOnMessage.set.call(this, wrappedHandler);
        }
      });
    });
    
    // Click on MARKET_MAKER
    await page.waitForSelector('.grid-row', { timeout: 10000 });
    const marketRows = page.locator('.grid-row');
    const count = await marketRows.count();
    
    for (let i = 0; i < count; i++) {
      const ticker = await marketRows.nth(i).locator('.ticker').textContent();
      if (ticker.includes('MARKET_MAKER')) {
        await marketRows.nth(i).click();
        break;
      }
    }
    
    // Wait for order book
    await page.waitForSelector('.orderbook', { timeout: 5000 });
    
    // Monitor for 20 seconds and log all messages
    console.log('\n=== Starting WebSocket monitoring ===');
    await page.waitForTimeout(20000);
    
    console.log('\n=== WebSocket Message Summary ===');
    const messageTypes = {};
    wsMessages.forEach(msg => {
      if (msg.includes('type:')) {
        const typeMatch = msg.match(/type:\s*["']?(\w+)/);
        if (typeMatch) {
          const type = typeMatch[1];
          messageTypes[type] = (messageTypes[type] || 0) + 1;
        }
      }
    });
    
    console.log('Message types received:', messageTypes);
    console.log('Total messages:', wsMessages.length);
  });
});