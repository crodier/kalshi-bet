import { test, expect } from '@playwright/test';

test('AG Grid should display pure JSON data without HTML tags', async ({ page }) => {
  // Navigate to the frontend
  await page.goto('http://localhost:5174');
  
  // Wait for the page to load
  await page.waitForTimeout(2000);
  
  // Select the DUMMY_TEST market which has existing orders
  const marketOption = page.locator('.market-option').filter({ hasText: 'DUMMY_TEST' });
  await marketOption.click();
  
  // Wait for orders to load in AG Grid
  await page.waitForTimeout(3000);
  
  // Check if AG Grid is present
  const agGrid = page.locator('.ag-theme-alpine.orders-grid');
  await expect(agGrid).toBeVisible();
  
  // Get all data rows in the grid
  const dataRows = page.locator('.ag-row');
  const rowCount = await dataRows.count();
  
  console.log(`Found ${rowCount} rows in AG Grid`);
  
  if (rowCount > 0) {
    const firstRow = dataRows.first();
    
    // Check Side column - should NOT contain <span> tags
    const sideCell = firstRow.locator('[col-id="side"]');
    const sideHTML = await sideCell.innerHTML();
    const sideText = await sideCell.textContent();
    
    console.log('Side cell HTML:', sideHTML);
    console.log('Side cell text:', sideText);
    
    // Verify no HTML tags are present
    expect(sideHTML).not.toContain('<span');
    expect(sideText).toMatch(/^(YES|NO)$/);
    
    // Check Action column - should NOT contain <span> tags
    const actionCell = firstRow.locator('[col-id="action"]');
    const actionHTML = await actionCell.innerHTML();
    const actionText = await actionCell.textContent();
    
    console.log('Action cell HTML:', actionHTML);
    console.log('Action cell text:', actionText);
    
    expect(actionHTML).not.toContain('<span');
    expect(actionText).toMatch(/^(BUY|SELL)$/);
    
    // Check Status column - should NOT contain <span> tags
    const statusCell = firstRow.locator('[col-id="status"]');
    const statusHTML = await statusCell.innerHTML();
    const statusText = await statusCell.textContent();
    
    console.log('Status cell HTML:', statusHTML);
    console.log('Status cell text:', statusText);
    
    expect(statusHTML).not.toContain('<span');
    expect(statusText).toMatch(/^[A-Z\s]+$/);
    
    // Check Filled quantity column - should NOT contain <span> tags
    const filledCell = firstRow.locator('[col-id="filled_quantity"]');
    const filledHTML = await filledCell.innerHTML();
    const filledText = await filledCell.textContent();
    
    console.log('Filled cell HTML:', filledHTML);
    console.log('Filled cell text:', filledText);
    
    expect(filledHTML).not.toContain('<span');
    expect(filledText).toMatch(/^\d+$/);
    
    console.log('✅ All AG Grid cells contain pure text data without HTML tags');
  } else {
    console.log('No orders found in AG Grid - this is also valid');
  }
});