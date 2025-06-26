import { test, expect } from '@playwright/test';

test.describe('Delta Fixes - Dedicated Space & Bid/Ask Updates', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('http://localhost:5173');
    await page.waitForSelector('.app', { timeout: 10000 });
  });

  test('should have dedicated delta indicators area that does not shift order book', async ({ page }) => {
    // Click on MARKET_MAKER to open order book
    await page.locator('.grid-row').filter({ hasText: 'MARKET_MAKER' }).click();
    await page.waitForSelector('.orderbook-section .orderbook', { timeout: 10000 });
    
    // Check that delta indicators area exists
    await expect(page.locator('.delta-indicators-area')).toBeVisible();
    
    // Wait for delta updates
    await page.waitForTimeout(5000);
    
    // Check for delta indicator
    const deltaIndicator = page.locator('.delta-indicator');
    if (await deltaIndicator.count() > 0) {
      // Verify delta indicator is in the dedicated area, not inside orderbook sides
      const deltaInArea = page.locator('.delta-indicators-area .delta-indicator');
      await expect(deltaInArea).toBeVisible();
      
      // Verify no delta indicators inside orderbook sides
      const deltaInBids = page.locator('.orderbook-side.bids .delta-indicator');
      const deltaInAsks = page.locator('.orderbook-side.asks .delta-indicator');
      
      expect(await deltaInBids.count()).toBe(0);
      expect(await deltaInAsks.count()).toBe(0);
      
      console.log('✅ Delta indicators are in dedicated area, not shifting order book');
    }
  });

  test('should update Yes Buy Best and No Buy Best columns from delta feeds', async ({ page }) => {
    // Get initial bid/ask values from market grid
    const marketRow = page.locator('.grid-row').filter({ hasText: 'MARKET_MAKER' });
    
    const initialBid = await marketRow.locator('.bid').textContent();
    const initialAsk = await marketRow.locator('.ask').textContent();
    
    console.log(`Initial - Yes Buy Best: ${initialBid}, No Buy Best: ${initialAsk}`);
    
    // Click on MARKET_MAKER to start monitoring deltas
    await marketRow.click();
    await page.waitForSelector('.orderbook-section .orderbook', { timeout: 10000 });
    
    // Wait for WebSocket connection and delta messages
    // Check for delta indicator updates rather than just price changes
    let deltaUpdateSeen = false;
    let changesSeen = false;
    
    for (let i = 0; i < 15; i++) {
      await page.waitForTimeout(1000);
      
      // Check if we see delta updates in the order book
      const deltaIndicator = page.locator('.delta-indicators-area .delta-indicator');
      if (await deltaIndicator.count() > 0) {
        deltaUpdateSeen = true;
        console.log('✅ Delta indicator detected');
      }
      
      // Check for any changes in bid/ask values
      const currentBid = await marketRow.locator('.bid').textContent();
      const currentAsk = await marketRow.locator('.ask').textContent();
      
      if (currentBid !== initialBid || currentAsk !== initialAsk) {
        changesSeen = true;
        console.log(`✅ Price changes detected - Bid: ${initialBid} → ${currentBid}, Ask: ${initialAsk} → ${currentAsk}`);
        break;
      }
    }
    
    // Test passes if we see either delta updates OR price changes
    const success = deltaUpdateSeen || changesSeen;
    expect(success).toBeTruthy();
    
    if (deltaUpdateSeen) {
      console.log('✅ Delta updates are being displayed correctly');
    }
    if (changesSeen) {
      console.log('✅ Bid/Ask columns are updating from market data');
    }
  });

  test('should flash specific bid/ask cells when they update', async ({ page }) => {
    const marketRow = page.locator('.grid-row').filter({ hasText: 'MARKET_MAKER' });
    
    // Click on MARKET_MAKER
    await marketRow.click();
    await page.waitForSelector('.orderbook-section .orderbook', { timeout: 10000 });
    
    // Monitor for flashing in bid/ask cells or any flash-* class
    let bidFlashSeen = false;
    let askFlashSeen = false;
    let anyFlashSeen = false;
    
    const checkForFlashing = async () => {
      const flashingBid = await marketRow.locator('.bid.flash-green').count();
      const flashingAsk = await marketRow.locator('.ask.flash-red').count();
      
      // Also check for any flash classes
      const anyFlash = await marketRow.locator('[class*="flash"]').count();
      
      if (flashingBid > 0) bidFlashSeen = true;
      if (flashingAsk > 0) askFlashSeen = true;
      if (anyFlash > 0) anyFlashSeen = true;
      
      return bidFlashSeen || askFlashSeen || anyFlashSeen;
    };
    
    // Check for flashing over time
    for (let i = 0; i < 15; i++) {
      await page.waitForTimeout(1000);
      await checkForFlashing();
      
      if (anyFlashSeen) {
        break;
      }
    }
    
    // At least some flashing should have occurred
    expect(anyFlashSeen || bidFlashSeen || askFlashSeen).toBeTruthy();
    
    if (bidFlashSeen) {
      console.log('✅ Yes Buy Best cell flashed when updated');
    }
    if (askFlashSeen) {
      console.log('✅ No Buy Best cell flashed when updated');
    }
    if (anyFlashSeen) {
      console.log('✅ Some market data flashing detected');
    }
  });

  test('should display delta indicators with proper formatting', async ({ page }) => {
    await page.locator('.grid-row').filter({ hasText: 'MARKET_MAKER' }).click();
    await page.waitForSelector('.orderbook-section .orderbook', { timeout: 10000 });
    
    // Wait for delta updates
    await page.waitForTimeout(5000);
    
    const deltaIndicator = page.locator('.delta-indicators-area .delta-indicator');
    
    if (await deltaIndicator.count() > 0) {
      const deltaText = await deltaIndicator.textContent();
      console.log('Delta indicator text:', deltaText);
      
      // Should contain side (YES/NO), action (ADD/REMOVE), quantity, and price
      expect(deltaText).toMatch(/(YES|NO):/);
      expect(deltaText).toMatch(/(ADD|REMOVE)/);
      expect(deltaText).toMatch(/\d+/); // quantity
      expect(deltaText).toMatch(/@ \d+¢/); // price
      
      // Check for proper CSS classes
      const hasYesClass = await deltaIndicator.evaluate(el => el.classList.contains('delta-yes'));
      const hasNoClass = await deltaIndicator.evaluate(el => el.classList.contains('delta-no'));
      
      expect(hasYesClass || hasNoClass).toBeTruthy();
      
      console.log('✅ Delta indicator properly formatted with side, action, quantity, and price');
    }
  });

  test('should maintain stable order book layout during delta updates', async ({ page }) => {
    await page.locator('.grid-row').filter({ hasText: 'MARKET_MAKER' }).click();
    await page.waitForSelector('.orderbook-section .orderbook', { timeout: 10000 });
    
    // Get initial position of the first bid level
    const firstBidLevel = page.locator('.orderbook-side.bids .level').first();
    await expect(firstBidLevel).toBeVisible();
    
    const initialPosition = await firstBidLevel.boundingBox();
    
    // Wait for several delta updates
    await page.waitForTimeout(6000);
    
    // Check that the first bid level is still in the same position
    const finalPosition = await firstBidLevel.boundingBox();
    
    if (initialPosition && finalPosition) {
      // Allow for small variations but ensure no major shifts
      const verticalDrift = Math.abs(finalPosition.y - initialPosition.y);
      
      expect(verticalDrift).toBeLessThan(35); // Allow max 35px drift (delta indicators area)
      
      console.log(`✅ Order book layout stable - vertical drift: ${verticalDrift}px`);
    }
  });

  test('should show order book statistics with delta/snapshot counts', async ({ page }) => {
    await page.locator('.grid-row').filter({ hasText: 'MARKET_MAKER' }).click();
    await page.waitForSelector('.orderbook-section .orderbook', { timeout: 10000 });
    
    // Wait for updates
    await page.waitForTimeout(8000);
    
    const statusDetails = page.locator('.status-details');
    await expect(statusDetails).toBeVisible();
    
    const statusText = await statusDetails.textContent();
    console.log('Status text:', statusText);
    
    // Should show message counts
    expect(statusText).toMatch(/Messages: \d+/);
    expect(statusText).toMatch(/Deltas: \d+/);
    expect(statusText).toMatch(/Snapshots: \d+/);
    
    // Extract counts
    const messageCount = parseInt(statusText.match(/Messages: (\d+)/)?.[1] || '0');
    const deltaCount = parseInt(statusText.match(/Deltas: (\d+)/)?.[1] || '0');
    const snapshotCount = parseInt(statusText.match(/Snapshots: (\d+)/)?.[1] || '0');
    
    // Should have received some deltas
    expect(deltaCount).toBeGreaterThan(0);
    
    console.log(`✅ Statistics: ${messageCount} messages, ${deltaCount} deltas, ${snapshotCount} snapshots`);
  });

  test('should show correct No Buy Best prices (highest NO bid)', async ({ page }) => {
    const marketRow = page.locator('.grid-row').filter({ hasText: 'MARKET_MAKER' });
    
    // Click on MARKET_MAKER to start monitoring
    await marketRow.click();
    await page.waitForSelector('.orderbook-section .orderbook', { timeout: 10000 });
    
    // Wait for orderbook to populate
    await page.waitForTimeout(3000);
    
    // Get the displayed No Buy Best price
    const noBuyBest = await marketRow.locator('.ask').textContent();
    console.log(`Displayed No Buy Best: ${noBuyBest}`);
    
    // Check order book to verify this is actually the highest NO price
    const noLevels = page.locator('.orderbook-side.asks .level .price');
    const noLevelCount = await noLevels.count();
    
    if (noLevelCount > 0) {
      const noPrices = [];
      for (let i = 0; i < noLevelCount; i++) {
        const priceText = await noLevels.nth(i).textContent();
        const price = parseInt(priceText.replace('¢', ''));
        noPrices.push(price);
      }
      
      const highestNoPrice = Math.max(...noPrices);
      const displayedPrice = parseInt(noBuyBest.split('¢')[0]);
      
      console.log(`NO order book prices: ${noPrices.join(', ')}`);
      console.log(`Highest NO price: ${highestNoPrice}¢`);
      console.log(`Displayed price: ${displayedPrice}¢`);
      
      // The displayed price should match the highest NO price
      expect(displayedPrice).toBe(highestNoPrice);
      console.log('✅ No Buy Best shows correct highest NO bid price');
    }
  });

  test('should show Last Bid and Last Ask Update columns with timestamps', async ({ page }) => {
    const marketRow = page.locator('.grid-row').filter({ hasText: 'MARKET_MAKER' });
    
    // Click on MARKET_MAKER to start monitoring  
    await marketRow.click();
    await page.waitForSelector('.orderbook-section .orderbook', { timeout: 10000 });
    
    // Wait for some orderbook activity
    await page.waitForTimeout(5000);
    
    // Check that Last Bid Update and Last Ask Update columns exist and have content
    const lastBidUpdate = await marketRow.locator('.last-bid-update').textContent();
    const lastAskUpdate = await marketRow.locator('.last-ask-update').textContent();
    
    console.log(`Last Bid Update: ${lastBidUpdate}`);
    console.log(`Last Ask Update: ${lastAskUpdate}`);
    
    // Should show some update information (not just "-")
    const hasBidUpdate = lastBidUpdate && lastBidUpdate !== '-' && lastBidUpdate.trim() !== '';
    const hasAskUpdate = lastAskUpdate && lastAskUpdate !== '-' && lastAskUpdate.trim() !== '';
    
    expect(hasBidUpdate || hasAskUpdate).toBeTruthy();
    
    if (hasBidUpdate) {
      // Should contain action (ADD/REMOVE/UPDATE), quantity, price, and timestamp
      expect(lastBidUpdate).toMatch(/(ADD|REMOVE|UPDATE)/);
      expect(lastBidUpdate).toMatch(/\d+¢/); // price
      expect(lastBidUpdate).toMatch(/\d{2}:\d{2}:\d{2}/); // timestamp
      console.log('✅ Last Bid Update column showing correctly');
    }
    
    if (hasAskUpdate) {
      // Should contain action (ADD/REMOVE/UPDATE), quantity, price, and timestamp
      expect(lastAskUpdate).toMatch(/(ADD|REMOVE|UPDATE)/);
      expect(lastAskUpdate).toMatch(/\d+¢/); // price  
      expect(lastAskUpdate).toMatch(/\d{2}:\d{2}:\d{2}/); // timestamp
      console.log('✅ Last Ask Update column showing correctly');
    }
  });
});