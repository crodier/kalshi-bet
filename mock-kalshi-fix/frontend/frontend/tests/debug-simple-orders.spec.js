import { test, expect } from '@playwright/test';

test.describe('Debug Simple Orders Panel', () => {
  
  test('should debug app loading', async ({ page }) => {
    // Capture console logs
    const consoleLogs = [];
    page.on('console', msg => {
      consoleLogs.push(`[${msg.type()}] ${msg.text()}`);
    });

    // Capture errors
    const errors = [];
    page.on('pageerror', error => {
      errors.push(error.message);
    });

    // Navigate to the application
    console.log('Navigating to http://localhost:5176');
    await page.goto('http://localhost:5176');
    
    // Wait for any content
    await page.waitForTimeout(3000);
    
    // Check what's on the page
    const title = await page.title();
    console.log('Page title:', title);
    
    const bodyText = await page.locator('body').textContent();
    console.log('Body text length:', bodyText.length);
    console.log('Body text (first 200 chars):', bodyText.substring(0, 200));
    
    // Check for specific elements
    const h1Exists = await page.locator('h1').count();
    console.log('H1 elements found:', h1Exists);
    
    const marketGridExists = await page.locator('.market-grid').count();
    console.log('Market grid elements found:', marketGridExists);
    
    const ordersPanelExists = await page.locator('.orders-panel').count();
    console.log('Orders panel elements found:', ordersPanelExists);
    
    // Check for React root
    const reactRoot = await page.locator('#root').count();
    console.log('React root found:', reactRoot);
    
    // Log all captured information
    console.log('\n=== Console Logs ===');
    consoleLogs.forEach(log => console.log(log));
    
    console.log('\n=== JavaScript Errors ===');
    errors.forEach(error => console.log(error));
    
    // Take a screenshot
    await page.screenshot({ path: 'debug-screenshot.png', fullPage: true });
    console.log('\nScreenshot saved as debug-screenshot.png');
    
    // Check network activity
    const networkLogs = [];
    page.on('response', response => {
      networkLogs.push(`${response.status()} ${response.url()}`);
    });
    
    // Wait a bit more to capture network activity
    await page.waitForTimeout(2000);
    
    console.log('\n=== Network Activity ===');
    networkLogs.forEach(log => console.log(log));
    
    // Check if frontend server is actually running
    try {
      const response = await fetch('http://localhost:5176');
      console.log('\nFrontend server status:', response.status);
    } catch (e) {
      console.log('\nFrontend server error:', e.message);
    }
  });
});