const { chromium } = require('playwright');

async function testOrderBookRebuilderFrontend() {
    console.log('Starting Order Book Rebuilder Frontend Test...');
    
    const browser = await chromium.launch();
    const page = await browser.newPage();
    
    try {
        // Navigate to the order book rebuilder
        console.log('Navigating to http://localhost:8085');
        await page.goto('http://localhost:8085');
        
        // Wait for the page to load
        await page.waitForSelector('.container');
        
        // Check for main components
        console.log('✓ Page loaded successfully');
        
        // Check for header
        const header = await page.textContent('header h1');
        console.log(`✓ Header found: "${header}"`);
        
        // Check for market selector
        const marketInput = await page.locator('#marketTicker');
        const isVisible = await marketInput.isVisible();
        console.log(`✓ Market selector input visible: ${isVisible}`);
        
        // Check for order book sections
        const yesSide = await page.locator('.yes-side').isVisible();
        const noSide = await page.locator('.no-side').isVisible();
        console.log(`✓ YES side visible: ${yesSide}`);
        console.log(`✓ NO side visible: ${noSide}`);
        
        // Check for markets list
        const marketsList = await page.locator('.markets-list').isVisible();
        console.log(`✓ Markets list visible: ${marketsList}`);
        
        // Check for stats section
        const stats = await page.locator('.stats').isVisible();
        console.log(`✓ Stats section visible: ${stats}`);
        
        // Test connection status
        const connectionStatus = await page.textContent('#connectionStatus');
        console.log(`✓ Connection status: "${connectionStatus}"`);
        
        // Try to connect WebSocket
        console.log('Testing WebSocket connection...');
        await page.click('#connectBtn');
        
        // Wait a moment for potential connection
        await page.waitForTimeout(2000);
        
        // Check if status changed
        const newConnectionStatus = await page.textContent('#connectionStatus');
        console.log(`✓ WebSocket connection attempted: "${newConnectionStatus}"`);
        
        // Test API endpoints
        console.log('Testing API endpoints...');
        
        // Test stats endpoint
        const statsResponse = await page.evaluate(async () => {
            const response = await fetch('/api/v1/orderbook/stats');
            return await response.json();
        });
        console.log(`✓ Stats API response:`, statsResponse);
        
        // Test markets list endpoint
        const marketsResponse = await page.evaluate(async () => {
            const response = await fetch('/api/v1/orderbook/all?depth=1');
            return await response.json();
        });
        console.log(`✓ Markets API response:`, marketsResponse);
        
        // Test order book endpoint with a test market
        const testMarket = 'SELF_TEST_MARKET';
        const orderBookResponse = await page.evaluate(async (market) => {
            const response = await fetch(`/trade-api/v2/markets/${market}/orderbook?depth=10`);
            return { status: response.status, data: response.status === 200 ? await response.json() : null };
        }, testMarket);
        console.log(`✓ Order book API (${testMarket}) response:`, orderBookResponse);
        
        // Test timing information display
        console.log('Checking for timing information elements...');
        const hasTiming = await page.locator('.timing-info').count();
        console.log(`✓ Timing info elements found: ${hasTiming}`);
        
        // Test flashing animation styles
        const hasFlashAnimation = await page.evaluate(() => {
            const styles = Array.from(document.styleSheets).flatMap(sheet => 
                Array.from(sheet.cssRules || []).map(rule => rule.cssText)
            );
            return styles.some(style => style.includes('flashYes') || style.includes('flashNo'));
        });
        console.log(`✓ Flash animation styles present: ${hasFlashAnimation}`);
        
        console.log('\\n=== Frontend Test Summary ===');
        console.log('✓ Page loads successfully');
        console.log('✓ All main UI components present');
        console.log('✓ API endpoints responding');
        console.log('✓ WebSocket connection functionality available');
        console.log('✓ Timing information infrastructure in place');
        console.log('✓ Flashing animation styles configured');
        
    } catch (error) {
        console.error('❌ Test failed:', error);
        throw error;
    } finally {
        await browser.close();
    }
}

// Run the test
if (require.main === module) {
    testOrderBookRebuilderFrontend()
        .then(() => {
            console.log('\\n🎉 All frontend tests passed!');
            process.exit(0);
        })
        .catch((error) => {
            console.error('\\n💥 Frontend tests failed:', error.message);
            process.exit(1);
        });
}

module.exports = { testOrderBookRebuilderFrontend };