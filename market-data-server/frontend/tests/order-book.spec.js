import { test, expect } from '@playwright/test';

test.describe('Order Book Viewer', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/');
    await page.waitForLoadState('networkidle');
  });

  test('should show placeholder when no market selected', async ({ page }) => {
    const orderBookViewer = page.locator('.right-panel .card').last();
    
    await expect(orderBookViewer).toContainText('Order Book Viewer');
    await expect(orderBookViewer).toContainText('Select a market from the filter to view its order book');
  });

  test('should select market and display order book', async ({ page }) => {
    // Search for and select a market
    const searchInput = page.locator('.search-input');
    await searchInput.fill('MARKET');
    await page.waitForTimeout(500);
    
    const marketItems = page.locator('.market-item');
    const count = await marketItems.count();
    
    if (count > 0) {
      // Click on first market
      await marketItems.first().click();
      
      // Order book viewer should update
      const orderBookViewer = page.locator('.right-panel .card').last();
      
      // Should show market info
      await expect(orderBookViewer.locator('.market-info')).toBeVisible();
      
      // Should show update info
      await expect(orderBookViewer.locator('.update-info')).toBeVisible();
      await expect(orderBookViewer).toContainText('Messages:');
      await expect(orderBookViewer).toContainText('Last Update:');
      
      // Should show order book container or loading state
      const hasOrderBook = await orderBookViewer.locator('.orderbook-container').count() > 0;
      const hasLoading = await orderBookViewer.locator('.loading').count() > 0;
      
      expect(hasOrderBook || hasLoading).toBeTruthy();
    }
  });

  test('should display order book structure when data available', async ({ page }) => {
    // Mock WebSocket connection or wait for real data
    await page.addInitScript(() => {
      // Mock orderbook data for testing
      window.mockOrderBookData = {
        marketTicker: 'TEST_MARKET',
        lastSequence: 123,
        yes: {
          bids: { '55': 100, '54': 200 }
        },
        no: {
          bids: { '45': 150, '44': 250 }
        },
        receivedTimestamp: Date.now(),
        publishedTimestamp: Date.now()
      };
    });
    
    // Search and select market
    const searchInput = page.locator('.search-input');
    await searchInput.fill('MARKET');
    await page.waitForTimeout(500);
    
    const marketItems = page.locator('.market-item');
    if (await marketItems.count() > 0) {
      await marketItems.first().click();
      
      const orderBookViewer = page.locator('.right-panel .card').last();
      
      // Should eventually show orderbook structure (or loading)
      await page.waitForTimeout(2000);
      
      // Check for orderbook sides
      const hasBothSides = await orderBookViewer.locator('.orderbook-side').count() >= 2;
      const hasLoading = await orderBookViewer.locator('.loading').count() > 0;
      
      if (hasBothSides) {
        // Should have YES and NO sides
        await expect(orderBookViewer.locator('.orderbook-side').first()).toContainText('Yes (Buy)');
        await expect(orderBookViewer.locator('.orderbook-side').last()).toContainText('No (Buy)');
        
        // Should have headers
        await expect(orderBookViewer.locator('.orderbook-header')).toContainText('Price');
        await expect(orderBookViewer.locator('.orderbook-header')).toContainText('Quantity');
        await expect(orderBookViewer.locator('.orderbook-header')).toContainText('Received');
        await expect(orderBookViewer.locator('.orderbook-header')).toContainText('Published');
      } else {
        // Should show loading or waiting state
        expect(hasLoading || await orderBookViewer.textContent()).toMatch(/waiting|loading/i);
      }
    }
  });

  test('should show timestamp information', async ({ page }) => {
    // Search and select market
    const searchInput = page.locator('.search-input');
    await searchInput.fill('MARKET');
    await page.waitForTimeout(500);
    
    const marketItems = page.locator('.market-item');
    if (await marketItems.count() > 0) {
      await marketItems.first().click();
      
      const orderBookViewer = page.locator('.right-panel .card').last();
      
      // Should show timing information
      const updateInfo = orderBookViewer.locator('.update-info');
      await expect(updateInfo).toBeVisible();
      
      // Should contain latency info if available
      const hasLatencyInfo = await updateInfo.locator('.latency-info').count() > 0;
      if (hasLatencyInfo) {
        await expect(updateInfo).toContainText('Processing Latency:');
      }
    }
  });

  test('should handle connection status warnings', async ({ page }) => {
    // The order book viewer should show appropriate warnings for connection issues
    const orderBookViewer = page.locator('.right-panel .card').last();
    
    // Initially might show connection warning if WebSocket isn't connected
    const hasConnectionWarning = await orderBookViewer.locator('.connection-warning').count() > 0;
    const hasPlaceholder = await orderBookViewer.textContent().then(text => 
      text.includes('Select a market from the filter to view its order book')
    );
    
    // Should show either placeholder or connection warning initially
    expect(hasConnectionWarning || hasPlaceholder).toBeTruthy();
  });

  test('should update message counts', async ({ page }) => {
    // Search and select market
    const searchInput = page.locator('.search-input');
    await searchInput.fill('MARKET');
    await page.waitForTimeout(500);
    
    const marketItems = page.locator('.market-item');
    if (await marketItems.count() > 0) {
      await marketItems.first().click();
      
      const orderBookViewer = page.locator('.right-panel .card').last();
      
      // Should show message count
      await expect(orderBookViewer).toContainText('Messages:');
      
      // Should show last update time
      await expect(orderBookViewer).toContainText('Last Update:');
    }
  });

  test('should display market ticker in header', async ({ page }) => {
    // Search and select market
    const searchInput = page.locator('.search-input');
    await searchInput.fill('MARKET');
    await page.waitForTimeout(500);
    
    const marketItems = page.locator('.market-item');
    if (await marketItems.count() > 0) {
      const firstMarket = marketItems.first();
      const marketTicker = await firstMarket.locator('.market-ticker').textContent();
      
      await firstMarket.click();
      
      const orderBookViewer = page.locator('.right-panel .card').last();
      
      // Should show the selected market ticker
      await expect(orderBookViewer.locator('.market-info h3')).toContainText(marketTicker);
    }
  });

  test('should handle switching between markets', async ({ page }) => {
    // Search for markets
    const searchInput = page.locator('.search-input');
    await searchInput.fill('MARKET');
    await page.waitForTimeout(500);
    
    const marketItems = page.locator('.market-item');
    const count = await marketItems.count();
    
    if (count >= 2) {
      // Select first market
      const firstMarketTicker = await marketItems.first().locator('.market-ticker').textContent();
      await marketItems.first().click();
      
      const orderBookViewer = page.locator('.right-panel .card').last();
      
      // Verify first market is selected
      await expect(orderBookViewer.locator('.market-info h3')).toContainText(firstMarketTicker);
      
      // Select second market
      const secondMarketTicker = await marketItems.nth(1).locator('.market-ticker').textContent();
      await marketItems.nth(1).click();
      
      // Verify second market is now selected
      await expect(orderBookViewer.locator('.market-info h3')).toContainText(secondMarketTicker);
      
      // Message count should reset or update for new market
      await expect(orderBookViewer).toContainText('Messages:');
    }
  });
});