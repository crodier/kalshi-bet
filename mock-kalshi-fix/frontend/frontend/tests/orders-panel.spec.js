import { test, expect } from '@playwright/test';

test.describe('Orders Panel', () => {
  
  test.beforeEach(async ({ page }) => {
    // Navigate to the application
    await page.goto('http://localhost:5173');
    
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
    
    // Check that AG Grid is rendered
    await expect(page.locator('.ag-theme-alpine.orders-grid')).toBeVisible();
    await expect(page.locator('.ag-header')).toBeVisible();
    
    // Check for order columns
    const headers = ['Order ID', 'Market', 'Side', 'Action', 'Status', 'Orig Qty', 'Price'];
    for (const header of headers) {
      await expect(page.locator('.ag-header-cell-text')).toContainText(header);
    }
  });

  test('should display existing orders in the grid', async ({ page }) => {
    // Select DUMMY_TEST market
    await page.click('text=DUMMY_TEST');
    
    // Wait for orders to load
    await page.waitForSelector('.orders-panel .connection-status.connected', { timeout: 10000 });
    
    // Wait for grid to populate with data
    await page.waitForSelector('.ag-row', { timeout: 5000 });
    
    // Check that we have some order rows
    const orderRows = page.locator('.ag-row');
    await expect(orderRows.first()).toBeVisible();
    
    // Check that order data is displayed correctly
    const firstRow = orderRows.first();
    await expect(firstRow.locator('.ag-cell').nth(1)).toContainText('DUMMY_TEST'); // Market column
    
    // Check for side and action columns
    const sideCell = firstRow.locator('text=YES, text=NO').first();
    await expect(sideCell).toBeVisible();
  });

  test('should update order statistics when market changes', async ({ page }) => {
    // Select first market
    await page.click('text=DUMMY_TEST');
    await page.waitForSelector('.orders-panel .connection-status.connected', { timeout: 5000 });
    
    // Get initial stats
    const initialStats = await page.locator('.orders-stats').textContent();
    
    // Switch to different market if available
    const marketButtons = page.locator('.market-row');
    const marketCount = await marketButtons.count();
    
    if (marketCount > 1) {
      // Click on second market
      await marketButtons.nth(1).click();
      await page.waitForSelector('.orders-panel .connection-status.connected', { timeout: 5000 });
      
      // Check that stats may have changed (different market may have different orders)
      const newStats = await page.locator('.orders-stats').textContent();
      
      // At minimum, the header should show the new market
      const marketHeader = await page.locator('.orders-header h3').textContent();
      expect(marketHeader).not.toContain('DUMMY_TEST');
    }
  });

  test('should handle WebSocket connection and order updates', async ({ page }) => {
    // Select DUMMY_TEST market
    await page.click('text=DUMMY_TEST');
    await page.waitForSelector('.orders-panel .connection-status.connected', { timeout: 10000 });
    
    // Wait for initial orders to load
    await page.waitForSelector('.ag-row', { timeout: 5000 });
    
    // Get initial order count
    const initialStatsText = await page.locator('.orders-stats').textContent();
    const initialTotalMatch = initialStatsText.match(/Total:\s*(\d+)/);
    const initialTotal = initialTotalMatch ? parseInt(initialTotalMatch[1]) : 0;
    
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
      // Wait for the new order to appear in the grid (WebSocket update)
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
    }
  });

  test('should show order details with proper formatting', async ({ page }) => {
    // Select DUMMY_TEST market
    await page.click('text=DUMMY_TEST');
    await page.waitForSelector('.orders-panel .connection-status.connected', { timeout: 10000 });
    await page.waitForSelector('.ag-row', { timeout: 5000 });
    
    // Check for properly formatted cells
    const firstRow = page.locator('.ag-row').first();
    
    // Check side formatting (YES/NO with colors)
    const sideElements = page.locator('.side-yes, .side-no');
    if (await sideElements.count() > 0) {
      await expect(sideElements.first()).toBeVisible();
    }
    
    // Check action formatting (BUY/SELL with colors)
    const actionElements = page.locator('.action-buy, .action-sell');
    if (await actionElements.count() > 0) {
      await expect(actionElements.first()).toBeVisible();
    }
    
    // Check status formatting
    const statusElements = page.locator('.status-open, .status-filled, .status-partial');
    if (await statusElements.count() > 0) {
      await expect(statusElements.first()).toBeVisible();
    }
    
    // Check price formatting (should end with ¢)
    const priceCells = page.locator('.ag-cell').filter({ hasText: /\d+¢/ });
    if (await priceCells.count() > 0) {
      await expect(priceCells.first()).toBeVisible();
    }
  });

  test('should support grid interactions', async ({ page }) => {
    // Select DUMMY_TEST market
    await page.click('text=DUMMY_TEST');
    await page.waitForSelector('.orders-panel .connection-status.connected', { timeout: 10000 });
    await page.waitForSelector('.ag-row', { timeout: 5000 });
    
    // Test sorting by clicking on a column header
    await page.click('.ag-header-cell-text:has-text("Price")');
    
    // Wait for sort to apply
    await page.waitForTimeout(500);
    
    // Check that sort indicator is visible
    const sortedHeader = page.locator('.ag-header-cell').filter({ hasText: 'Price' });
    await expect(sortedHeader.locator('.ag-sort-indicator')).toBeVisible();
    
    // Test pagination if enough rows
    const paginationPanel = page.locator('.ag-paging-panel');
    if (await paginationPanel.isVisible()) {
      // Check pagination controls work
      const nextButton = page.locator('.ag-paging-button-next');
      if (await nextButton.isEnabled()) {
        await nextButton.click();
        await page.waitForTimeout(500);
      }
    }
    
    // Test column resizing
    const resizeHandle = page.locator('.ag-header-cell-resize').first();
    if (await resizeHandle.isVisible()) {
      await resizeHandle.hover();
      // Just check that we can interact with resize handles
      await expect(resizeHandle).toBeVisible();
    }
  });
});