import { test, expect } from '@playwright/test';

test('debug page structure', async ({ page }) => {
  await page.goto('http://localhost:5173');
  
  // Wait for app to load
  await page.waitForTimeout(3000);
  
  // Take a screenshot
  await page.screenshot({ path: 'debug-screenshot.png', fullPage: true });
  
  // Log all elements
  const allElements = await page.locator('*').all();
  console.log('Total elements:', allElements.length);
  
  // Check what classes exist
  const classes = await page.evaluate(() => {
    const elements = document.querySelectorAll('*');
    const classSet = new Set();
    elements.forEach(el => {
      if (el.className && typeof el.className === 'string') {
        el.className.split(' ').forEach(cls => {
          if (cls.trim()) classSet.add(cls.trim());
        });
      }
    });
    return Array.from(classSet).sort();
  });
  
  console.log('Available classes:', classes);
  
  // Check for market-related content
  const marketContent = await page.textContent('body');
  console.log('Page contains MARKET_MAKER:', marketContent.includes('MARKET_MAKER'));
  
  // Try to find any buttons or clickable elements
  const buttons = await page.locator('button, .clickable, [onclick], a').all();
  console.log('Found clickable elements:', buttons.length);
  
  for (let i = 0; i < Math.min(buttons.length, 5); i++) {
    const text = await buttons[i].textContent();
    console.log(`Button ${i}: "${text}"`);
  }
  
  // Look for any table rows or grid items
  const rows = await page.locator('tr, .row, .grid-row, .market-row').all();
  console.log('Found row elements:', rows.length);
  
  for (let i = 0; i < Math.min(rows.length, 5); i++) {
    const text = await rows[i].textContent();
    console.log(`Row ${i}: "${text}"`);
  }
});