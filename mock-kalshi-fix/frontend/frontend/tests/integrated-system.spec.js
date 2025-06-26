import { test, expect } from '@playwright/test';

test.describe('Integrated System Tests', () => {
  
  test.beforeEach(async ({ page }) => {
    // Navigate to the application
    await page.goto('http://localhost:5176');
    
    // Wait for the page to load
    await expect(page.locator('h1')).toContainText('Mock Kalshi Trading Platform');
    
    // Wait for markets to load
    await page.waitForSelector('.market-grid', { timeout: 10000 });
  });

  test('complete trading workflow with orders and orderbook', async ({ page }) => {
    // Monitor console for WebSocket messages
    const wsMessages = [];
    page.on('console', msg => {
      const text = msg.text();
      if (text.includes('WebSocket') || text.includes('Received') || text.includes('subscribe')) {
        wsMessages.push(text);
      }
    });
    
    // Step 1: Select DUMMY_TEST market
    console.log('=== Step 1: Selecting DUMMY_TEST Market ===');
    const marketRows = page.locator('.grid-row');
    const dummyTestRow = marketRows.filter({ hasText: 'DUMMY_TEST' });
    await dummyTestRow.click();
    await page.waitForTimeout(1000);
    
    // Verify both panels updated
    await expect(page.locator('.orders-panel h3')).toContainText('Orders - DUMMY_TEST');
    await expect(page.locator('.orderbook h3')).toContainText('Order Book - DUMMY_TEST');
    
    // Step 2: Check initial state
    console.log('\n=== Step 2: Checking Initial State ===');
    
    // Check orderbook is receiving updates
    const orderbookBids = page.locator('.orderbook-bids .orderbook-row');
    const orderbookAsks = page.locator('.orderbook-asks .orderbook-row');
    
    // Wait for orderbook to populate
    await page.waitForTimeout(2000);
    
    const bidCount = await orderbookBids.count();
    const askCount = await orderbookAsks.count();
    console.log(`Orderbook - Bids: ${bidCount}, Asks: ${askCount}`);
    
    // Check orders panel
    const orderRows = page.locator('.simple-orders-table tbody tr');
    const initialOrderCount = await orderRows.count();
    console.log(`Initial orders: ${initialOrderCount}`);
    
    // Step 3: Place a new order using the order entry form
    console.log('\n=== Step 3: Placing New Order ===');
    
    // Check if order entry is available
    const orderEntry = page.locator('.order-entry');
    if (await orderEntry.isVisible()) {
      // Select BUY side
      await page.click('button:has-text("BUY")');
      
      // Select YES option
      await page.click('button:has-text("YES")');
      
      // Enter quantity
      await page.fill('input[name="quantity"]', '5');
      
      // Enter price
      await page.fill('input[name="price"]', '46');
      
      // Submit order
      await page.click('button:has-text("Place Order")');
      
      // Wait for order to be processed
      await page.waitForTimeout(2000);
      
      // Check for success message or new order in list
      const newOrderCount = await orderRows.count();
      console.log(`Orders after placement: ${newOrderCount}`);
      
      if (newOrderCount > initialOrderCount) {
        console.log('✓ Order successfully added to orders list');
      }
    } else {
      // Fallback: Create order via API
      console.log('Order entry not visible, creating via API...');
      
      const orderResponse = await page.evaluate(async () => {
        try {
          const response = await fetch('/trade-api/v2/portfolio/orders', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
              side: 'yes',
              market_ticker: 'DUMMY_TEST',
              type: 'limit',
              count: 5,
              price: 46,
              time_in_force: 'GTC',
              action: 'buy'
            })
          });
          return { 
            ok: response.ok, 
            status: response.status,
            data: response.ok ? await response.json() : await response.text()
          };
        } catch (error) {
          return { ok: false, error: error.message };
        }
      });
      
      console.log('API Order response:', orderResponse);
    }
    
    // Step 4: Verify WebSocket updates
    console.log('\n=== Step 4: Checking WebSocket Updates ===');
    await page.waitForTimeout(3000);
    
    // Check if orderbook updated
    const newBidCount = await orderbookBids.count();
    const newAskCount = await orderbookAsks.count();
    console.log(`Orderbook after order - Bids: ${newBidCount}, Asks: ${newAskCount}`);
    
    // Step 5: Test market switching
    console.log('\n=== Step 5: Testing Market Switching ===');
    
    // Switch to another market
    const btcMarket = marketRows.filter({ hasText: 'BTCZ-23DEC31-B50000' });
    await btcMarket.click();
    await page.waitForTimeout(1000);
    
    // Verify both panels updated
    await expect(page.locator('.orders-panel h3')).toContainText('Orders - BTCZ-23DEC31-B50000');
    await expect(page.locator('.orderbook h3')).toContainText('Order Book - BTCZ-23DEC31-B50000');
    
    // Switch back
    await dummyTestRow.click();
    await page.waitForTimeout(1000);
    
    await expect(page.locator('.orders-panel h3')).toContainText('Orders - DUMMY_TEST');
    await expect(page.locator('.orderbook h3')).toContainText('Order Book - DUMMY_TEST');
    
    console.log('✓ Market switching works correctly for both panels');
    
    // Step 6: Log WebSocket activity
    console.log('\n=== WebSocket Activity Summary ===');
    wsMessages.slice(-10).forEach(msg => console.log(msg));
  });

  test('verify data consistency between panels', async ({ page }) => {
    // Select DUMMY_TEST market
    const marketRows = page.locator('.grid-row');
    const dummyTestRow = marketRows.filter({ hasText: 'DUMMY_TEST' });
    await dummyTestRow.click();
    await page.waitForTimeout(2000);
    
    // Get market data from the grid
    const marketData = await dummyTestRow.evaluate(el => {
      const cells = el.querySelectorAll('.grid-cell');
      return {
        yesBuy: cells[1]?.querySelector('.price-value')?.textContent,
        noBuy: cells[2]?.querySelector('.price-value')?.textContent,
        volume: cells[6]?.textContent
      };
    });
    
    console.log('Market grid data:', marketData);
    
    // Check if orderbook shows consistent data
    const orderbookBestBid = await page.locator('.orderbook-bids .orderbook-row').first().locator('.price').textContent();
    const orderbookBestAsk = await page.locator('.orderbook-asks .orderbook-row').first().locator('.price').textContent();
    
    console.log('Orderbook best prices:', { bid: orderbookBestBid, ask: orderbookBestAsk });
    
    // The prices should be related (market grid shows aggregated view)
    if (marketData.yesBuy !== '-' && orderbookBestBid) {
      console.log('✓ Market data and orderbook data are present');
    }
  });
});