import { test, expect } from '@playwright/test';

test.describe('OrderBook WebSocket Updates', () => {
  let page;
  
  test.beforeEach(async ({ browser }) => {
    page = await browser.newPage();
    
    // Set up console logging to capture WebSocket messages
    page.on('console', msg => {
      if (msg.type() === 'log') {
        console.log('PAGE LOG:', msg.text());
      }
    });
    
    // Navigate to the application
    await page.goto('http://localhost:5173');
    await page.waitForLoadState('networkidle');
  });

  test.afterEach(async () => {
    await page.close();
  });

  test('should connect to WebSocket and receive updates', async () => {
    // Wait for WebSocket connection
    await page.waitForTimeout(2000);
    
    // Check that no disconnection banner is shown
    const disconnectBanner = await page.locator('.connection-status').count();
    expect(disconnectBanner).toBe(0);
    
    // Check that markets are loaded
    const marketRows = await page.locator('.grid-row').count();
    expect(marketRows).toBeGreaterThan(0);
  });

  test('should display order book when clicking on a market', async () => {
    // Wait for markets to load
    await page.waitForSelector('.grid-row', { timeout: 10000 });
    
    // Click on the first market
    await page.locator('.grid-row').first().click();
    
    // Wait for order book to appear
    await page.waitForSelector('.orderbook', { timeout: 5000 });
    
    // Check that order book is displayed
    const orderbook = await page.locator('.orderbook').isVisible();
    expect(orderbook).toBeTruthy();
    
    // Check for order book status bar
    const statusBar = await page.locator('.orderbook-status').isVisible();
    expect(statusBar).toBeTruthy();
  });

  test('should show delta and snapshot counts', async () => {
    // Click on MARKET_MAKER market
    await page.waitForSelector('.grid-row', { timeout: 10000 });
    const marketRows = page.locator('.grid-row');
    const count = await marketRows.count();
    
    for (let i = 0; i < count; i++) {
      const ticker = await marketRows.nth(i).locator('.ticker').textContent();
      if (ticker.includes('MARKET_MAKER')) {
        await marketRows.nth(i).click();
        break;
      }
    }
    
    // Wait for order book to load
    await page.waitForSelector('.orderbook', { timeout: 5000 });
    
    // Wait for at least one update
    await page.waitForTimeout(5000);
    
    // Check status details
    const statusDetails = await page.locator('.status-details').textContent();
    console.log('Status details:', statusDetails);
    
    // Verify message count is greater than 0
    expect(statusDetails).toMatch(/Messages: \d+/);
    
    // Extract counts
    const messageMatch = statusDetails.match(/Messages: (\d+)/);
    if (messageMatch) {
      const messageCount = parseInt(messageMatch[1]);
      expect(messageCount).toBeGreaterThan(0);
    }
  });

  test('should display delta updates above correct side', async () => {
    // Navigate to MARKET_MAKER
    await page.waitForSelector('.grid-row', { timeout: 10000 });
    const marketRows = page.locator('.grid-row');
    const count = await marketRows.count();
    
    for (let i = 0; i < count; i++) {
      const ticker = await marketRows.nth(i).locator('.ticker').textContent();
      if (ticker.includes('MARKET_MAKER')) {
        await marketRows.nth(i).click();
        break;
      }
    }
    
    // Wait for order book
    await page.waitForSelector('.orderbook', { timeout: 5000 });
    
    // Wait for a delta update
    let foundDelta = false;
    const startTime = Date.now();
    
    while (!foundDelta && Date.now() - startTime < 20000) {
      const statusType = await page.locator('.status-type').textContent();
      if (statusType.includes('DELTA')) {
        foundDelta = true;
        
        // Check if delta indicator appears
        const deltaIndicators = await page.locator('.delta-indicator').count();
        console.log('Delta indicators found:', deltaIndicators);
        
        if (deltaIndicators > 0) {
          const deltaText = await page.locator('.delta-indicator').first().textContent();
          console.log('Delta indicator text:', deltaText);
          expect(deltaText).toMatch(/^(ADD|REMOVE) \d+ @ \d+¢$/);
        }
      }
      await page.waitForTimeout(100);
    }
    
    expect(foundDelta).toBeTruthy();
  });

  test('should show snapshot every 10th update', async () => {
    // Navigate to MARKET_MAKER
    await page.waitForSelector('.grid-row', { timeout: 10000 });
    const marketRows = page.locator('.grid-row');
    const count = await marketRows.count();
    
    for (let i = 0; i < count; i++) {
      const ticker = await marketRows.nth(i).locator('.ticker').textContent();
      if (ticker.includes('MARKET_MAKER')) {
        await marketRows.nth(i).click();
        break;
      }
    }
    
    // Wait for order book
    await page.waitForSelector('.orderbook', { timeout: 5000 });
    
    // Monitor update types
    const updateTypes = [];
    let lastMessageCount = 0;
    
    // Collect update types for 20 seconds
    const startTime = Date.now();
    while (Date.now() - startTime < 20000) {
      const statusType = await page.locator('.status-type').textContent();
      const statusDetails = await page.locator('.status-details').textContent();
      
      const messageMatch = statusDetails.match(/Messages: (\d+)/);
      if (messageMatch) {
        const currentCount = parseInt(messageMatch[1]);
        if (currentCount > lastMessageCount) {
          updateTypes.push({
            count: currentCount,
            type: statusType.includes('SNAPSHOT') ? 'snapshot' : 'delta'
          });
          lastMessageCount = currentCount;
          console.log(`Update ${currentCount}: ${statusType}`);
        }
      }
      
      await page.waitForTimeout(100);
    }
    
    // Verify snapshot pattern
    console.log('Collected updates:', updateTypes);
    
    // Check if we have snapshots at expected intervals
    const snapshots = updateTypes.filter(u => u.type === 'snapshot');
    console.log('Snapshot counts:', snapshots.map(s => s.count));
    
    // Verify we got multiple snapshots
    expect(snapshots.length).toBeGreaterThanOrEqual(1);
    
    // Verify snapshots occur approximately every 10 updates
    if (snapshots.length >= 2) {
      for (let i = 1; i < snapshots.length; i++) {
        const diff = snapshots[i].count - snapshots[i-1].count;
        // Allow some tolerance due to timing
        expect(diff).toBeGreaterThanOrEqual(8);
        expect(diff).toBeLessThanOrEqual(12);
      }
    }
  });

  test('should flash individual price levels on updates', async () => {
    // Navigate to MARKET_MAKER
    await page.waitForSelector('.grid-row', { timeout: 10000 });
    const marketRows = page.locator('.grid-row');
    const count = await marketRows.count();
    
    for (let i = 0; i < count; i++) {
      const ticker = await marketRows.nth(i).locator('.ticker').textContent();
      if (ticker.includes('MARKET_MAKER')) {
        await marketRows.nth(i).click();
        break;
      }
    }
    
    // Wait for order book
    await page.waitForSelector('.orderbook', { timeout: 5000 });
    
    // Monitor for flashing elements
    let flashDetected = false;
    const startTime = Date.now();
    
    while (!flashDetected && Date.now() - startTime < 10000) {
      const flashingBids = await page.locator('.level.bid.flash-green').count();
      const flashingAsks = await page.locator('.level.ask.flash-red').count();
      
      if (flashingBids > 0 || flashingAsks > 0) {
        flashDetected = true;
        console.log(`Flashing detected - Bids: ${flashingBids}, Asks: ${flashingAsks}`);
      }
      
      await page.waitForTimeout(100);
    }
    
    // We should have detected some flashing
    expect(flashDetected).toBeTruthy();
  });

  test('should properly sort NO side descending', async () => {
    // Navigate to any market with orders
    await page.waitForSelector('.grid-row', { timeout: 10000 });
    await page.locator('.grid-row').first().click();
    
    // Wait for order book
    await page.waitForSelector('.orderbook', { timeout: 5000 });
    
    // Wait for levels to populate
    await page.waitForTimeout(2000);
    
    // Get NO side prices
    const noPrices = await page.locator('.orderbook-side.asks .level .price').allTextContents();
    
    if (noPrices.length > 1) {
      // Convert prices to numbers
      const prices = noPrices.map(p => parseInt(p.replace('¢', '')));
      
      // Verify descending order
      for (let i = 1; i < prices.length; i++) {
        expect(prices[i]).toBeLessThanOrEqual(prices[i-1]);
      }
      
      console.log('NO side prices (should be descending):', prices);
    }
  });

  test('should handle WebSocket reconnection', async () => {
    // Wait for initial connection
    await page.waitForTimeout(2000);
    
    // Verify connected (no disconnect banner)
    let disconnectBanner = await page.locator('.connection-status').count();
    expect(disconnectBanner).toBe(0);
    
    // Simulate disconnect by evaluating in page context
    await page.evaluate(() => {
      if (window.websocketService && window.websocketService.client) {
        window.websocketService.client.deactivate();
      }
    });
    
    // Wait a bit and check for disconnect banner
    await page.waitForTimeout(1000);
    disconnectBanner = await page.locator('.connection-status').count();
    expect(disconnectBanner).toBe(1);
    
    // Wait for automatic reconnection
    await page.waitForTimeout(5000);
    
    // Check if reconnected (banner should disappear)
    disconnectBanner = await page.locator('.connection-status').count();
    expect(disconnectBanner).toBe(0);
  });

  test('full integration test - market maker updates', async () => {
    // Navigate to MARKET_MAKER
    await page.waitForSelector('.grid-row', { timeout: 10000 });
    const marketRows = page.locator('.grid-row');
    const count = await marketRows.count();
    
    for (let i = 0; i < count; i++) {
      const ticker = await marketRows.nth(i).locator('.ticker').textContent();
      if (ticker.includes('MARKET_MAKER')) {
        await marketRows.nth(i).click();
        break;
      }
    }
    
    // Wait for order book
    await page.waitForSelector('.orderbook', { timeout: 5000 });
    
    // Collect data for 15 seconds
    const observations = [];
    const startTime = Date.now();
    
    while (Date.now() - startTime < 15000) {
      const statusType = await page.locator('.status-type').textContent();
      const statusDetails = await page.locator('.status-details').textContent();
      const bidCount = await page.locator('.orderbook-side.bids .level').count();
      const askCount = await page.locator('.orderbook-side.asks .level').count();
      
      // Get top of book if available
      let topBid = null, topAsk = null;
      if (bidCount > 0) {
        topBid = await page.locator('.orderbook-side.bids .level .price').first().textContent();
      }
      if (askCount > 0) {
        topAsk = await page.locator('.orderbook-side.asks .level .price').first().textContent();
      }
      
      observations.push({
        time: Date.now() - startTime,
        type: statusType,
        details: statusDetails,
        bidLevels: bidCount,
        askLevels: askCount,
        topBid,
        topAsk
      });
      
      await page.waitForTimeout(500);
    }
    
    // Analyze observations
    console.log('Observation summary:');
    console.log(`Total observations: ${observations.length}`);
    console.log(`Snapshots: ${observations.filter(o => o.type.includes('SNAPSHOT')).length}`);
    console.log(`Deltas: ${observations.filter(o => o.type.includes('DELTA')).length}`);
    
    // Verify we got both types of updates
    expect(observations.some(o => o.type.includes('SNAPSHOT'))).toBeTruthy();
    expect(observations.some(o => o.type.includes('DELTA'))).toBeTruthy();
    
    // Verify order book has levels
    expect(observations.some(o => o.bidLevels > 0)).toBeTruthy();
    expect(observations.some(o => o.askLevels > 0)).toBeTruthy();
  });
});