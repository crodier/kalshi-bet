# Manual Testing Guide for Mock Kalshi Frontend

## Prerequisites
1. Backend server running on http://localhost:8080
2. Frontend dev server running on http://localhost:5173

## Test Steps

### 1. Basic UI Test
- [ ] Open http://localhost:5173 in browser
- [ ] Verify the header shows "Mock Kalshi Trading Platform"
- [ ] Verify the Markets grid is displayed with 4 markets
- [ ] Check that each market shows ticker, name, price, and volume

### 2. Market Selection Test
- [ ] Click on "DUMMY_TEST" market
- [ ] Verify it gets highlighted in blue
- [ ] Verify the Order Book appears for DUMMY_TEST
- [ ] Verify the Order Entry form shows DUMMY_TEST as selected market

### 3. WebSocket Connection Test
- [ ] Open browser Developer Console (F12)
- [ ] Look for "WebSocket connected" message
- [ ] Check for any WebSocket errors

### 4. Order Placement Test
- [ ] With DUMMY_TEST selected, fill in order form:
  - Side: YES
  - Action: Buy
  - Price: 50
  - Quantity: 100
- [ ] Click "BUY YES" button
- [ ] Verify success message appears
- [ ] Check if orderbook updates (may need to refresh due to simplified bid/ask logic)

### 5. Real-time Update Test
- [ ] Open the app in two browser tabs side by side
- [ ] Select DUMMY_TEST market in both tabs
- [ ] Place an order in one tab
- [ ] Verify both tabs show updated orderbook (if WebSocket is working correctly)

### 6. API Integration Test
- [ ] Check browser Network tab for API calls
- [ ] Verify calls to:
  - `/trade-api/v2/orderbook?ticker=DUMMY_TEST`
  - `/trade-api/v2/portfolio/orders` (when placing order)
- [ ] Verify responses are successful (200/201 status)

## Expected Results
- UI loads without errors
- Markets display correctly
- Market selection works
- Order placement succeeds
- WebSocket connects and receives updates
- Multiple tabs stay synchronized

## Known Limitations
1. The orderbook bid/ask separation is simplified (uses 50¢ as threshold)
2. Market data is currently hardcoded (no /markets endpoint)
3. No authentication implemented (uses demo user)
4. MarketEntry component not implemented yet

## Troubleshooting
- If WebSocket fails to connect, check that backend is running
- If orders don't appear to update orderbook, it may be due to the simplified bid/ask logic
- Check browser console for any JavaScript errors