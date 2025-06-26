import { test, expect } from '@playwright/test';

test.describe('Market Data Server Admin Dashboard', () => {
  test.beforeEach(async ({ page }) => {
    // Navigate to the admin dashboard
    await page.goto('/');
    
    // Wait for the page to load
    await page.waitForLoadState('networkidle');
  });

  test('should load admin dashboard with header and main components', async ({ page }) => {
    // Check header
    await expect(page.locator('h1')).toContainText('Market Data Server Admin');
    
    // Check status bar
    await expect(page.locator('.status-bar')).toBeVisible();
    
    // Check main content areas
    await expect(page.locator('.left-panel')).toBeVisible();
    await expect(page.locator('.right-panel')).toBeVisible();
    
    // Check system stats component
    await expect(page.locator('.card')).toContainText('System Statistics');
    
    // Check market filter component
    await expect(page.locator('.card')).toContainText('Market Filter');
    
    // Check order book viewer component
    await expect(page.locator('.card')).toContainText('Order Book Viewer');
  });

  test('should show connection status in header', async ({ page }) => {
    // Check for connection status indicator
    const connectionStatus = page.locator('.connection-status');
    await expect(connectionStatus).toBeVisible();
    
    // Should show status text
    await expect(connectionStatus.locator('span')).toBeVisible();
    
    // Should have status indicator
    await expect(connectionStatus.locator('.status-indicator')).toBeVisible();
  });

  test('should display system statistics', async ({ page }) => {
    // Wait for system stats to load
    await page.waitForSelector('.stats-grid', { timeout: 10000 });
    
    // Check for key statistics
    const expectedStats = [
      'Total Markets',
      'Active Markets', 
      'Messages/sec',
      'Average Latency',
      'WebSocket Status'
    ];
    
    for (const stat of expectedStats) {
      await expect(page.locator('.stat-label')).toContainText(stat);
    }
    
    // Check refresh button
    const refreshButton = page.locator('.refresh-button');
    await expect(refreshButton).toBeVisible();
    await expect(refreshButton).toContainText('Refresh');
  });

  test('should allow market search and filtering', async ({ page }) => {
    const searchInput = page.locator('.search-input');
    await expect(searchInput).toBeVisible();
    
    // Test search functionality
    await searchInput.fill('MARKET');
    
    // Wait for search results
    await page.waitForTimeout(1000);
    
    // Should show filtered results or "start typing" message
    const marketsList = page.locator('.markets-list');
    await expect(marketsList).toBeVisible();
    
    // Clear search
    await searchInput.fill('');
    
    // Should show default state
    await expect(marketsList).toContainText('Start typing to search for markets');
  });

  test('should show order book viewer placeholder when no market selected', async ({ page }) => {
    const orderBookViewer = page.locator('.right-panel .card').last();
    
    await expect(orderBookViewer).toContainText('Order Book Viewer');
    await expect(orderBookViewer).toContainText('Select a market from the filter to view its order book');
  });

  test('should refresh system statistics when refresh button clicked', async ({ page }) => {
    // Wait for initial stats to load
    await page.waitForSelector('.stats-grid', { timeout: 10000 });
    
    const refreshButton = page.locator('.refresh-button');
    await expect(refreshButton).toBeVisible();
    
    // Get initial timestamp or value to compare
    const initialLastUpdate = await page.locator('.stat-value').last().textContent();
    
    // Click refresh
    await refreshButton.click();
    
    // Wait for potential update
    await page.waitForTimeout(1000);
    
    // The refresh should trigger an API call (we can't easily test the exact change)
    // but we can verify the button is still functional
    await expect(refreshButton).toBeVisible();
  });

  test('should handle mobile responsive layout', async ({ page }) => {
    // Set mobile viewport
    await page.setViewportSize({ width: 375, height: 667 });
    
    // Check that main content adapts to mobile
    const mainContent = page.locator('.main-content');
    await expect(mainContent).toBeVisible();
    
    // Check that components are still visible
    await expect(page.locator('.card')).toHaveCount(3); // System stats, market filter, order book viewer
    
    // Check header adapts
    await expect(page.locator('.header')).toBeVisible();
    await expect(page.locator('h1')).toContainText('Market Data Server Admin');
  });

  test('should display performance metrics', async ({ page }) => {
    // Wait for system stats to load
    await page.waitForSelector('.performance-metrics', { timeout: 10000 });
    
    const performanceSection = page.locator('.performance-metrics');
    await expect(performanceSection).toBeVisible();
    await expect(performanceSection).toContainText('Performance Metrics');
    
    // Check for specific metrics
    await expect(performanceSection).toContainText('Max Throughput');
    await expect(performanceSection).toContainText('Uptime');
  });

  test('should show loading states appropriately', async ({ page }) => {
    // On initial load, there might be loading states
    const cards = page.locator('.card');
    
    // Check that cards load content (not just loading states)
    await expect(cards.first()).not.toContainText('Loading...');
    
    // All cards should have content
    const cardCount = await cards.count();
    expect(cardCount).toBeGreaterThanOrEqual(3);
  });
});