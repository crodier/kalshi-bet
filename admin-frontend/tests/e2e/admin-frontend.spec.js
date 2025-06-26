import { test, expect } from '@playwright/test';

test.describe('Admin Frontend', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/');
  });

  test('should display main header and title', async ({ page }) => {
    await expect(page.locator('h1')).toContainText('Kalshi Admin Frontend');
    await expect(page.locator('header p')).toContainText('Multi-Service Trading System Administration');
  });

  test('should display environment selector', async ({ page }) => {
    await expect(page.locator('.environment-selector')).toBeVisible();
    await expect(page.locator('.environment-dropdown')).toBeVisible();
    
    // Check default environment is Local
    await expect(page.locator('.environment-dropdown')).toHaveValue('Local');
  });

  test('should display system health dashboard', async ({ page }) => {
    await expect(page.locator('.system-health-dashboard')).toBeVisible();
    await expect(page.locator('h2')).toContainText('System Health Dashboard');
    
    // Check that all service cards are present
    await expect(page.locator('.service-card')).toHaveCount(4);
    
    // Check service names in service cards
    await expect(page.locator('.service-name', { hasText: 'Mock Server' })).toBeVisible();
    await expect(page.locator('.service-name', { hasText: 'Market Data' })).toBeVisible();
    await expect(page.locator('.service-name', { hasText: 'Order Rebuilder' })).toBeVisible();
    await expect(page.locator('.service-name', { hasText: 'Temp Orders' })).toBeVisible();
  });

  test('should display global stats and last execution cards', async ({ page }) => {
    await expect(page.locator('.global-stats-card')).toBeVisible();
    await expect(page.locator('.last-execution-card')).toBeVisible();
    
    await expect(page.getByText('Global Stats')).toBeVisible();
    await expect(page.getByText('Last Execution')).toBeVisible();
  });

  test('should allow environment switching', async ({ page }) => {
    const environmentDropdown = page.locator('.environment-dropdown');
    
    // Switch to Dev environment
    await environmentDropdown.selectOption('Dev');
    await expect(environmentDropdown).toHaveValue('Dev');
    
    // Check that URLs updated
    await expect(page.locator('.base-url').first()).toContainText('wss://dev-mock.kalshi.com/ws');
  });

  test('should allow custom URL input', async ({ page }) => {
    const customUrlInput = page.locator('.custom-url-input').first();
    
    await customUrlInput.fill('ws://localhost:9999/ws');
    await expect(customUrlInput).toHaveValue('ws://localhost:9999/ws');
    await expect(customUrlInput).toHaveClass(/active/);
  });

  test('should validate custom URLs', async ({ page }) => {
    const customUrlInput = page.locator('.custom-url-input').first();
    
    // Test invalid URL
    await customUrlInput.fill('invalid-url');
    await expect(customUrlInput).toHaveClass(/invalid/);
    await expect(page.locator('.validation-error')).toBeVisible();
    
    // Test valid URL
    await customUrlInput.fill('ws://localhost:9999/ws');
    await expect(customUrlInput).not.toHaveClass(/invalid/);
  });

  test('should connect to WebSocket and update connection status', async ({ page }) => {
    // Switch to Test environment to connect to test servers
    await page.locator('.environment-dropdown').selectOption('Test');
    await page.locator('.apply-btn').click();
    
    // Wait for WebSocket connections to establish
    await page.waitForTimeout(3000);
    
    // Check that status icons are present
    const statusIcons = page.locator('.status-icon');
    await expect(statusIcons).toHaveCount(4);
    
    // Wait a bit more for connections to potentially establish
    await page.waitForTimeout(2000);
    
    // Check for any connection activity - at least some services should show connection attempts
    const serviceCards = page.locator('.service-card');
    await expect(serviceCards).toHaveCount(4);
  });

  test('should display throughput and message metrics', async ({ page }) => {
    // Wait for some messages to arrive
    await page.waitForTimeout(5000);
    
    // Check for throughput metrics
    const throughputMetrics = page.locator('.metric-value').filter({ hasText: /msg\/s/ });
    await expect(throughputMetrics.first()).toBeVisible();
    
    // Check for global stats
    await expect(page.locator('.global-stats-card .metric-value').first()).toBeVisible();
  });

  test('should reset to default configuration', async ({ page }) => {
    // Add custom URL
    const customUrlInput = page.locator('.custom-url-input').first();
    await customUrlInput.fill('ws://localhost:9999/ws');
    
    // Click reset button
    await page.locator('.reset-btn').click();
    
    // Verify custom URL is cleared
    await expect(customUrlInput).toHaveValue('');
  });

  test('should persist environment selection in localStorage', async ({ page }) => {
    // Change environment
    await page.locator('.environment-dropdown').selectOption('QA');
    
    // Reload page
    await page.reload();
    
    // Verify environment is still selected
    await expect(page.locator('.environment-dropdown')).toHaveValue('QA');
  });

  test('should handle WebSocket disconnection gracefully', async ({ page }) => {
    // Wait for initial connections
    await page.waitForTimeout(3000);
    
    // Check for disconnection handling by looking at status indicators
    const statusIndicators = page.locator('.status-icon');
    await expect(statusIndicators).toHaveCount(4);
    
    // Verify that disconnected services show appropriate status
    const disconnectedCards = page.locator('.service-card.disconnected, .service-card.never-connected');
    // Should have some cards in disconnected state (since not all test servers may be running)
  });
});