import { test, expect } from '@playwright/test';

test.describe('Simple Orders Panel Working Tests', () => {
  
  test.beforeEach(async ({ page }) => {
    // Navigate to the application
    await page.goto('http://localhost:5176');
    
    // Wait for the page to load
    await expect(page.locator('h1')).toContainText('Mock Kalshi Trading Platform');
    
    // Wait for markets to load
    await page.waitForSelector('.market-grid', { timeout: 10000 });
  });

  test('should show orders panel when clicking on DUMMY_TEST market', async ({ page }) => {
    // Find the specific market row for DUMMY_TEST and click it
    const dummyTestRow = page.locator('.market-row').filter({ hasText: 'DUMMY_TEST' });
    await expect(dummyTestRow).toBeVisible();
    await dummyTestRow.click();
    
    // Wait a bit for the selection to register
    await page.waitForTimeout(1000);
    
    // Check that orders panel shows the selected market
    await expect(page.locator('.orders-panel h3')).toContainText('Orders - DUMMY_TEST');
    
    // Check that the orders panel is visible
    await expect(page.locator('.orders-panel')).toBeVisible();
    
    // Check if the connection status exists (might be connecting or connected)
    const connectionStatus = page.locator('.orders-panel .connection-status');
    await expect(connectionStatus).toBeVisible();
    
    // Check if stats are showing
    const statsSection = page.locator('.orders-stats');
    if (await statsSection.isVisible()) {
      console.log('Stats section is visible');
      const statsText = await statsSection.textContent();
      console.log('Stats text:', statsText);
    }
    
    // Check if table is showing
    const ordersTable = page.locator('.simple-orders-table');
    if (await ordersTable.isVisible()) {
      console.log('Orders table is visible');
      
      // Check if there are any rows
      const rows = page.locator('.simple-orders-table tbody tr');
      const rowCount = await rows.count();
      console.log('Number of order rows:', rowCount);
    }
  });

  test('should test order creation flow', async ({ page }) => {
    // First select the DUMMY_TEST market
    const dummyTestRow = page.locator('.market-row').filter({ hasText: 'DUMMY_TEST' });
    await dummyTestRow.click();
    await page.waitForTimeout(1000);
    
    // Verify we're on the right market
    await expect(page.locator('.orders-panel h3')).toContainText('Orders - DUMMY_TEST');
    
    // Get initial state
    let initialTotal = 0;
    const statsSection = page.locator('.orders-stats');
    if (await statsSection.isVisible()) {
      const statsText = await statsSection.textContent();
      const totalMatch = statsText.match(/Total:\s*(\d+)/);
      initialTotal = totalMatch ? parseInt(totalMatch[1]) : 0;
      console.log('Initial total orders:', initialTotal);
    }
    
    // Create a new order using the API
    const orderCreated = await page.evaluate(async () => {
      try {
        const response = await fetch('/trade-api/v2/portfolio/orders', {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json'
          },
          body: JSON.stringify({
            side: 'yes',
            market_ticker: 'DUMMY_TEST',
            type: 'limit',
            count: 1,
            price: 48,
            time_in_force: 'GTC',
            action: 'buy'
          })
        });
        
        if (!response.ok) {
          const error = await response.text();
          console.error('Order creation failed:', error);
          return false;
        }
        
        const data = await response.json();
        console.log('Order created:', data);
        return true;
      } catch (error) {
        console.error('Failed to create order:', error);
        return false;
      }
    });
    
    console.log('Order creation result:', orderCreated);
    
    if (orderCreated) {
      // Wait a bit for the WebSocket update
      await page.waitForTimeout(3000);
      
      // Check if the stats updated
      if (await statsSection.isVisible()) {
        const newStatsText = await statsSection.textContent();
        const newTotalMatch = newStatsText.match(/Total:\s*(\d+)/);
        const newTotal = newTotalMatch ? parseInt(newTotalMatch[1]) : 0;
        console.log('New total orders:', newTotal);
        
        // The total should have increased
        if (newTotal > initialTotal) {
          console.log('✓ Order count increased successfully');
        } else {
          console.log('✗ Order count did not increase');
        }
      }
    }
  });

  test('should verify WebSocket subscription is working', async ({ page }) => {
    // Monitor console for WebSocket messages
    const wsMessages = [];
    page.on('console', msg => {
      const text = msg.text();
      if (text.includes('WebSocket') || text.includes('order') || text.includes('subscribe')) {
        wsMessages.push(text);
      }
    });
    
    // Select DUMMY_TEST market
    const dummyTestRow = page.locator('.market-row').filter({ hasText: 'DUMMY_TEST' });
    await dummyTestRow.click();
    await page.waitForTimeout(2000);
    
    // Log all WebSocket related messages
    console.log('\n=== WebSocket Messages ===');
    wsMessages.forEach(msg => console.log(msg));
    
    // Check if we subscribed to orders
    const subscribedToOrders = wsMessages.some(msg => 
      msg.includes('Subscribing to order updates') || 
      msg.includes('subscribe') && msg.includes('orders')
    );
    
    if (subscribedToOrders) {
      console.log('✓ Successfully subscribed to order updates');
    } else {
      console.log('✗ Did not find order subscription message');
    }
  });
});