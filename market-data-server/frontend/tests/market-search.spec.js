import { test, expect } from '@playwright/test';

test.describe('Market Search and Filtering', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/');
    await page.waitForLoadState('networkidle');
  });

  test('should perform real-time market search', async ({ page }) => {
    const searchInput = page.locator('.search-input');
    
    // Test search with common market prefix
    await searchInput.fill('MARKET');
    await page.waitForTimeout(500); // Wait for debounce
    
    // Should show results or empty state
    const marketsList = page.locator('.markets-list');
    await expect(marketsList).toBeVisible();
    
    // Test search refinement
    await searchInput.fill('MARKET_MAKER');
    await page.waitForTimeout(500);
    
    // Should filter results further
    const marketItems = page.locator('.market-item');
    const count = await marketItems.count();
    
    if (count > 0) {
      // If results exist, they should contain the search term
      const firstMarket = marketItems.first();
      const marketTicker = await firstMarket.locator('.market-ticker').textContent();
      expect(marketTicker.toLowerCase()).toContain('market');
    } else {
      // If no results, should show appropriate message
      await expect(marketsList).toContainText('No markets found matching');
    }
  });

  test('should show market details in search results', async ({ page }) => {
    const searchInput = page.locator('.search-input');
    
    // Search for a common market pattern
    await searchInput.fill('TRUMP');
    await page.waitForTimeout(500);
    
    const marketItems = page.locator('.market-item');
    const count = await marketItems.count();
    
    if (count > 0) {
      const firstMarket = marketItems.first();
      
      // Should show market ticker
      await expect(firstMarket.locator('.market-ticker')).toBeVisible();
      
      // Should show market status
      await expect(firstMarket.locator('.market-status')).toBeVisible();
      
      // Should show market stats
      await expect(firstMarket.locator('.market-stats')).toBeVisible();
      await expect(firstMarket.locator('.market-stats')).toContainText('Last Update:');
      await expect(firstMarket.locator('.market-stats')).toContainText('Messages:');
    }
  });

  test('should highlight selected market', async ({ page }) => {
    const searchInput = page.locator('.search-input');
    
    // Search for markets
    await searchInput.fill('MARKET');
    await page.waitForTimeout(500);
    
    const marketItems = page.locator('.market-item');
    const count = await marketItems.count();
    
    if (count > 0) {
      const firstMarket = marketItems.first();
      
      // Click on first market
      await firstMarket.click();
      
      // Should be highlighted as selected
      await expect(firstMarket).toHaveClass(/selected/);
      
      // Order book viewer should update
      const orderBookViewer = page.locator('.right-panel .card').last();
      
      // Should no longer show the placeholder text
      await expect(orderBookViewer).not.toContainText('Select a market from the filter to view its order book');
    }
  });

  test('should handle empty search results', async ({ page }) => {
    const searchInput = page.locator('.search-input');
    
    // Search for something that definitely won't exist
    await searchInput.fill('NONEXISTENT_MARKET_XYZ123');
    await page.waitForTimeout(500);
    
    const marketsList = page.locator('.markets-list');
    await expect(marketsList).toContainText('No markets found matching "NONEXISTENT_MARKET_XYZ123"');
    
    // Clear search
    await searchInput.fill('');
    await page.waitForTimeout(300);
    
    // Should show default state
    await expect(marketsList).toContainText('Start typing to search for markets');
  });

  test('should update market count display', async ({ page }) => {
    const marketCount = page.locator('.market-count');
    await expect(marketCount).toBeVisible();
    
    // Should show initial count
    const initialText = await marketCount.textContent();
    expect(initialText).toMatch(/\d+ markets/);
    
    // Search for something
    const searchInput = page.locator('.search-input');
    await searchInput.fill('MARKET');
    await page.waitForTimeout(500);
    
    // Count should update
    const updatedText = await marketCount.textContent();
    expect(updatedText).toMatch(/\d+ markets/);
  });

  test('should handle special characters in search', async ({ page }) => {
    const searchInput = page.locator('.search-input');
    
    // Test search with special characters
    await searchInput.fill('TRUMP-2024');
    await page.waitForTimeout(500);
    
    // Should not crash and should show results or empty state
    const marketsList = page.locator('.markets-list');
    await expect(marketsList).toBeVisible();
    
    // Test with numbers
    await searchInput.fill('2024');
    await page.waitForTimeout(500);
    
    await expect(marketsList).toBeVisible();
    
    // Test with mixed case
    await searchInput.fill('trump');
    await page.waitForTimeout(500);
    
    await expect(marketsList).toBeVisible();
  });

  test('should maintain search state on page interactions', async ({ page }) => {
    const searchInput = page.locator('.search-input');
    
    // Perform search
    await searchInput.fill('MARKET');
    await page.waitForTimeout(500);
    
    // Click refresh button in stats
    const refreshButton = page.locator('.refresh-button').first();
    await refreshButton.click();
    
    // Search input should maintain its value
    await expect(searchInput).toHaveValue('MARKET');
    
    // Results should still be filtered
    const marketItems = page.locator('.market-item');
    const count = await marketItems.count();
    
    if (count > 0) {
      const firstMarket = marketItems.first();
      const marketTicker = await firstMarket.locator('.market-ticker').textContent();
      expect(marketTicker.toLowerCase()).toContain('market');
    }
  });
});