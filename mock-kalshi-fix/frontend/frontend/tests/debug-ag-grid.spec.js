import { test, expect } from '@playwright/test';

test.describe('Debug AG Grid Rendering', () => {
  
  test('check AG Grid rendering issue', async ({ page }) => {
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
    await page.goto('http://localhost:5176');
    
    // Wait for the page to load
    await expect(page.locator('h1')).toContainText('Mock Kalshi Trading Platform');
    
    // Select DUMMY_TEST market
    const marketRows = page.locator('.grid-row');
    const dummyTestRow = marketRows.filter({ hasText: 'DUMMY_TEST' });
    await dummyTestRow.click();
    
    // Wait for orders panel
    await page.waitForTimeout(3000);
    
    // Check what's in the orders panel
    const ordersPanel = page.locator('.orders-panel');
    const ordersPanelHTML = await ordersPanel.innerHTML();
    console.log('Orders Panel HTML length:', ordersPanelHTML.length);
    
    // Check if the AG Grid container exists
    const agGridContainer = page.locator('.ag-theme-alpine.orders-grid');
    const agGridExists = await agGridContainer.count();
    console.log('AG Grid container exists:', agGridExists > 0);
    
    if (agGridExists > 0) {
      const agGridHTML = await agGridContainer.innerHTML();
      console.log('AG Grid HTML length:', agGridHTML.length);
      console.log('AG Grid HTML preview:', agGridHTML.substring(0, 200));
      
      // Check for specific AG Grid elements
      const agRootWrapper = await page.locator('.ag-root-wrapper').count();
      console.log('AG Root Wrapper count:', agRootWrapper);
      
      const agRoot = await page.locator('.ag-root').count();
      console.log('AG Root count:', agRoot);
      
      // Check if AG Grid scripts loaded
      const hasAgGrid = await page.evaluate(() => {
        return typeof window.agGrid !== 'undefined';
      });
      console.log('AG Grid global object exists:', hasAgGrid);
    }
    
    // Check connection status
    const connectionStatus = page.locator('.orders-panel .connection-status');
    const statusText = await connectionStatus.textContent();
    console.log('Connection status:', statusText);
    
    // Check stats
    const stats = page.locator('.orders-stats');
    const statsText = await stats.textContent();
    console.log('Stats:', statsText);
    
    // Log all captured information
    console.log('\n=== Console Logs ===');
    consoleLogs.slice(-20).forEach(log => console.log(log));
    
    console.log('\n=== JavaScript Errors ===');
    errors.forEach(error => console.log(error));
    
    // Check React version
    const reactVersion = await page.evaluate(() => {
      if (window.React) {
        return window.React.version;
      }
      // Try to find React in the page
      const scripts = Array.from(document.scripts);
      for (const script of scripts) {
        if (script.src && script.src.includes('react')) {
          return 'React found in: ' + script.src;
        }
      }
      return 'React not found in window';
    });
    console.log('\nReact version:', reactVersion);
    
    // Take a screenshot
    await page.screenshot({ path: 'debug-ag-grid-screenshot.png', fullPage: true });
    console.log('\nScreenshot saved as debug-ag-grid-screenshot.png');
  });
});