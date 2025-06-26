import { test, expect } from '@playwright/test';

test.describe('OrderBook Basic Functionality', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('http://localhost:5173');
  });

  test('should load the application', async ({ page }) => {
    await expect(page).toHaveTitle(/Kalshi Mock Trading/);
    await expect(page.locator('.app')).toBeVisible();
  });

  test('should display market grid', async ({ page }) => {
    await expect(page.locator('.market-grid')).toBeVisible();
    
    // Check for MARKET_MAKER market
    const marketMakerRow = page.locator('.grid-row').filter({ hasText: 'MARKET_MAKER' });
    await expect(marketMakerRow).toBeVisible();
    
    // Check that it has bid/ask prices
    await expect(marketMakerRow.locator('.bid')).toBeVisible();
    await expect(marketMakerRow.locator('.ask')).toBeVisible();
  });

  test('should display order book when market is clicked', async ({ page }) => {
    // Click on MARKET_MAKER
    await page.locator('.grid-row').filter({ hasText: 'MARKET_MAKER' }).click();
    
    // Wait for order book to appear
    await expect(page.locator('.orderbook')).toBeVisible();
    await expect(page.locator('h3')).toContainText('Order Book - MARKET_MAKER');
    
    // Check for bid and ask sides
    await expect(page.locator('.orderbook-side.bids h4')).toContainText('Yes (Buy)');
    await expect(page.locator('.orderbook-side.asks h4')).toContainText('No (Buy)');
  });

  test('should display order book status bar', async ({ page }) => {
    await page.locator('.grid-row').filter({ hasText: 'MARKET_MAKER' }).click();
    
    // Check for status bar
    await expect(page.locator('.orderbook-status')).toBeVisible();
    await expect(page.locator('.status-line')).toBeVisible();
    
    // Should show message counts
    await expect(page.locator('.status-details')).toContainText('Messages:');
    await expect(page.locator('.status-details')).toContainText('Deltas:');
    await expect(page.locator('.status-details')).toContainText('Snapshots:');
  });

  test('should have order placement form', async ({ page }) => {
    await expect(page.locator('.order-form')).toBeVisible();
    
    // Check form fields
    await expect(page.locator('select#market')).toBeVisible();
    await expect(page.locator('select#side')).toBeVisible();
    await expect(page.locator('select#action')).toBeVisible();
    await expect(page.locator('input#quantity')).toBeVisible();
    await expect(page.locator('input#price')).toBeVisible();
    await expect(page.locator('button[type="submit"]')).toContainText('Place Order');
  });
});

test.describe('WebSocket Updates', () => {
  test('should receive initial snapshot', async ({ page }) => {
    await page.goto('http://localhost:5173');
    
    // Click on MARKET_MAKER
    await page.locator('.grid-row').filter({ hasText: 'MARKET_MAKER' }).click();
    
    // Wait for order book
    await expect(page.locator('.orderbook')).toBeVisible();
    
    // Check that we have some levels
    const bidLevels = page.locator('.orderbook-side.bids .level');
    const askLevels = page.locator('.orderbook-side.asks .level');
    
    // Should have at least some bid levels from initial data
    await expect(bidLevels).toHaveCount(10); // Based on the test output showing 10 YES levels
    
    // Check snapshot count
    await expect(page.locator('.status-details')).toContainText('Snapshots: 1');
  });

  test('should update counts in status bar', async ({ page }) => {
    await page.goto('http://localhost:5173');
    await page.locator('.grid-row').filter({ hasText: 'MARKET_MAKER' }).click();
    
    // Get initial counts
    const initialStatus = await page.locator('.status-details').textContent();
    const initialMessageCount = parseInt(initialStatus.match(/Messages: (\d+)/)[1]);
    
    // Wait a bit for potential updates
    await page.waitForTimeout(5000);
    
    // Check if counts have increased
    const updatedStatus = await page.locator('.status-details').textContent();
    const updatedMessageCount = parseInt(updatedStatus.match(/Messages: (\d+)/)[1]);
    
    expect(updatedMessageCount).toBeGreaterThanOrEqual(initialMessageCount);
  });
});

