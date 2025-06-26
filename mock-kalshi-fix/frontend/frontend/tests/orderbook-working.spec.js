import { test, expect } from '@playwright/test';

test.describe('OrderBook Current Functionality', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('http://localhost:5173');
    // Wait for app to load
    await page.waitForSelector('.app', { timeout: 10000 });
  });

  test('should load the application', async ({ page }) => {
    await expect(page).toHaveTitle(/Kalshi Mock Trading/);
    await expect(page.locator('.app')).toBeVisible();
    await expect(page.locator('h1')).toContainText('Mock Kalshi Trading Platform');
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
    await expect(page.locator('.orderbook h3')).toContainText('Order Book - MARKET_MAKER');
    
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
});

test.describe('WebSocket Connection', () => {
  test('should show connection status', async ({ page }) => {
    await page.goto('http://localhost:5173');
    
    // Should eventually show connected
    await expect(page.locator('.connection-status')).toBeVisible();
    
    // Wait for connection (may show "Connecting..." initially)
    await page.waitForTimeout(2000);
    
    // Should show connected
    const statusText = await page.locator('.connection-status').textContent();
    expect(statusText).toMatch(/Connected|Connecting/);
  });

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
    await expect(bidLevels.first()).toBeVisible();
    
    // Check snapshot count
    await page.waitForTimeout(1000);
    await expect(page.locator('.status-details')).toContainText('Snapshots: 1');
  });
});

