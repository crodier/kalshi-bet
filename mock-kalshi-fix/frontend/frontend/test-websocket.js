import { chromium } from 'playwright';

(async () => {
  const browser = await chromium.launch({ 
    headless: true,
    devtools: true 
  });
  const context = await browser.newContext();
  const page = await context.newPage();

  // Capture console messages
  page.on('console', msg => {
    console.log(`[Browser Console ${msg.type()}]:`, msg.text());
  });

  // Capture page errors
  page.on('pageerror', error => {
    console.error('[Page Error]:', error.message);
  });

  // Monitor WebSocket traffic
  page.on('websocket', ws => {
    console.log(`[WebSocket] Created: ${ws.url()}`);
    
    ws.on('framesent', event => {
      console.log(`[WebSocket] Sent:`, event.payload);
    });
    
    ws.on('framereceived', event => {
      console.log(`[WebSocket] Received:`, event.payload);
    });
    
    ws.on('close', () => {
      console.log(`[WebSocket] Closed`);
    });
    
    ws.on('error', error => {
      console.error(`[WebSocket] Error:`, error);
    });
  });

  // Navigate to the frontend
  console.log('Navigating to frontend...');
  await page.goto('http://localhost:5173', { 
    waitUntil: 'networkidle',
    timeout: 30000 
  });

  // Wait for WebSocket to connect
  console.log('Waiting for WebSocket connection...');
  await page.waitForTimeout(2000);

  // Check if markets are loaded
  const marketCount = await page.evaluate(() => {
    const rows = document.querySelectorAll('.grid-row');
    return rows.length;
  });
  console.log(`Found ${marketCount} markets`);

  // Monitor for 10 seconds
  console.log('Monitoring WebSocket traffic for 10 seconds...');
  await page.waitForTimeout(10000);

  await browser.close();
})();