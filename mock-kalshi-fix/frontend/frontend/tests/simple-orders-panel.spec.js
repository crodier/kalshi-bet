import { test, expect } from '@playwright/test';

test.describe('Simple Orders Panel', () => {
  
  test.beforeEach(async ({ page }) => {
    // Navigate to the application
    await page.goto('http://localhost:5176');
    
    // Wait for the page to load
    await expect(page.locator('h1')).toContainText('Mock Kalshi Trading Platform');
    
    // Wait for markets to load
    await page.waitForSelector('.market-grid', { timeout: 10000 });
  });

  test('should show "Select a market" message when no market is selected', async ({ page }) => {
    // Check that orders panel shows select market message
    await expect(page.locator('.orders-panel')).toBeVisible();
    await expect(page.locator('.orders-panel')).toContainText('Select a market to view orders');
    await expect(page.locator('.orders-panel .connection-status.disconnected')).toBeVisible();
  });

  test('should load orders when a market is selected', async ({ page }) => {
    // Select a market (click on DUMMY_TEST)
    await page.click('text=DUMMY_TEST');
    
    // Wait for orders panel to load orders
    await expect(page.locator('.orders-panel h3')).toContainText('Orders - DUMMY_TEST');
    
    // Check that connection status shows as connected
    await expect(page.locator('.orders-panel .connection-status.connected')).toBeVisible({ timeout: 5000 });
    await expect(page.locator('.orders-panel .connection-status')).toContainText('🔗 Live');
    
    // Check that orders statistics are displayed
    await expect(page.locator('.orders-stats')).toBeVisible();
    await expect(page.locator('.orders-stats .stat').first()).toContainText('Total:');
    
    // Check that the simple table is rendered
    await expect(page.locator('.simple-orders-table')).toBeVisible();
    await expect(page.locator('.simple-orders-table table')).toBeVisible();
    
    // Check for table headers
    const headers = ['Order ID', 'Side', 'Action', 'Status', 'Qty', 'Price'];
    for (const header of headers) {
      await expect(page.locator('th')).toContainText(header);
    }
  });

  test('should display existing orders in the table', async ({ page }) => {
    // Select DUMMY_TEST market
    await page.click('text=DUMMY_TEST');
    
    // Wait for orders to load
    await page.waitForSelector('.orders-panel .connection-status.connected', { timeout: 10000 });
    
    // Wait for table content
    await page.waitForTimeout(2000);
    
    // Check if we have any orders in the table
    const rows = page.locator('.simple-orders-table tbody tr');
    const rowCount = await rows.count();
    
    if (rowCount > 0) {
      // If we have orders, verify the first one has proper data
      const firstRow = rows.first();
      await expect(firstRow.locator('td').nth(0)).toBeVisible(); // Order ID
      await expect(firstRow.locator('td').nth(1)).toBeVisible(); // Side
      await expect(firstRow.locator('td').nth(2)).toBeVisible(); // Action
    } else {
      // If no orders, check that we show the "No orders found" message
      await expect(page.locator('.simple-orders-table tbody')).toContainText('No orders found for this market');
    }
  });

  test('should handle order creation and real-time updates', async ({ page }) => {
    // Select DUMMY_TEST market
    await page.click('text=DUMMY_TEST');
    await page.waitForSelector('.orders-panel .connection-status.connected', { timeout: 10000 });
    
    // Wait for initial load
    await page.waitForTimeout(2000);
    
    // Get initial order count
    const initialStatsText = await page.locator('.orders-stats').textContent();
    const initialTotalMatch = initialStatsText.match(/Total:\s*(\d+)/);
    const initialTotal = initialTotalMatch ? parseInt(initialTotalMatch[1]) : 0;
    
    console.log('Initial total orders:', initialTotal);
    
    // Create a new order via API to test real-time updates
    const response = await page.evaluate(async () => {
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
            price: 45,
            time_in_force: 'GTC',
            action: 'buy'
          })
        });
        return response.ok;
      } catch (error) {
        console.error('Failed to create order:', error);
        return false;
      }
    });
    
    if (response) {
      // Wait for the new order to appear via WebSocket or refresh
      await page.waitForFunction(
        (initialCount) => {
          const statsText = document.querySelector('.orders-stats')?.textContent || '';
          const totalMatch = statsText.match(/Total:\s*(\d+)/);
          const currentTotal = totalMatch ? parseInt(totalMatch[1]) : 0;
          return currentTotal > initialCount;
        },
        initialTotal,
        { timeout: 10000 }
      );
      
      // Verify the stats updated
      const updatedStatsText = await page.locator('.orders-stats').textContent();
      const updatedTotalMatch = updatedStatsText.match(/Total:\s*(\d+)/);
      const updatedTotal = updatedTotalMatch ? parseInt(updatedTotalMatch[1]) : 0;
      
      expect(updatedTotal).toBeGreaterThan(initialTotal);
      console.log('Updated total orders:', updatedTotal);
    }
  });

  test('should display order data with proper formatting', async ({ page }) => {
    // Select DUMMY_TEST market
    await page.click('text=DUMMY_TEST');
    await page.waitForSelector('.orders-panel .connection-status.connected', { timeout: 10000 });
    await page.waitForTimeout(2000);
    
    // Check if we have any orders to verify formatting
    const rows = page.locator('.simple-orders-table tbody tr');
    const rowCount = await rows.count();
    
    if (rowCount > 0) {
      // Check for proper formatting classes and content
      const firstRow = rows.first();
      
      // Check for side formatting (YES/NO)
      const sideCell = firstRow.locator('td').nth(1);
      const sideText = await sideCell.textContent();
      expect(['YES', 'NO']).toContain(sideText.trim());
      
      // Check for action formatting (BUY/SELL)
      const actionCell = firstRow.locator('td').nth(2);
      const actionText = await actionCell.textContent();
      expect(['BUY', 'SELL']).toContain(actionText.trim());
      
      // Check price formatting (should end with ¢)
      const priceCell = firstRow.locator('td').nth(6);
      const priceText = await priceCell.textContent();
      expect(priceText).toMatch(/\d+¢/);
    }
  });
});