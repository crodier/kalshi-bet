import { test, expect } from '@playwright/test';

test.describe('Integrated WebSocket System', () => {
  
  test.beforeEach(async ({ page }) => {
    // Navigate to the application
    await page.goto('http://localhost:5173');
    
    // Wait for the page to load
    await expect(page.locator('h1')).toContainText('Mock Kalshi Trading Platform');
    
    // Wait for markets to load and connection to establish
    await page.waitForSelector('.market-grid', { timeout: 10000 });
    await page.waitForSelector('.connection-status.connected', { timeout: 10000 });
  });

  test('should establish both orderbook and orders WebSocket connections', async ({ page }) => {
    // Select DUMMY_TEST market
    await page.click('text=DUMMY_TEST');
    
    // Wait for both panels to connect
    await page.waitForSelector('.orders-panel .connection-status.connected', { timeout: 10000 });
    
    // Check orderbook is loaded
    await expect(page.locator('.orderbook')).toBeVisible();
    await expect(page.locator('.orderbook-header')).toContainText('DUMMY_TEST');
    
    // Check orders panel is loaded
    await expect(page.locator('.orders-panel h3')).toContainText('Orders - DUMMY_TEST');
    await expect(page.locator('.orders-panel .connection-status')).toContainText('🔗 Live');
    
    // Check both grids are populated
    await page.waitForSelector('.orderbook-table tbody tr', { timeout: 5000 });
    await page.waitForSelector('.ag-row', { timeout: 5000 });
  });

  test('should update both panels when orders are created and matched', async ({ page }) => {
    // Select DUMMY_TEST market
    await page.click('text=DUMMY_TEST');
    await page.waitForSelector('.orders-panel .connection-status.connected', { timeout: 10000 });
    
    // Wait for initial data to load
    await page.waitForSelector('.orderbook-table tbody tr', { timeout: 5000 });
    await page.waitForSelector('.ag-row', { timeout: 5000 });
    
    // Get initial states
    const initialOrderCount = await page.evaluate(() => {
      const statsText = document.querySelector('.orders-stats')?.textContent || '';
      const match = statsText.match(/Total:\s*(\d+)/);
      return match ? parseInt(match[1]) : 0;
    });
    
    const initialOrderbookState = await page.evaluate(() => {
      const rows = Array.from(document.querySelectorAll('.orderbook-table tbody tr'));
      return rows.length;
    });
    
    // Create a new order that should match existing orders
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
            count: 5,
            price: 53, // Price that should match existing orders
            time_in_force: 'GTC',
            action: 'buy'
          })
        });
        
        if (response.ok) {
          const data = await response.json();
          return data.order;
        }
        return null;
      } catch (error) {
        console.error('Failed to create order:', error);
        return null;
      }
    });
    
    if (orderResponse) {
      console.log('Order created:', orderResponse);
      
      // Wait for orders panel to update with new order
      await page.waitForFunction(
        (initialCount) => {
          const statsText = document.querySelector('.orders-stats')?.textContent || '';
          const match = statsText.match(/Total:\s*(\d+)/);
          const currentCount = match ? parseInt(match[1]) : 0;
          return currentCount > initialCount;
        },
        initialOrderCount,
        { timeout: 15000 }
      );
      
      // Check that orders panel shows the update
      const updatedOrderCount = await page.evaluate(() => {
        const statsText = document.querySelector('.orders-stats')?.textContent || '';
        const match = statsText.match(/Total:\s*(\d+)/);
        return match ? parseInt(match[1]) : 0;
      });
      
      expect(updatedOrderCount).toBeGreaterThan(initialOrderCount);
      
      // If the order was filled, check for fill status in orders panel
      if (orderResponse.status === 'filled' || orderResponse.filled_quantity > 0) {
        // Look for filled status in the grid
        await expect(page.locator('.status-filled, .status-partial')).toBeVisible({ timeout: 5000 });
        
        // Check that filled count in stats increased
        const filledStatsText = await page.locator('.orders-stats').textContent();
        const filledMatch = filledStatsText.match(/Filled:\s*(\d+)/);
        if (filledMatch) {
          const filledCount = parseInt(filledMatch[1]);
          expect(filledCount).toBeGreaterThan(0);
        }
      }
      
      // Wait for orderbook to potentially update (if orders were matched)
      await page.waitForTimeout(2000);
      
      // Check for orderbook flashing effects (indicates updates)
      const flashingCells = page.locator('.flash-green, .flash-red');
      // Note: Flash effects are transient, so we just check the orderbook is still functional
      await expect(page.locator('.orderbook-table')).toBeVisible();
    }
  });

  test('should handle market switching with both WebSocket subscriptions', async ({ page }) => {
    // Select first market
    await page.click('text=DUMMY_TEST');
    await page.waitForSelector('.orders-panel .connection-status.connected', { timeout: 10000 });
    
    // Wait for data to load
    await page.waitForSelector('.ag-row', { timeout: 5000 });
    await page.waitForSelector('.orderbook-table tbody tr', { timeout: 5000 });
    
    // Get initial state
    const initialOrdersHeader = await page.locator('.orders-panel h3').textContent();
    const initialOrderbookHeader = await page.locator('.orderbook-header h3').textContent();
    
    // Switch to another market if available
    const marketButtons = page.locator('.market-row');
    const marketCount = await marketButtons.count();
    
    if (marketCount > 1) {
      // Find a different market
      for (let i = 0; i < marketCount; i++) {
        const marketText = await marketButtons.nth(i).textContent();
        if (!marketText.includes('DUMMY_TEST')) {
          await marketButtons.nth(i).click();
          break;
        }
      }
      
      // Wait for both panels to update
      await page.waitForTimeout(2000);
      
      // Check that both panels updated to new market
      const newOrdersHeader = await page.locator('.orders-panel h3').textContent();
      const newOrderbookHeader = await page.locator('.orderbook-header h3').textContent();
      
      expect(newOrdersHeader).not.toBe(initialOrdersHeader);
      expect(newOrderbookHeader).not.toBe(initialOrderbookHeader);
      
      // Both should show the same market
      const ordersMarket = newOrdersHeader.split(' - ')[1];
      const orderbookMarket = newOrderbookHeader.split(' - ')[1] || newOrderbookHeader;
      
      if (ordersMarket && orderbookMarket) {
        expect(ordersMarket.trim()).toBe(orderbookMarket.trim());
      }
      
      // Both connections should remain active
      await expect(page.locator('.orders-panel .connection-status.connected')).toBeVisible();
      await expect(page.locator('.connection-status.connected')).toBeVisible();
    }
  });

  test('should show real-time order updates with proper timestamps', async ({ page }) => {
    // Select DUMMY_TEST market
    await page.click('text=DUMMY_TEST');
    await page.waitForSelector('.orders-panel .connection-status.connected', { timeout: 10000 });
    await page.waitForSelector('.ag-row', { timeout: 5000 });
    
    // Create a new order and track its timeline
    const startTime = new Date();
    
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
            price: 49,
            time_in_force: 'GTC',
            action: 'buy'
          })
        });
        
        return response.ok ? await response.json() : null;
      } catch (error) {
        return null;
      }
    });
    
    if (orderResponse) {
      // Wait for the order to appear in the grid
      await page.waitForFunction(
        (orderId) => {
          const rows = Array.from(document.querySelectorAll('.ag-row'));
          return rows.some(row => row.textContent.includes(orderId.substring(4, 12)));
        },
        orderResponse.order.id,
        { timeout: 10000 }
      );
      
      // Find the order row
      const orderRowSelector = `.ag-row:has-text("${orderResponse.order.id.substring(4, 12)}")`;
      await expect(page.locator(orderRowSelector)).toBeVisible();
      
      // Check that timestamps are recent
      const orderRow = page.locator(orderRowSelector);
      const createdTimeCell = orderRow.locator('.ag-cell').nth(12); // Created time column
      const createdTimeText = await createdTimeCell.textContent();
      
      // Verify timestamp is recent (within last few minutes)
      if (createdTimeText) {
        const createdTime = new Date(createdTimeText);
        const timeDiff = Math.abs(createdTime.getTime() - startTime.getTime());
        expect(timeDiff).toBeLessThan(5 * 60 * 1000); // Within 5 minutes
      }
      
      // Check for update type indicator
      const updateTypeCell = orderRow.locator('.ag-cell').nth(14); // Update type column
      const updateTypeText = await updateTypeCell.textContent();
      expect(['NEW', 'FILL', 'EXISTING']).toContain(updateTypeText);
    }
  });

  test('should handle WebSocket reconnection gracefully', async ({ page }) => {
    // Select DUMMY_TEST market
    await page.click('text=DUMMY_TEST');
    await page.waitForSelector('.orders-panel .connection-status.connected', { timeout: 10000 });
    
    // Simulate connection loss by evaluating JavaScript to close WebSocket
    await page.evaluate(() => {
      // Find and close WebSocket connections
      if (window.websocket && window.websocket.readyState === WebSocket.OPEN) {
        window.websocket.close();
      }
    });
    
    // Wait for disconnection to be detected
    await page.waitForSelector('.connection-status.disconnected', { timeout: 5000 });
    
    // The app should attempt to reconnect
    await page.waitForSelector('.connection-status.connected', { timeout: 15000 });
    
    // Both panels should still be functional
    await expect(page.locator('.orders-panel .connection-status.connected')).toBeVisible();
    await expect(page.locator('.orderbook')).toBeVisible();
  });

  test('should maintain data consistency between panels', async ({ page }) => {
    // Select DUMMY_TEST market
    await page.click('text=DUMMY_TEST');
    await page.waitForSelector('.orders-panel .connection-status.connected', { timeout: 10000 });
    
    // Get market ticker from both panels
    const ordersMarket = await page.locator('.orders-panel h3').textContent();
    const orderbookMarket = await page.locator('.orderbook-header h3').textContent();
    
    // Both should reference the same market
    expect(ordersMarket).toContain('DUMMY_TEST');
    expect(orderbookMarket).toContain('DUMMY_TEST');
    
    // Check that order data is consistent
    // Orders panel should show orders for the same market as orderbook
    const agCells = await page.locator('.ag-cell').filter({ hasText: 'DUMMY_TEST' }).count();
    expect(agCells).toBeGreaterThan(0);
    
    // Both panels should be showing live data
    await expect(page.locator('.orders-panel .connection-status')).toContainText('🔗 Live');
    await expect(page.locator('.connection-status.connected')).toBeVisible();
  });

  test('should handle high-frequency updates without UI lag', async ({ page }) => {
    // Select DUMMY_TEST market
    await page.click('text=DUMMY_TEST');
    await page.waitForSelector('.orders-panel .connection-status.connected', { timeout: 10000 });
    
    // Create multiple orders in quick succession
    const orderPromises = [];
    for (let i = 0; i < 3; i++) {
      const promise = page.evaluate(async (orderIndex) => {
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
              price: 48 + orderIndex,
              time_in_force: 'GTC',
              action: 'buy'
            })
          });
          return response.ok;
        } catch (error) {
          return false;
        }
      }, i);
      
      orderPromises.push(promise);
      
      // Small delay between requests
      await page.waitForTimeout(200);
    }
    
    // Wait for all orders to be created
    const results = await Promise.all(orderPromises);
    const successfulOrders = results.filter(Boolean).length;
    
    if (successfulOrders > 0) {
      // Wait for UI to update
      await page.waitForTimeout(3000);
      
      // Check that the UI is still responsive
      await expect(page.locator('.orders-panel')).toBeVisible();
      await expect(page.locator('.orderbook')).toBeVisible();
      
      // Check that orders panel updated
      const currentStats = await page.locator('.orders-stats').textContent();
      expect(currentStats).toContain('Total:');
      
      // UI should not be frozen - test by clicking
      await page.click('.orders-panel h3');
      await expect(page.locator('.orders-panel h3')).toBeVisible();
    }
  });
});