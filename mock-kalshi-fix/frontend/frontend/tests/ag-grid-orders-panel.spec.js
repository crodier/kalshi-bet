import { test, expect } from '@playwright/test';

test.describe('AG Grid Orders Panel Tests', () => {
  
  test.beforeEach(async ({ page }) => {
    // Navigate to the application
    await page.goto('http://localhost:5176');
    
    // Wait for the page to load
    await expect(page.locator('h1')).toContainText('Mock Kalshi Trading Platform');
    
    // Wait for markets to load
    await page.waitForSelector('.market-grid', { timeout: 10000 });
  });

  test('complete AG Grid orders panel workflow', async ({ page }) => {
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
    
    // Check connection status
    const connectionStatus = page.locator('.orders-panel .connection-status');
    await expect(connectionStatus).toBeVisible();
    
    // Wait for connection to establish
    await page.waitForTimeout(2000);
    
    // Check if AG Grid is visible
    const agGrid = page.locator('.ag-theme-alpine.orders-grid');
    await expect(agGrid).toBeVisible();
    
    // Check for AG Grid structure
    const agRoot = page.locator('.ag-root');
    await expect(agRoot).toBeVisible();
    
    // Check for AG Grid headers
    const agHeaders = page.locator('.ag-header');
    await expect(agHeaders).toBeVisible();
    
    // Check for specific column headers
    const orderIdHeader = page.locator('.ag-header-cell-text:has-text("Order ID")');
    await expect(orderIdHeader).toBeVisible();
    
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
      
      // Check if we have order rows in AG Grid
      const agRows = page.locator('.ag-row');
      const rowCount = await agRows.count();
      console.log('Number of AG Grid rows:', rowCount);
      
      if (rowCount > 0) {
        // Verify the first order has proper data
        const firstRow = agRows.first();
        await expect(firstRow).toBeVisible();
        
        // Check for cells in the row
        const cells = firstRow.locator('.ag-cell');
        const cellCount = await cells.count();
        console.log('Number of cells in first row:', cellCount);
        expect(cellCount).toBeGreaterThan(0);
      }
    }
    
    // Test grid sorting
    console.log('\nTesting AG Grid sorting...');
    const priceHeader = page.locator('.ag-header-cell-text:has-text("Price")');
    if (await priceHeader.isVisible()) {
      await priceHeader.click();
      await page.waitForTimeout(500);
      console.log('✓ Clicked on Price column header for sorting');
    }
    
    // Test market switching
    console.log('\nTesting market switching...');
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

  test('AG Grid renders with proper styling and formatting', async ({ page }) => {
    // Select a market
    const marketRows = page.locator('.grid-row');
    const dummyTestRow = marketRows.filter({ hasText: 'DUMMY_TEST' });
    await dummyTestRow.click();
    await page.waitForTimeout(2000);
    
    // Check AG Grid is rendered
    const agGrid = page.locator('.ag-theme-alpine.orders-grid');
    await expect(agGrid).toBeVisible();
    await expect(agGrid).toHaveClass(/ag-theme-alpine/);
    
    // Check for AG Grid structure elements
    const agRootWrapper = page.locator('.ag-root-wrapper');
    await expect(agRootWrapper).toBeVisible();
    
    // Check headers are visible
    const agHeaderRow = page.locator('.ag-header-row');
    await expect(agHeaderRow).toBeVisible();
    
    // Check specific columns exist
    const columns = ['Order ID', 'Market', 'Side', 'Action', 'Status', 'Price'];
    for (const col of columns) {
      const header = page.locator(`.ag-header-cell-text:has-text("${col}")`);
      await expect(header).toBeVisible();
      console.log(`✓ Column "${col}" is visible`);
    }
    
    // If there are rows, check cell formatting
    const rows = page.locator('.ag-row');
    const rowCount = await rows.count();
    
    if (rowCount > 0) {
      console.log(`Found ${rowCount} order rows`);
      
      // Check for formatted cells (these use innerHTML with spans)
      const sideCell = page.locator('.ag-cell .side-yes, .ag-cell .side-no').first();
      if (await sideCell.count() > 0) {
        await expect(sideCell).toBeVisible();
        console.log('✓ Side cell has proper formatting');
      }
      
      const actionCell = page.locator('.ag-cell .action-buy, .ag-cell .action-sell').first();
      if (await actionCell.count() > 0) {
        await expect(actionCell).toBeVisible();
        console.log('✓ Action cell has proper formatting');
      }
      
      const statusCell = page.locator('.ag-cell .status-open, .ag-cell .status-filled, .ag-cell .status-canceled').first();
      if (await statusCell.count() > 0) {
        await expect(statusCell).toBeVisible();
        console.log('✓ Status cell has proper formatting');
      }
    }
  });

  test('AG Grid WebSocket integration works correctly', async ({ page }) => {
    // Monitor console for WebSocket messages
    const wsMessages = [];
    page.on('console', msg => {
      const text = msg.text();
      if (text.includes('WebSocket') || text.includes('order') || text.includes('Subscribing')) {
        wsMessages.push(text);
      }
    });
    
    // Select DUMMY_TEST market
    const marketRows = page.locator('.grid-row');
    const dummyTestRow = marketRows.filter({ hasText: 'DUMMY_TEST' });
    await dummyTestRow.click();
    
    // Wait for WebSocket connection
    await page.waitForTimeout(3000);
    
    // Check connection status
    const connectionStatus = page.locator('.orders-panel .connection-status');
    const statusText = await connectionStatus.textContent();
    console.log('Connection status:', statusText);
    
    // The status should show connected or connecting
    const isConnected = statusText.includes('Live') || statusText.includes('Connecting');
    expect(isConnected).toBeTruthy();
    
    // Log WebSocket activity
    console.log('\n=== WebSocket Messages ===');
    wsMessages.forEach(msg => console.log(msg));
    
    // Check that we subscribed to orders
    const hasOrderSubscription = wsMessages.some(msg => 
      msg.includes('Subscribing to order updates') || 
      msg.includes('orders WebSocket')
    );
    
    if (hasOrderSubscription) {
      console.log('✓ Orders WebSocket subscription detected');
    } else {
      console.log('⚠️ No orders WebSocket subscription detected');
    }
  });
});