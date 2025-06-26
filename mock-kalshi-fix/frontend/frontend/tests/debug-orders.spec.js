import { test, expect } from '@playwright/test';

test.describe('Debug Orders Panel', () => {
  
  test('should debug orders panel rendering', async ({ page }) => {
    // Capture console logs
    const consoleLogs = [];
    page.on('console', msg => {
      consoleLogs.push(`[${msg.type()}] ${msg.text()}`);
    });

    // Capture errors
    const errors = [];
    page.on('pageerror', error => {
      errors.push(error.message);
    });

    // Navigate to the application
    await page.goto('http://localhost:5173');
    
    // Wait for the page to load
    await expect(page.locator('h1')).toContainText('Mock Kalshi Trading Platform');
    
    // Wait for markets to load
    await page.waitForSelector('.market-grid', { timeout: 10000 });
    
    // Check initial orders panel state
    console.log('=== Initial Orders Panel State ===');
    const ordersPanel = page.locator('.orders-panel');
    await expect(ordersPanel).toBeVisible();
    
    const initialText = await ordersPanel.textContent();
    console.log('Orders panel text:', initialText);
    
    // Select DUMMY_TEST market
    console.log('=== Selecting DUMMY_TEST Market ===');
    await page.click('text=DUMMY_TEST');
    
    // Wait a bit for things to load
    await page.waitForTimeout(3000);
    
    // Check orders panel state after selection
    console.log('=== After Market Selection ===');
    const afterText = await ordersPanel.textContent();
    console.log('Orders panel text after selection:', afterText);
    
    // Check if AG Grid elements exist
    const agGrid = page.locator('.ag-theme-alpine.orders-grid');
    const agGridExists = await agGrid.isVisible();
    console.log('AG Grid visible:', agGridExists);
    
    if (agGridExists) {
      const agRoot = page.locator('.ag-root');
      const agRootExists = await agRoot.isVisible();
      console.log('AG Root visible:', agRootExists);
      
      const agHeaders = page.locator('.ag-header');
      const agHeadersExists = await agHeaders.isVisible();
      console.log('AG Headers visible:', agHeadersExists);
      
      const agCells = page.locator('.ag-cell');
      const cellCount = await agCells.count();
      console.log('AG Cell count:', cellCount);
    }
    
    // Check connection status
    const connectionStatus = page.locator('.orders-panel .connection-status');
    const connectionText = await connectionStatus.textContent();
    console.log('Connection status:', connectionText);
    
    // Check statistics
    const stats = page.locator('.orders-stats');
    const statsExists = await stats.isVisible();
    console.log('Stats visible:', statsExists);
    
    if (statsExists) {
      const statsText = await stats.textContent();
      console.log('Stats text:', statsText);
    }
    
    // Check for network requests
    console.log('=== Network Activity ===');
    const responses = [];
    page.on('response', response => {
      if (response.url().includes('orders')) {
        responses.push(`${response.status()} ${response.url()}`);
      }
    });
    
    // Wait a bit more to see any network activity
    await page.waitForTimeout(2000);
    
    // Log all captured information
    console.log('=== Console Logs ===');
    consoleLogs.forEach(log => console.log(log));
    
    console.log('=== JavaScript Errors ===');
    errors.forEach(error => console.log(error));
    
    console.log('=== Network Responses ===');
    responses.forEach(response => console.log(response));
    
    // Make sure no critical errors occurred
    expect(errors.length).toBe(0);
  });
});