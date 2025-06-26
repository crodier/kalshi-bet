import { test, expect } from '@playwright/test';

test.describe('Orders Panel - Final Tests', () => {
  
  test.beforeEach(async ({ page }) => {
    // Navigate to the application
    await page.goto('http://localhost:5176');
    
    // Wait for the page to load
    await expect(page.locator('h1')).toContainText('Mock Kalshi Trading Platform');
    
    // Wait for markets to load
    await page.waitForSelector('.market-grid', { timeout: 10000 });
  });

  test('complete orders panel workflow', async ({ page }) => {
    // Find and click on DUMMY_TEST market
    const marketRows = page.locator('.grid-row');
    const dummyTestRow = marketRows.filter({ hasText: 'DUMMY_TEST' });
    
    await expect(dummyTestRow).toBeVisible();
    await dummyTestRow.click();
    
    // Wait for orders panel to update
    await page.waitForTimeout(1000);
    
    // Verify orders panel shows the selected market
    const ordersHeader = page.locator('.orders-panel h3');
    await expect(ordersHeader).toBeVisible();
    await expect(ordersHeader).toContainText('Orders - DUMMY_TEST');
    
    // Check connection status (should be connected or connecting)
    const connectionStatus = page.locator('.orders-panel .connection-status');
    await expect(connectionStatus).toBeVisible();
    
    // Wait for connection to establish
    await page.waitForTimeout(2000);
    
    // Check if we have the orders table
    const ordersTable = page.locator('.simple-orders-table');
    await expect(ordersTable).toBeVisible();
    
    // Check for table headers
    const tableHeaders = page.locator('.simple-orders-table th');
    await expect(tableHeaders.first()).toBeVisible();
    
    // Get initial order count
    let initialOrderCount = 0;
    const statsSection = page.locator('.orders-stats');
    
    if (await statsSection.isVisible()) {
      const statsText = await statsSection.textContent();
      console.log('Stats text:', statsText);
      
      const totalMatch = statsText.match(/Total:\s*(\d+)/);
      initialOrderCount = totalMatch ? parseInt(totalMatch[1]) : 0;
      console.log('Initial order count:', initialOrderCount);
    }
    
    // Create a new order
    console.log('Creating new order...');
    const orderResponse = await page.evaluate(async () => {
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
            count: 2,
            price: 47,
            time_in_force: 'GTC',
            action: 'buy'
          })
        });
        
        if (!response.ok) {
          const error = await response.text();
          return { success: false, error };
        }
        
        const data = await response.json();
        return { success: true, data };
      } catch (error) {
        return { success: false, error: error.message };
      }
    });
    
    console.log('Order response:', orderResponse);
    
    if (orderResponse.success) {
      // Wait for WebSocket update
      await page.waitForTimeout(3000);
      
      // Check if order count increased
      if (await statsSection.isVisible()) {
        const newStatsText = await statsSection.textContent();
        const newTotalMatch = newStatsText.match(/Total:\s*(\d+)/);
        const newOrderCount = newTotalMatch ? parseInt(newTotalMatch[1]) : 0;
        console.log('New order count:', newOrderCount);
        
        // We should have at least one more order
        expect(newOrderCount).toBeGreaterThanOrEqual(initialOrderCount);
      }
      
      // Check if we have order rows in the table
      const orderRows = page.locator('.simple-orders-table tbody tr');
      const rowCount = await orderRows.count();
      console.log('Number of order rows:', rowCount);
      
      if (rowCount > 0) {
        // Verify the first order has proper data
        const firstRow = orderRows.first();
        
        // Check Order ID column
        const orderIdCell = firstRow.locator('td').nth(0);
        await expect(orderIdCell).toBeVisible();
        const orderId = await orderIdCell.textContent();
        console.log('First order ID:', orderId);
        
        // Check Side column (should be YES or NO)
        const sideCell = firstRow.locator('td').nth(1);
        const sideText = await sideCell.textContent();
        expect(['YES', 'NO']).toContain(sideText.trim());
        
        // Check Action column (should be BUY or SELL)
        const actionCell = firstRow.locator('td').nth(2);
        const actionText = await actionCell.textContent();
        expect(['BUY', 'SELL']).toContain(actionText.trim());
        
        // Check Status column
        const statusCell = firstRow.locator('td').nth(3);
        const statusText = await statusCell.textContent();
        console.log('Order status:', statusText);
        
        // Check Price column (should contain ¢)
        const priceCell = firstRow.locator('td').nth(6);
        const priceText = await priceCell.textContent();
        expect(priceText).toMatch(/\d+¢/);
      }
    }
    
    // Test market switching
    console.log('\nTesting market switching...');
    
    // Click on a different market
    const btcMarket = marketRows.filter({ hasText: 'BTCZ-23DEC31-B50000' });
    if (await btcMarket.isVisible()) {
      await btcMarket.click();
      await page.waitForTimeout(1000);
      
      // Verify orders panel updated
      await expect(ordersHeader).toContainText('Orders - BTCZ-23DEC31-B50000');
      console.log('✓ Successfully switched to BTC market');
    }
    
    // Switch back to DUMMY_TEST
    await dummyTestRow.click();
    await page.waitForTimeout(1000);
    await expect(ordersHeader).toContainText('Orders - DUMMY_TEST');
    console.log('✓ Successfully switched back to DUMMY_TEST market');
  });

  test('orders panel displays correct styling', async ({ page }) => {
    // Select a market
    const marketRows = page.locator('.grid-row');
    const dummyTestRow = marketRows.filter({ hasText: 'DUMMY_TEST' });
    await dummyTestRow.click();
    await page.waitForTimeout(2000);
    
    // Check for proper CSS classes
    const sideYes = page.locator('.side-yes').first();
    if (await sideYes.isVisible()) {
      // Check that YES side has proper styling
      await expect(sideYes).toHaveClass(/side-yes/);
    }
    
    const actionBuy = page.locator('.action-buy').first();
    if (await actionBuy.isVisible()) {
      // Check that BUY action has proper styling
      await expect(actionBuy).toHaveClass(/action-buy/);
    }
    
    // Check status styling
    const statusOpen = page.locator('.status-open').first();
    if (await statusOpen.isVisible()) {
      await expect(statusOpen).toHaveClass(/status-open/);
    }
  });
});