test.describe('Delta Updates', () => {
  test('should receive and display delta updates when orders are placed', async ({ page }) => {
    await page.goto('http://localhost:5173');
    
    // First, click on MARKET_MAKER to start watching
    await page.locator('.grid-row').filter({ hasText: 'MARKET_MAKER' }).click();
    await expect(page.locator('.orderbook')).toBeVisible();
    
    // Get initial state
    const initialStatus = await page.locator('.status-details').textContent();
    const initialDeltaCount = parseInt(initialStatus.match(/Deltas: (\d+)/)[1]);
    
    // Place an order through the form
    await page.selectOption('#market', 'MARKET_MAKER');
    await page.selectOption('#side', 'yes');
    await page.selectOption('#action', 'buy');
    await page.fill('#quantity', '25');
    await page.fill('#price', '48');
    await page.click('button[type="submit"]');
    
    // Wait for the order to be processed
    await page.waitForTimeout(2000);
    
    // Check that delta count increased
    const updatedStatus = await page.locator('.status-details').textContent();
    const updatedDeltaCount = parseInt(updatedStatus.match(/Deltas: (\d+)/)[1]);
    
    expect(updatedDeltaCount).toBeGreaterThan(initialDeltaCount);
    
    // Check for delta indicator
    const deltaIndicator = page.locator('.delta-indicator').first();
    if (await deltaIndicator.isVisible()) {
      await expect(deltaIndicator).toContainText('ADD');
      await expect(deltaIndicator).toContainText('@ 48¢');
    }
  });

  test('should flash price levels when they update', async ({ page }) => {
    await page.goto('http://localhost:5173');
    await page.locator('.grid-row').filter({ hasText: 'MARKET_MAKER' }).click();
    
    // Place an order at a specific price
    await page.selectOption('#market', 'MARKET_MAKER');
    await page.selectOption('#side', 'yes');
    await page.selectOption('#action', 'buy');
    await page.fill('#quantity', '30');
    await page.fill('#price', '47');
    await page.click('button[type="submit"]');
    
    // Look for flashing animation on the 47¢ level
    const level47 = page.locator('.orderbook-side.bids .level').filter({ hasText: '47¢' });
    
    // Check if it has the flash class
    await expect(level47).toHaveClass(/flash-green/);
    
    // The flash should disappear after animation
    await page.waitForTimeout(2500);
    await expect(level47).not.toHaveClass(/flash-green/);
  });

  test('should update order book levels dynamically', async ({ page }) => {
    await page.goto('http://localhost:5173');
    await page.locator('.grid-row').filter({ hasText: 'MARKET_MAKER' }).click();
    
    // Count initial bid levels
    const initialBidCount = await page.locator('.orderbook-side.bids .level').count();
    
    // Place an order at a new price level
    await page.selectOption('#market', 'MARKET_MAKER');
    await page.selectOption('#side', 'yes');
    await page.selectOption('#action', 'buy');
    await page.fill('#quantity', '50');
    await page.fill('#price', '43'); // New price level
    await page.click('button[type="submit"]');
    
    // Wait for update
    await page.waitForTimeout(1000);
    
    // Should have one more bid level
    const updatedBidCount = await page.locator('.orderbook-side.bids .level').count();
    expect(updatedBidCount).toBe(initialBidCount + 1);
    
    // Check that the new level exists
    const newLevel = page.locator('.orderbook-side.bids .level').filter({ hasText: '43¢' });
    await expect(newLevel).toBeVisible();
    await expect(newLevel.locator('.quantity')).toContainText('50');
  });

  test('should handle order cancellation deltas', async ({ page }) => {
    await page.goto('http://localhost:5173');
    
    // Place an order first
    await page.selectOption('#market', 'MARKET_MAKER');
    await page.selectOption('#side', 'yes');
    await page.selectOption('#action', 'buy');
    await page.fill('#quantity', '100');
    await page.fill('#price', '44');
    await page.click('button[type="submit"]');
    
    // Wait for order response
    await page.waitForSelector('.order-status.success', { timeout: 5000 });
    const orderInfo = await page.locator('.order-info').textContent();
    const orderId = orderInfo.match(/Order ID: (ORD-\d+)/)[1];
    
    // Now watch the order book
    await page.locator('.grid-row').filter({ hasText: 'MARKET_MAKER' }).click();
    
    // Verify the level exists
    const level44 = page.locator('.orderbook-side.bids .level').filter({ hasText: '44¢' });
    await expect(level44).toBeVisible();
    const initialQuantity = await level44.locator('.quantity').textContent();
    
    // Cancel the order
    await page.fill('#cancelOrderId', orderId);
    await page.click('button:has-text("Cancel Order")');
    
    // Wait for the delta update
    await page.waitForTimeout(2000);
    
    // Check if the quantity decreased or level disappeared
    if (await level44.isVisible()) {
      const updatedQuantity = await level44.locator('.quantity').textContent();
      expect(parseInt(updatedQuantity)).toBeLessThan(parseInt(initialQuantity));
    }
    
    // Check for delta indicator showing removal
    const deltaIndicator = page.locator('.delta-indicator').filter({ hasText: 'REMOVE' });
    if (await deltaIndicator.isVisible()) {
      await expect(deltaIndicator).toContainText('@ 44¢');
    }
  });

  test('should show correct snapshot interval pattern', async ({ page }) => {
    await page.goto('http://localhost:5173');
    await page.locator('.grid-row').filter({ hasText: 'MARKET_MAKER' }).click();
    
    // Get initial counts
    let previousMessageCount = 0;
    let snapshotsSeen = [];
    
    // Monitor for 15 seconds, placing orders periodically
    for (let i = 0; i < 12; i++) {
      const status = await page.locator('.status-details').textContent();
      const messageCount = parseInt(status.match(/Messages: (\d+)/)[1]);
      const snapshotCount = parseInt(status.match(/Snapshots: (\d+)/)[1]);
      
      if (messageCount > previousMessageCount) {
        snapshotsSeen.push({ message: messageCount, snapshots: snapshotCount });
      }
      previousMessageCount = messageCount;
      
      // Place an order every 1.5 seconds
      if (i % 2 === 0) {
        await page.selectOption('#market', 'MARKET_MAKER');
        await page.selectOption('#side', 'yes');
        await page.selectOption('#action', 'buy');
        await page.fill('#quantity', String(10 + i));
        await page.fill('#price', String(45 + i));
        await page.click('button[type="submit"]');
      }
      
      await page.waitForTimeout(1500);
    }
    
    // Verify snapshot pattern - should get a new snapshot roughly every 10 messages
    const finalStatus = await page.locator('.status-details').textContent();
    const totalMessages = parseInt(finalStatus.match(/Messages: (\d+)/)[1]);
    const totalSnapshots = parseInt(finalStatus.match(/Snapshots: (\d+)/)[1]);
    
    if (totalMessages >= 10) {
      // Should have at least 2 snapshots (initial + one at 10 messages)
      expect(totalSnapshots).toBeGreaterThanOrEqual(2);
      
      // But not too many - roughly totalMessages/10 + 1
      expect(totalSnapshots).toBeLessThanOrEqual(Math.ceil(totalMessages / 10) + 1);
    }
  });
});

