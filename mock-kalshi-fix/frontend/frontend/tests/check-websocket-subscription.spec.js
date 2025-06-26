import { test, expect } from '@playwright/test';

test.describe('WebSocket Subscription Debug', () => {
  test('check WebSocket subscription and messages', async ({ page }) => {
    // Enable detailed logging
    page.on('console', msg => {
      console.log(`[${msg.type()}] ${msg.text()}`);
    });
    
    page.on('websocket', ws => {
      console.log(`WebSocket opened: ${ws.url()}`);
      ws.on('framesent', event => console.log('>> WS SEND:', event.payload));
      ws.on('framereceived', event => console.log('<< WS RECV:', event.payload));
      ws.on('close', () => console.log('WebSocket closed'));
    });
    
    // Navigate to the application
    await page.goto('http://localhost:5173');
    await page.waitForLoadState('networkidle');
    
    // Wait for WebSocket to connect
    await page.waitForTimeout(2000);
    
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
    
    console.log('\n=== Monitoring for 15 seconds ===');
    await page.waitForTimeout(15000);
    
    // Check the order book status
    const statusType = await page.locator('.status-type').textContent();
    const statusDetails = await page.locator('.status-details').textContent();
    
    console.log('\nFinal status:');
    console.log('Type:', statusType);
    console.log('Details:', statusDetails);
  });
});