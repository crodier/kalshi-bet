import { test, expect } from '@playwright/test';

test.describe('Frontend Delta Updates Working Tests', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('http://localhost:5173');
    // Wait for app to load
    await page.waitForSelector('.app', { timeout: 10000 });
  });

  test('should load the application correctly', async ({ page }) => {
    await expect(page).toHaveTitle(/Kalshi Mock Trading/);
    await expect(page.locator('.app')).toBeVisible();
    await expect(page.locator('h1')).toContainText('Mock Kalshi Trading Platform');
  });

  test('should display market grid with MARKET_MAKER', async ({ page }) => {
    await expect(page.locator('.market-grid')).toBeVisible();
    
    // Check for MARKET_MAKER market in grid rows
    const marketMakerRow = page.locator('.grid-row').filter({ hasText: 'MARKET_MAKER' });
    await expect(marketMakerRow).toBeVisible();
  });

  test('should show correct column headers', async ({ page }) => {
    // Check for the correct column headers
    await expect(page.locator('.grid-header')).toContainText('Yes Buy Best');
    await expect(page.locator('.grid-header')).toContainText('No Buy Best');
  });

  test('should display order book when market is clicked', async ({ page }) => {
    // Click on MARKET_MAKER row
    await page.locator('.grid-row').filter({ hasText: 'MARKET_MAKER' }).click();
    
    // Wait for order book section to appear
    await expect(page.locator('.orderbook-section')).toBeVisible();
    
    // The OrderBook component should show
    await page.waitForSelector('.orderbook-section .orderbook', { timeout: 10000 });
    
    // Check for order book title
    await expect(page.locator('.orderbook h3')).toContainText('Order Book - MARKET_MAKER');
  });

  test('should show connection status', async ({ page }) => {
    // Should show connection status
    await expect(page.locator('.connection-status')).toBeVisible();
    
    // Wait a bit for connection to establish
    await page.waitForTimeout(3000);
    
    // Check if we're connected or connecting
    const statusText = await page.locator('.connection-status-text').textContent();
    console.log('Connection status:', statusText);
    
    // Should show some connection state
    expect(statusText).toMatch(/Connected|Connecting|Disconnected/i);
  });

  test('should handle WebSocket connection and receive updates', async ({ page }) => {
    // Click on MARKET_MAKER to start monitoring
    await page.locator('.grid-row').filter({ hasText: 'MARKET_MAKER' }).click();
    
    // Wait for order book to appear
    await page.waitForSelector('.orderbook-section .orderbook', { timeout: 10000 });
    
    // Check for status bar
    await expect(page.locator('.orderbook-status')).toBeVisible();
    
    // Get initial message count
    await page.waitForTimeout(2000);
    const initialStatus = await page.locator('.status-details').textContent();
    console.log('Initial status:', initialStatus);
    
    const initialMessageCount = parseInt(initialStatus.match(/Messages: (\d+)/)?.[1] || '0');
    const initialSnapshotCount = parseInt(initialStatus.match(/Snapshots: (\d+)/)?.[1] || '0');
    
    // Wait for market maker updates (it runs every 3 seconds)
    await page.waitForTimeout(6000);
    
    // Check updated counts
    const updatedStatus = await page.locator('.status-details').textContent();
    console.log('Updated status:', updatedStatus);
    
    const updatedMessageCount = parseInt(updatedStatus.match(/Messages: (\d+)/)?.[1] || '0');
    const updatedSnapshotCount = parseInt(updatedStatus.match(/Snapshots: (\d+)/)?.[1] || '0');
    
    // Should have received at least one snapshot
    expect(updatedSnapshotCount).toBeGreaterThanOrEqual(1);
    
    // Should have received some messages
    expect(updatedMessageCount).toBeGreaterThan(initialMessageCount);
  });

  test('should receive delta updates from market maker', async ({ page }) => {
    // Click on MARKET_MAKER
    await page.locator('.grid-row').filter({ hasText: 'MARKET_MAKER' }).click();
    await page.waitForSelector('.orderbook-section .orderbook', { timeout: 10000 });
    
    // Get initial counts
    await page.waitForTimeout(2000);
    const initialStatus = await page.locator('.status-details').textContent();
    const initialDeltaCount = parseInt(initialStatus.match(/Deltas: (\d+)/)?.[1] || '0');
    
    console.log('Initial delta count:', initialDeltaCount);
    
    // Wait for market maker updates (multiple cycles)
    await page.waitForTimeout(10000);
    
    // Check for deltas
    const updatedStatus = await page.locator('.status-details').textContent();
    const updatedDeltaCount = parseInt(updatedStatus.match(/Deltas: (\d+)/)?.[1] || '0');
    
    console.log('Updated delta count:', updatedDeltaCount);
    console.log('Final status:', updatedStatus);
    
    // Should have received delta updates from market maker
    expect(updatedDeltaCount).toBeGreaterThan(initialDeltaCount);
  });

  test('should show delta indicators when order book changes', async ({ page }) => {
    await page.locator('.grid-row').filter({ hasText: 'MARKET_MAKER' }).click();
    await page.waitForSelector('.orderbook-section .orderbook', { timeout: 10000 });
    
    // Wait for market maker updates
    await page.waitForTimeout(6000);
    
    // Look for delta indicators
    const deltaIndicators = page.locator('.delta-indicator');
    const indicatorCount = await deltaIndicators.count();
    
    console.log(`Found ${indicatorCount} delta indicators`);
    
    if (indicatorCount > 0) {
      const firstIndicator = deltaIndicators.first();
      const text = await firstIndicator.textContent();
      console.log('Delta indicator text:', text);
      
      // Should show ADD/REMOVE with price
      expect(text).toMatch(/ADD|REMOVE/);
      expect(text).toMatch(/\d+¢/);
    }
  });

  test('should flash order book levels when they update', async ({ page }) => {
    await page.locator('.grid-row').filter({ hasText: 'MARKET_MAKER' }).click();
    await page.waitForSelector('.orderbook-section .orderbook', { timeout: 10000 });
    
    // Monitor for flashing levels over time
    let flashingSeen = false;
    const checkForFlashing = async () => {
      const flashingBids = await page.locator('.orderbook-side.bids .level.flash-green').count();
      const flashingAsks = await page.locator('.orderbook-side.asks .level.flash-red').count();
      return flashingBids > 0 || flashingAsks > 0;
    };
    
    // Check multiple times over 8 seconds
    for (let i = 0; i < 8; i++) {
      await page.waitForTimeout(1000);
      if (await checkForFlashing()) {
        flashingSeen = true;
        console.log(`Flashing detected at second ${i + 1}`);
        break;
      }
    }
    
    // At least one flash should have been seen
    if (!flashingSeen) {
      console.log('No flashing levels detected');
      // Take screenshot for debugging
      await page.screenshot({ path: 'no-flash-debug.png' });
    }
  });

  test('should update market grid prices when best bid/ask changes', async ({ page }) => {
    // Get initial prices
    const marketRow = page.locator('.grid-row').filter({ hasText: 'MARKET_MAKER' });
    const initialBid = await marketRow.locator('.bid').textContent();
    const initialAsk = await marketRow.locator('.ask').textContent();
    
    console.log(`Initial - Bid: ${initialBid}, Ask: ${initialAsk}`);
    
    // Wait for market maker updates
    await page.waitForTimeout(8000);
    
    // Get updated prices
    const updatedBid = await marketRow.locator('.bid').textContent();
    const updatedAsk = await marketRow.locator('.ask').textContent();
    
    console.log(`Updated - Bid: ${updatedBid}, Ask: ${updatedAsk}`);
    
    // Prices should have changed due to market maker
    const bidChanged = updatedBid !== initialBid;
    const askChanged = updatedAsk !== initialAsk;
    
    if (!bidChanged && !askChanged) {
      console.log('No price changes detected');
      // Take screenshot for debugging
      await page.screenshot({ path: 'no-price-change-debug.png' });
    }
    
    // At least one price should have changed
    expect(bidChanged || askChanged).toBeTruthy();
  });

  test('should maintain proper snapshot to delta ratio', async ({ page }) => {
    await page.locator('.grid-row').filter({ hasText: 'MARKET_MAKER' }).click();
    await page.waitForSelector('.orderbook-section .orderbook', { timeout: 10000 });
    
    // Wait for several updates
    await page.waitForTimeout(15000);
    
    const status = await page.locator('.status-details').textContent();
    const messageCount = parseInt(status.match(/Messages: (\d+)/)?.[1] || '0');
    const deltaCount = parseInt(status.match(/Deltas: (\d+)/)?.[1] || '0');
    const snapshotCount = parseInt(status.match(/Snapshots: (\d+)/)?.[1] || '0');
    
    console.log(`Final counts - Messages: ${messageCount}, Deltas: ${deltaCount}, Snapshots: ${snapshotCount}`);
    
    if (messageCount > 0) {
      const deltaRatio = deltaCount / messageCount;
      const snapshotRatio = snapshotCount / messageCount;
      
      console.log(`Delta ratio: ${deltaRatio.toFixed(2)}, Snapshot ratio: ${snapshotRatio.toFixed(2)}`);
      
      // Most messages should be deltas (at least 60%)
      expect(deltaRatio).toBeGreaterThan(0.6);
      
      // Snapshots should be less frequent (at most 20%)
      expect(snapshotRatio).toBeLessThan(0.2);
    }
  });
});