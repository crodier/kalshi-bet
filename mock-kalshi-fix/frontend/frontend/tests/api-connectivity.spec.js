import { test, expect } from '@playwright/test';

test('API connectivity and AG Grid population', async ({ page }) => {
  // Enable console logging to see any errors
  page.on('console', msg => {
    if (msg.type() === 'error') {
      console.log('Browser error:', msg.text());
    }
  });

  // Navigate to the frontend
  await page.goto('http://localhost:5174');
  
  // Wait for initial load
  await page.waitForTimeout(3000);
  
  // Check if markets are loaded
  const marketsPanel = page.locator('.markets-panel');
  await expect(marketsPanel).toBeVisible();
  
  // Select DUMMY_TEST market
  const dummyTestMarket = page.locator('.market-option').filter({ hasText: 'DUMMY_TEST' });
  await expect(dummyTestMarket).toBeVisible();
  await dummyTestMarket.click();
  
  // Wait for orders to load
  await page.waitForTimeout(5000);
  
  // Check if orders panel is visible
  const ordersPanel = page.locator('.orders-panel');
  await expect(ordersPanel).toBeVisible();
  
  // Check if AG Grid is present
  const agGrid = page.locator('.ag-theme-alpine.orders-grid');
  await expect(agGrid).toBeVisible();
  
  // Check if connection status shows connected
  const connectionStatus = page.locator('.orders-panel .connection-status');
  await expect(connectionStatus).toBeVisible();
  
  // Check if we have data rows (should have orders from the API)
  const dataRows = page.locator('.ag-row');
  const rowCount = await dataRows.count();
  
  console.log(`Found ${rowCount} rows in AG Grid`);
  
  if (rowCount > 0) {
    console.log('✅ AG Grid successfully populated with order data');
    
    // Verify first row has proper data structure
    const firstRow = dataRows.first();
    const orderIdCell = firstRow.locator('[col-id="order_id"]');
    const marketCell = firstRow.locator('[col-id="market_ticker"]');
    const sideCell = firstRow.locator('[col-id="side"]');
    
    await expect(orderIdCell).toHaveText(/ORD-\d+/);
    await expect(marketCell).toHaveText('DUMMY_TEST');
    await expect(sideCell).toHaveText(/YES|NO/);
    
    // Verify no HTML tags in the cells
    const sideHTML = await sideCell.innerHTML();
    expect(sideHTML).not.toContain('<span');
    
    console.log('✅ Order data properly formatted without HTML tags');
  } else {
    console.log('❌ No orders found in AG Grid - API may not be working');
    
    // Check for any error messages in console or page
    const errorText = await page.textContent('body');
    if (errorText.includes('Failed to fetch')) {
      console.log('❌ Fetch error detected in page');
    }
  }
  
  // Check order statistics
  const statsSection = page.locator('.orders-stats');
  if (await statsSection.isVisible()) {
    const totalStat = await statsSection.locator('.stat').filter({ hasText: 'Total:' }).textContent();
    console.log('Order stats:', totalStat);
  }
});