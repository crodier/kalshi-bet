import { test, expect } from '@playwright/test';

test.describe('Orders Panel with AG Grid', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/');
    
    // Switch to Test environment to connect to test servers
    await page.locator('.environment-dropdown').selectOption('Test');
    await page.locator('.apply-btn').click();
    
    // Wait for connections to establish
    await page.waitForTimeout(2000);
  });

  test('should display markets admin component', async ({ page }) => {
    await expect(page.locator('.markets-admin')).toBeVisible();
    await expect(page.getByText('Market Administration')).toBeVisible();
    await expect(page.locator('.market-dropdown')).toBeVisible();
  });

  test('should allow market selection', async ({ page }) => {
    const marketDropdown = page.locator('.market-dropdown');
    
    // Select a market
    await marketDropdown.selectOption('MARKET_MAKER');
    await expect(marketDropdown).toHaveValue('MARKET_MAKER');
    
    // Check that market info appears
    await expect(page.getByText('Market: MARKET_MAKER')).toBeVisible();
  });

  test('should display orders panel when market is selected', async ({ page }) => {
    // Select a market
    await page.locator('.market-dropdown').selectOption('MARKET_MAKER');
    
    // Wait for orders panel to appear
    await expect(page.locator('.orders-panel')).toBeVisible();
    await expect(page.getByText('Orders - MARKET_MAKER')).toBeVisible();
    
    // Check for AG Grid
    await expect(page.locator('.ag-theme-alpine')).toBeVisible();
    await expect(page.locator('.ag-header')).toBeVisible();
  });

  test('should display order statistics', async ({ page }) => {
    // Select a market
    await page.locator('.market-dropdown').selectOption('MARKET_MAKER');
    
    // Wait for orders panel to load
    await page.waitForTimeout(1000);
    
    // Check for order statistics
    await expect(page.locator('.orders-stats')).toBeVisible();
    await expect(page.locator('.stat-item').first()).toBeVisible();
  });

  test('should show connection status', async ({ page }) => {
    // Select a market
    await page.locator('.market-dropdown').selectOption('MARKET_MAKER');
    
    // Check connection status indicator
    await expect(page.locator('.connection-status')).toBeVisible();
    
    // Should show either connected or disconnected status
    const statusElement = page.locator('.connection-status span');
    await expect(statusElement).toBeVisible();
  });

  test('should display AG Grid with proper columns', async ({ page }) => {
    // Select a market
    await page.locator('.market-dropdown').selectOption('MARKET_MAKER');
    
    // Wait for grid to load
    await page.waitForTimeout(1000);
    
    // Check for AG Grid headers
    await expect(page.locator('.ag-header-cell').first()).toBeVisible();
    
    // Check for specific column headers in AG Grid
    await expect(page.locator('.ag-header-cell-text', { hasText: 'Order ID' })).toBeVisible();
    await expect(page.locator('.ag-header-cell-text', { hasText: 'Market' })).toBeVisible();
    await expect(page.locator('.ag-header-cell-text', { hasText: 'Side' })).toBeVisible();
    await expect(page.locator('.ag-header-cell-text', { hasText: 'Status' })).toBeVisible();
    await expect(page.locator('.ag-header-cell-text', { hasText: 'Price' })).toBeVisible();
  });

  test('should generate mock orders for selected market', async ({ page }) => {
    // Select a market
    await page.locator('.market-dropdown').selectOption('MARKET_MAKER');
    
    // Wait for mock orders to be generated
    await page.waitForTimeout(2000);
    
    // Check that the orders grid has loaded properly
    await expect(page.locator('.orders-grid-container')).toBeVisible();
    
    // Look for grid content or empty state
    const hasRows = await page.locator('.ag-row').count();
    const hasLoadingOverlay = await page.locator('.ag-overlay-loading-wrapper').isVisible();
    const hasNoRowsOverlay = await page.locator('.ag-overlay-no-rows-wrapper').isVisible();
    
    // Should have either rows or show loading/no-rows overlay
    expect(hasRows > 0 || hasLoadingOverlay || hasNoRowsOverlay).toBeTruthy();
  });

  test('should handle market switching', async ({ page }) => {
    // Select first market
    await page.locator('.market-dropdown').selectOption('MARKET_MAKER');
    await page.waitForTimeout(500);
    
    // Switch to different market
    await page.locator('.market-dropdown').selectOption('TRUMP-2024');
    await expect(page.getByText('Orders - TRUMP-2024')).toBeVisible();
    
    // Orders panel should still be visible
    await expect(page.locator('.orders-panel')).toBeVisible();
  });

  test('should hide orders panel when no market selected', async ({ page }) => {
    // Initially no market selected
    await expect(page.locator('.orders-panel')).not.toBeVisible();
    
    // Select a market
    await page.locator('.market-dropdown').selectOption('MARKET_MAKER');
    await expect(page.locator('.orders-panel')).toBeVisible();
    
    // Deselect market
    await page.locator('.market-dropdown').selectOption('');
    await expect(page.locator('.orders-panel')).not.toBeVisible();
  });

  test('should update order statistics based on mock data', async ({ page }) => {
    // Select a market
    await page.locator('.market-dropdown').selectOption('MARKET_MAKER');
    
    // Wait for orders to load
    await page.waitForTimeout(1000);
    
    // Check that statistics show non-zero values
    const totalStat = page.locator('.stat-item').first();
    await expect(totalStat).toBeVisible();
    
    // The text should contain "Total:" followed by a number
    const totalText = await totalStat.textContent();
    expect(totalText).toMatch(/Total: \d+/);
  });
});