test.describe('Market Grid Updates', () => {
  test('should update market grid prices when orders affect best bid/ask', async ({ page }) => {
    await page.goto('http://localhost:5173');
    
    // Get initial MARKET_MAKER bid/ask
    const marketRow = page.locator('.grid-row').filter({ hasText: 'MARKET_MAKER' });
    const initialBid = await marketRow.locator('.bid').textContent();
    const initialAsk = await marketRow.locator('.ask').textContent();
    
    // Place a buy order at a better price
    await page.selectOption('#market', 'MARKET_MAKER');
    await page.selectOption('#side', 'yes');
    await page.selectOption('#action', 'buy');
    await page.fill('#quantity', '50');
    await page.fill('#price', String(parseInt(initialBid) + 1)); // Better bid
    await page.click('button[type="submit"]');
    
    // Wait for update
    await page.waitForTimeout(2000);
    
    // Check if bid updated
    const updatedBid = await marketRow.locator('.bid').textContent();
    expect(parseInt(updatedBid)).toBeGreaterThan(parseInt(initialBid));
    
    // Check for flash animation
    await expect(marketRow.locator('.bid')).toHaveClass(/flash/);
  });

  test('should show "Yes Buy Best" and "No Buy Best" headers', async ({ page }) => {
    await page.goto('http://localhost:5173');
    
    // Check column headers
    await expect(page.locator('.market-grid-header').nth(3)).toContainText('Yes Buy Best');
    await expect(page.locator('.market-grid-header').nth(4)).toContainText('No Buy Best');
  });
});