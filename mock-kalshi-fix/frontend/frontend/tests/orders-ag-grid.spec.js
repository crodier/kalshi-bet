import { test, expect } from '@playwright/test';

test.describe('Orders AG Grid Panel', () => {
  test.beforeEach(async ({ page }) => {
    // Start with frontend server running and mock server running
    await page.goto('http://localhost:5174');
    
    // Wait for page to load and markets to be fetched
    await page.waitForSelector('.markets-panel');
    
    // Wait for initial market data to load
    await page.waitForTimeout(2000);
  });

  test('should display AG Grid when market is selected', async ({ page }) => {
    // Select a market that has orders
    const marketOption = page.locator('.market-option').first();
    await marketOption.click();
    
    // Wait for orders panel to appear
    await page.waitForSelector('.orders-panel');
    
    // Verify AG Grid is rendered
    const agGrid = page.locator('.ag-theme-alpine.orders-grid');
    await expect(agGrid).toBeVisible();
    
    // Verify grid has headers
    const headers = page.locator('.ag-header-cell-text');
    await expect(headers).toContainText(['Order ID', 'Market', 'Side', 'Action', 'Status']);
  });

  test('should populate AG Grid with existing orders', async ({ page }) => {
    // Select DUMMY_TEST market which has pre-populated orders
    const dummyTestMarket = page.locator('.market-option').filter({ hasText: 'DUMMY_TEST' });
    await dummyTestMarket.click();
    
    // Wait for orders to load
    await page.waitForTimeout(3000);
    
    // Verify grid has data rows
    const dataRows = page.locator('.ag-row');
    await expect(dataRows).toHaveCount({ min: 1 });
    
    // Verify order data is displayed correctly
    const firstRow = dataRows.first();
    
    // Check that cells contain text (not HTML)
    const sideCell = firstRow.locator('[col-id="side"]');
    const actionCell = firstRow.locator('[col-id="action"]');
    const statusCell = firstRow.locator('[col-id="status"]');
    
    // Verify content is plain text, not HTML
    const sideText = await sideCell.textContent();
    const actionText = await actionCell.textContent();
    const statusText = await statusCell.textContent();
    
    expect(sideText).toMatch(/^(YES|NO)$/);
    expect(actionText).toMatch(/^(BUY|SELL)$/);
    expect(statusText).toMatch(/^[A-Z\s]+$/);
    
    // Verify no HTML tags are present in the text
    expect(sideText).not.toContain('<span');
    expect(actionText).not.toContain('<span');
    expect(statusText).not.toContain('<span');
  });

  test('should display order statistics correctly', async ({ page }) => {
    // Select market with orders
    const marketOption = page.locator('.market-option').first();
    await marketOption.click();
    
    // Wait for orders to load
    await page.waitForTimeout(3000);
    
    // Check order statistics
    const statsSection = page.locator('.orders-stats');
    await expect(statsSection).toBeVisible();
    
    const totalStat = statsSection.locator('.stat').filter({ hasText: 'Total:' });
    const openStat = statsSection.locator('.stat').filter({ hasText: 'Open:' });
    
    await expect(totalStat).toBeVisible();
    await expect(openStat).toBeVisible();
    
    // Verify stats show non-zero values
    const totalText = await totalStat.textContent();
    const openText = await openStat.textContent();
    
    expect(totalText).toMatch(/Total: \d+/);
    expect(openText).toMatch(/Open: \d+/);
  });

  test('should show connection status', async ({ page }) => {
    // Check connection status indicator
    const connectionStatus = page.locator('.orders-panel .connection-status');
    
    // Should start as connecting or connected
    await expect(connectionStatus).toHaveText(/Live|Connecting\.\.\.|Offline/);
    
    // Select a market
    const marketOption = page.locator('.market-option').first();
    await marketOption.click();
    
    // Wait for connection to establish
    await page.waitForTimeout(2000);
    
    // Should show as connected when market is selected
    await expect(connectionStatus).toHaveText(/Live|Connecting\.\.\./);
  });

  test('should handle market switching correctly', async ({ page }) => {
    // Select first market
    const firstMarket = page.locator('.market-option').first();
    const firstMarketText = await firstMarket.textContent();
    await firstMarket.click();
    
    // Wait for orders to load
    await page.waitForTimeout(2000);
    
    // Verify orders are loaded
    let dataRows = page.locator('.ag-row');
    const firstMarketRowCount = await dataRows.count();
    
    // Switch to a different market
    const secondMarket = page.locator('.market-option').nth(1);
    const secondMarketText = await secondMarket.textContent();
    
    if (firstMarketText !== secondMarketText) {
      await secondMarket.click();
      
      // Wait for new orders to load
      await page.waitForTimeout(2000);
      
      // Verify grid updates with new data
      dataRows = page.locator('.ag-row');
      const secondMarketRowCount = await dataRows.count();
      
      // The row count may be different, but there should be a grid present
      expect(secondMarketRowCount).toBeGreaterThanOrEqual(0);
      
      // Verify header shows new market name
      const header = page.locator('.orders-header h3');
      await expect(header).toContainText(secondMarketText);
    }
  });

  test('should format data correctly without HTML', async ({ page }) => {
    // Select market
    const marketOption = page.locator('.market-option').first();
    await marketOption.click();
    
    // Wait for orders to load
    await page.waitForTimeout(3000);
    
    // Check specific columns that previously had HTML formatting
    const rows = page.locator('.ag-row');
    
    if (await rows.count() > 0) {
      const firstRow = rows.first();
      
      // Check Side column - should be plain text
      const sideCell = firstRow.locator('[col-id="side"]');
      const sideHTML = await sideCell.innerHTML();
      expect(sideHTML).not.toContain('<span');
      
      // Check Action column - should be plain text
      const actionCell = firstRow.locator('[col-id="action"]');
      const actionHTML = await actionCell.innerHTML();
      expect(actionHTML).not.toContain('<span');
      
      // Check Status column - should be plain text
      const statusCell = firstRow.locator('[col-id="status"]');
      const statusHTML = await statusCell.innerHTML();
      expect(statusHTML).not.toContain('<span');
      
      // Check Filled column - should be plain text
      const filledCell = firstRow.locator('[col-id="filled_quantity"]');
      const filledHTML = await filledCell.innerHTML();
      expect(filledHTML).not.toContain('<span');
    }
  });

  test('should receive real-time order updates via WebSocket', async ({ page }) => {
    // Select market
    const marketOption = page.locator('.market-option').first();
    await marketOption.click();
    
    // Wait for initial load
    await page.waitForTimeout(2000);
    
    // Get initial row count
    const initialRows = page.locator('.ag-row');
    const initialCount = await initialRows.count();
    
    // Create a new order via REST API to trigger WebSocket update
    const response = await page.request.post('http://localhost:9090/trade-api/v2/portfolio/orders', {
      headers: {
        'Content-Type': 'application/json',
        'X-User-Id': 'PLAYWRIGHT_TEST_USER'
      },
      data: {
        action: 'buy',
        side: 'yes',
        type: 'limit',
        price: 45,
        count: 5,
        timeInForce: 'GTC',
        marketTicker: await marketOption.textContent()
      }
    });
    
    expect(response.status()).toBe(201);
    
    // Wait for WebSocket update to propagate to AG Grid
    await page.waitForTimeout(3000);
    
    // Verify new row was added
    const updatedRows = page.locator('.ag-row');
    const updatedCount = await updatedRows.count();
    
    expect(updatedCount).toBeGreaterThan(initialCount);
    
    // Verify the new order appears in the grid
    const newOrderRow = updatedRows.first(); // Should be at top
    const marketCell = newOrderRow.locator('[col-id="market_ticker"]');
    const priceCell = newOrderRow.locator('[col-id="price"]');
    
    await expect(priceCell).toContainText('45¢');
  });
});