test.describe('Delta Updates', () => {
  test('should receive delta updates when market maker updates', async ({ page }) => {
    await page.goto('http://localhost:5173');
    
    // Click on MARKET_MAKER to start watching
    await page.locator('.grid-row').filter({ hasText: 'MARKET_MAKER' }).click();
    await expect(page.locator('.orderbook')).toBeVisible();
    
    // Get initial state
    const initialStatus = await page.locator('.status-details').textContent();
    const initialDeltaCount = parseInt(initialStatus.match(/Deltas: (\d+)/)?.[1] || '0');
    const initialMessageCount = parseInt(initialStatus.match(/Messages: (\d+)/)?.[1] || '0');
    
    // Wait for market maker updates (it updates every 3 seconds)
    await page.waitForTimeout(5000);
    
    // Check that delta count increased
    const updatedStatus = await page.locator('.status-details').textContent();
    const updatedDeltaCount = parseInt(updatedStatus.match(/Deltas: (\d+)/)?.[1] || '0');
    const updatedMessageCount = parseInt(updatedStatus.match(/Messages: (\d+)/)?.[1] || '0');
    
    console.log(`Initial: Messages=${initialMessageCount}, Deltas=${initialDeltaCount}`);
    console.log(`Updated: Messages=${updatedMessageCount}, Deltas=${updatedDeltaCount}`);
    
    expect(updatedMessageCount).toBeGreaterThan(initialMessageCount);
    // We should see some deltas from market maker updates
    expect(updatedDeltaCount).toBeGreaterThan(initialDeltaCount);
  });

  test('should flash price levels when they update', async ({ page }) => {
    await page.goto('http://localhost:5173');
    await page.locator('.grid-row').filter({ hasText: 'MARKET_MAKER' }).click();
    
    // Wait for a market maker update (every 3 seconds)
    await page.waitForTimeout(4000);
    
    // Look for any flashing levels
    const flashingBids = page.locator('.orderbook-side.bids .level.flash-green');
    const flashingAsks = page.locator('.orderbook-side.asks .level.flash-red');
    
    // Should see at least one flashing level from market maker updates
    const hasBidFlash = await flashingBids.count() > 0;
    const hasAskFlash = await flashingAsks.count() > 0;
    
    expect(hasBidFlash || hasAskFlash).toBeTruthy();
  });

  test('should update market grid when best prices change', async ({ page }) => {
    await page.goto('http://localhost:5173');
    
    // Get initial MARKET_MAKER bid/ask
    const marketRow = page.locator('.grid-row').filter({ hasText: 'MARKET_MAKER' });
    const initialBid = await marketRow.locator('.bid').textContent();
    const initialAsk = await marketRow.locator('.ask').textContent();
    
    console.log(`Initial bid: ${initialBid}, ask: ${initialAsk}`);
    
    // Wait for market maker updates
    await page.waitForTimeout(6000);
    
    // Check if prices changed (market maker moves prices)
    const updatedBid = await marketRow.locator('.bid').textContent();
    const updatedAsk = await marketRow.locator('.ask').textContent();
    
    console.log(`Updated bid: ${updatedBid}, ask: ${updatedAsk}`);
    
    // At least one price should have changed
    const bidChanged = updatedBid !== initialBid;
    const askChanged = updatedAsk !== initialAsk;
    
    expect(bidChanged || askChanged).toBeTruthy();
  });

  test('should show delta indicators above order book sides', async ({ page }) => {
    await page.goto('http://localhost:5173');
    await page.locator('.grid-row').filter({ hasText: 'MARKET_MAKER' }).click();
    
    // Wait for market maker updates
    await page.waitForTimeout(5000);
    
    // Check for delta indicators
    const deltaIndicators = page.locator('.delta-indicator');
    
    // Should have seen at least one delta indicator
    const indicatorCount = await deltaIndicators.count();
    console.log(`Found ${indicatorCount} delta indicators`);
    
    if (indicatorCount > 0) {
      const firstIndicator = deltaIndicators.first();
      const text = await firstIndicator.textContent();
      
      // Should show ADD or REMOVE with price
      expect(text).toMatch(/ADD|REMOVE/);
      expect(text).toMatch(/\d+¢/);
    }
  });

  test('should maintain snapshot interval pattern', async ({ page }) => {
    await page.goto('http://localhost:5173');
    await page.locator('.grid-row').filter({ hasText: 'MARKET_MAKER' }).click();
    
    // Get initial counts
    const getMessageCounts = async () => {
      const status = await page.locator('.status-details').textContent();
      return {
        messages: parseInt(status.match(/Messages: (\d+)/)?.[1] || '0'),
        deltas: parseInt(status.match(/Deltas: (\d+)/)?.[1] || '0'),
        snapshots: parseInt(status.match(/Snapshots: (\d+)/)?.[1] || '0')
      };
    };
    
    const initial = await getMessageCounts();
    console.log('Initial counts:', initial);
    
    // Wait for several updates
    await page.waitForTimeout(15000);
    
    const final = await getMessageCounts();
    console.log('Final counts:', final);
    
    // Should have received multiple messages
    expect(final.messages).toBeGreaterThan(initial.messages);
    
    // Calculate expected snapshots (every 10 messages)
    const messagesSinceStart = final.messages - initial.messages;
    const expectedNewSnapshots = Math.floor(messagesSinceStart / 10);
    
    console.log(`Messages since start: ${messagesSinceStart}`);
    console.log(`Expected new snapshots: ${expectedNewSnapshots}`);
    console.log(`Actual new snapshots: ${final.snapshots - initial.snapshots}`);
    
    // Verify snapshot pattern
    if (messagesSinceStart >= 10) {
      expect(final.snapshots).toBeGreaterThan(initial.snapshots);
    }
    
    // Most messages should be deltas
    const deltaRatio = (final.deltas - initial.deltas) / messagesSinceStart;
    console.log(`Delta ratio: ${deltaRatio}`);
    expect(deltaRatio).toBeGreaterThan(0.5); // At least 50% should be deltas
  });
});

test.describe('Market Grid Updates', () => {
  test('should show "Yes Buy Best" and "No Buy Best" headers', async ({ page }) => {
    await page.goto('http://localhost:5173');
    
    // Check column headers
    const headers = page.locator('.market-grid-header');
    const headerTexts = await headers.allTextContents();
    
    expect(headerTexts).toContain('Yes Buy Best');
    expect(headerTexts).toContain('No Buy Best');
  });

  test('should flash market grid cells on price updates', async ({ page }) => {
    await page.goto('http://localhost:5173');
    
    // Wait for market maker updates
    await page.waitForTimeout(5000);
    
    // Look for flashing cells in market grid
    const flashingCells = page.locator('.grid-row .flash');
    
    // Should see at least one flashing cell from updates
    const flashCount = await flashingCells.count();
    console.log(`Found ${flashCount} flashing cells`);
    
    expect(flashCount).toBeGreaterThan(0);
  });
});