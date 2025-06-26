import { test, expect } from '@playwright/test';

test.describe('Market Cross Orders and Execution Display', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('http://localhost:5173');
    await page.waitForSelector('.app', { timeout: 10000 });
  });

  test('should update orderbook when market is selected', async ({ page }) => {
    // Select MARKET_MAKER
    const marketRow = page.locator('.grid-row').filter({ hasText: 'MARKET_MAKER' });
    await marketRow.click();
    
    // Wait for orderbook to load
    await page.waitForSelector('.orderbook-section .orderbook', { timeout: 10000 });
    
    // Verify orderbook has content
    const yesLevels = page.locator('.orderbook-side.bids .level');
    const noLevels = page.locator('.orderbook-side.asks .level');
    
    await expect(yesLevels.first()).toBeVisible();
    await expect(noLevels.first()).toBeVisible();
    
    // Check that we have real orderbook data
    const yesLevelCount = await yesLevels.count();
    const noLevelCount = await noLevels.count();
    
    expect(yesLevelCount).toBeGreaterThan(0);
    expect(noLevelCount).toBeGreaterThan(0);
    
    console.log(`✅ Orderbook loaded with ${yesLevelCount} YES levels and ${noLevelCount} NO levels`);
  });

  test('should show crossing prices in EventOrderTicket when market selected', async ({ page }) => {
    // Select MARKET_MAKER
    const marketRow = page.locator('.grid-row').filter({ hasText: 'MARKET_MAKER' });
    await marketRow.click();
    
    // Debug: Check what's actually on the page
    await page.waitForTimeout(2000);
    const allElements = await page.locator('body').innerHTML();
    console.log('Page content after clicking market:', allElements.substring(0, 1000));
    
    // Check if the order entry section exists
    const orderEntrySection = page.locator('.order-entry-section');
    const isVisible = await orderEntrySection.isVisible();
    console.log('Order entry section visible:', isVisible);
    
    // Try a more flexible selector
    const orderTicket = page.locator('[class*="order-ticket"], .event-order-ticket');
    
    try {
      await orderTicket.waitFor({ timeout: 5000 });
      console.log('Order ticket found');
    } catch (e) {
      console.log('Order ticket not found, taking screenshot');
      await page.screenshot({ path: 'debug-no-order-ticket.png' });
      throw e;
    }
    
    await page.waitForTimeout(3000); // Give time for WebSocket data
    
    // Check that crossing prices are displayed
    const yesCrossButton = page.locator('.side-button.yes');
    const noCrossButton = page.locator('.side-button.no');
    
    await expect(yesCrossButton).toBeVisible();
    await expect(noCrossButton).toBeVisible();
    
    // Get the crossing prices
    const yesPriceText = await yesCrossButton.locator('.side-price').textContent();
    const noPriceText = await noCrossButton.locator('.side-price').textContent();
    
    console.log(`YES crossing price: ${yesPriceText}`);
    console.log(`NO crossing price: ${noPriceText}`);
    
    // Prices should not be "--¢" (empty)
    expect(yesPriceText).not.toBe('--¢');
    expect(noPriceText).not.toBe('--¢');
    
    // Verify crossing info is shown
    const yesCrossingInfo = await yesCrossButton.locator('.crossing-info').textContent();
    const noCrossingInfo = await noCrossButton.locator('.crossing-info').textContent();
    
    console.log(`YES crossing info: ${yesCrossingInfo}`);
    console.log(`NO crossing info: ${noCrossingInfo}`);
    
    expect(yesCrossingInfo).toContain('Crosses NO @');
    expect(noCrossingInfo).toContain('Crosses YES @');
    
    console.log('✅ Crossing prices and info displayed correctly');
  });

  test('should place a cross order and show execution', async ({ page }) => {
    // Select MARKET_MAKER
    const marketRow = page.locator('.grid-row').filter({ hasText: 'MARKET_MAKER' });
    await marketRow.click();
    
    // Wait for order ticket to load
    await page.waitForSelector('.event-order-ticket', { timeout: 10000 });
    await page.waitForTimeout(3000);
    
    // Select YES side
    const yesCrossButton = page.locator('.side-button.yes');
    await yesCrossButton.click();
    
    // Get the crossing price
    const yesPriceText = await yesCrossButton.locator('.side-price').textContent();
    const crossingPrice = parseInt(yesPriceText.replace('¢', ''));
    
    console.log(`Placing cross order at ${crossingPrice}¢`);
    
    // Enter the crossing price in the limit price field
    const priceInput = page.locator('.price-input');
    await priceInput.fill(crossingPrice.toString());
    
    // Set quantity to 1
    const quantityInput = page.locator('.quantity-input');
    await quantityInput.fill('1');
    
    // Submit the order
    const submitButton = page.locator('.submit-button');
    await submitButton.click();
    
    // Wait for success message
    await page.waitForSelector('.message.success', { timeout: 10000 });
    const successMessage = await page.locator('.message.success').textContent();
    console.log(`Order result: ${successMessage}`);
    
    // Wait a bit for the execution to process
    await page.waitForTimeout(3000);
    
    // Check if Last Execution column was updated
    const lastExecutionCell = marketRow.locator('.last-execution');
    const executionText = await lastExecutionCell.textContent();
    
    console.log(`Last Execution: ${executionText}`);
    
    // Should show some execution data, not just "-"
    const hasExecution = executionText && executionText !== '-' && executionText.trim() !== '';
    
    if (hasExecution) {
      console.log('✅ Last Execution column updated after cross order');
      // Should contain price and size
      expect(executionText).toMatch(/\d+¢/); // price
      expect(executionText).toMatch(/×/); // multiplication symbol
      expect(executionText).toMatch(/\d{2}:\d{2}:\d{2}/); // timestamp
    } else {
      console.log('❌ Last Execution column not updated after cross order');
      // Take a screenshot for debugging
      await page.screenshot({ path: 'no-execution-displayed.png' });
    }
    
    expect(hasExecution).toBeTruthy();
  });

  test('should update orderbook in real-time when market is selected', async ({ page }) => {
    // Select MARKET_MAKER
    const marketRow = page.locator('.grid-row').filter({ hasText: 'MARKET_MAKER' });
    await marketRow.click();
    
    // Wait for orderbook to load
    await page.waitForSelector('.orderbook-section .orderbook', { timeout: 10000 });
    await page.waitForTimeout(2000);
    
    // Get initial orderbook state
    const initialYesLevels = await page.locator('.orderbook-side.bids .level .price').allTextContents();
    const initialNoLevels = await page.locator('.orderbook-side.asks .level .price').allTextContents();
    
    console.log(`Initial YES levels: ${initialYesLevels.join(', ')}`);
    console.log(`Initial NO levels: ${initialNoLevels.join(', ')}`);
    
    // Wait for orderbook updates (market maker should be moving prices)
    let orderbookChanged = false;
    let attempts = 0;
    const maxAttempts = 10;
    
    while (!orderbookChanged && attempts < maxAttempts) {
      await page.waitForTimeout(2000);
      attempts++;
      
      const currentYesLevels = await page.locator('.orderbook-side.bids .level .price').allTextContents();
      const currentNoLevels = await page.locator('.orderbook-side.asks .level .price').allTextContents();
      
      // Check if orderbook has changed
      const yesChanged = JSON.stringify(initialYesLevels) !== JSON.stringify(currentYesLevels);
      const noChanged = JSON.stringify(initialNoLevels) !== JSON.stringify(currentNoLevels);
      
      if (yesChanged || noChanged) {
        orderbookChanged = true;
        console.log(`✅ Orderbook updated after ${attempts * 2} seconds`);
        console.log(`New YES levels: ${currentYesLevels.join(', ')}`);
        console.log(`New NO levels: ${currentNoLevels.join(', ')}`);
      }
    }
    
    if (!orderbookChanged) {
      console.log('❌ Orderbook did not update during monitoring period');
      // Take screenshot for debugging
      await page.screenshot({ path: 'orderbook-not-updating.png' });
    }
    
    expect(orderbookChanged).toBeTruthy();
  });

  test('should show flash animations when crossing orders', async ({ page }) => {
    // Select MARKET_MAKER
    const marketRow = page.locator('.grid-row').filter({ hasText: 'MARKET_MAKER' });
    await marketRow.click();
    
    // Wait for order ticket to load
    await page.waitForSelector('.event-order-ticket', { timeout: 10000 });
    await page.waitForTimeout(3000);
    
    // Place a cross order
    const yesCrossButton = page.locator('.side-button.yes');
    await yesCrossButton.click();
    
    const yesPriceText = await yesCrossButton.locator('.side-price').textContent();
    const crossingPrice = parseInt(yesPriceText.replace('¢', ''));
    
    await page.locator('.price-input').fill(crossingPrice.toString());
    await page.locator('.quantity-input').fill('1');
    
    // Monitor for flash animations during order submission
    let flashSeen = false;
    
    // Start monitoring for flashes
    const checkForFlash = async () => {
      const flashElements = await page.locator('[class*="flash"]').count();
      if (flashElements > 0) {
        flashSeen = true;
        console.log('✅ Flash animation detected');
      }
    };
    
    // Submit order and monitor flashes
    const submitButton = page.locator('.submit-button');
    await submitButton.click();
    
    // Check for flashes multiple times
    for (let i = 0; i < 10; i++) {
      await checkForFlash();
      await page.waitForTimeout(500);
      if (flashSeen) break;
    }
    
    // Wait for success message
    await page.waitForSelector('.message.success', { timeout: 10000 });
    
    // Continue monitoring after order completion
    for (let i = 0; i < 5; i++) {
      await checkForFlash();
      await page.waitForTimeout(1000);
      if (flashSeen) break;
    }
    
    expect(flashSeen).toBeTruthy();
  });

  test('should show trade executions in WebSocket messages', async ({ page }) => {
    // Monitor WebSocket messages
    const wsMessages = [];
    
    page.on('websocket', ws => {
      ws.on('framereceived', event => {
        try {
          const message = JSON.parse(event.payload);
          if (message.type === 'trade' || message.type === 'execution') {
            wsMessages.push(message);
            console.log('Trade/Execution WebSocket message:', message);
          }
        } catch (e) {
          // Ignore non-JSON messages
        }
      });
    });
    
    // Select market and place cross order
    const marketRow = page.locator('.grid-row').filter({ hasText: 'MARKET_MAKER' });
    await marketRow.click();
    
    await page.waitForSelector('.event-order-ticket', { timeout: 10000 });
    await page.waitForTimeout(3000);
    
    const yesCrossButton = page.locator('.side-button.yes');
    await yesCrossButton.click();
    
    const yesPriceText = await yesCrossButton.locator('.side-price').textContent();
    const crossingPrice = parseInt(yesPriceText.replace('¢', ''));
    
    await page.locator('.price-input').fill(crossingPrice.toString());
    await page.locator('.quantity-input').fill('1');
    await page.locator('.submit-button').click();
    
    // Wait for success and then check for trade messages
    await page.waitForSelector('.message.success', { timeout: 10000 });
    await page.waitForTimeout(5000); // Give time for WebSocket messages
    
    console.log(`Received ${wsMessages.length} trade/execution WebSocket messages`);
    
    if (wsMessages.length > 0) {
      console.log('✅ Trade/execution WebSocket messages received');
      wsMessages.forEach((msg, i) => {
        console.log(`Message ${i + 1}:`, msg);
      });
    } else {
      console.log('❌ No trade/execution WebSocket messages received');
    }
    
    expect(wsMessages.length).toBeGreaterThan(0);
  });
});