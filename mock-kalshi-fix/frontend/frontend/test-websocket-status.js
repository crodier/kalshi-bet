// Test script to verify WebSocket connection status display
import WebSocketService from './src/services/websocket.js';

// Mock WebSocket for testing
global.WebSocket = class MockWebSocket {
  constructor(url) {
    this.url = url;
    this.readyState = 0; // CONNECTING
    
    // Simulate connection failure after a short delay
    setTimeout(() => {
      this.readyState = 3; // CLOSED
      if (this.onerror) this.onerror(new Error('Connection failed'));
      if (this.onclose) this.onclose();
    }, 100);
  }
  
  send() {}
  close() {}
};

// Test 1: Initial connection failure (never connected)
console.log('Test 1: Initial connection failure');
let states = [];
WebSocketService.onConnectionStatusChange((state) => {
  states.push({ ...state });
  console.log('Connection state:', state);
});

WebSocketService.connect().catch(() => {
  console.log('Connection failed as expected');
  console.log('Final states:', states);
  
  // Verify we show "Disconnected" (not "was connected")
  const lastState = states[states.length - 1];
  console.assert(!lastState.wasConnected, 'Should NOT have wasConnected=true on initial failure');
  console.assert(!lastState.isConnected, 'Should NOT be connected');
  console.assert(!lastState.isConnecting, 'Should NOT be connecting');
  
  // Test 2: Connection established then lost
  console.log('\nTest 2: Connection established then lost');
  
  // Mock successful connection
  global.WebSocket = class MockWebSocket2 {
    constructor(url) {
      this.url = url;
      this.readyState = 0;
      
      // Simulate successful connection
      setTimeout(() => {
        this.readyState = 1; // OPEN
        if (this.onopen) this.onopen();
        
        // Then simulate disconnection after 200ms
        setTimeout(() => {
          this.readyState = 3; // CLOSED
          if (this.onclose) this.onclose();
        }, 200);
      }, 100);
    }
    
    send() {}
    close() {}
  };
  
  // Reset state tracking
  states = [];
  WebSocketService.connectionState = {
    isConnected: false,
    isConnecting: false,
    error: null,
    wasConnected: false
  };
  
  WebSocketService.connect().then(() => {
    console.log('Connected successfully');
    
    // Wait for disconnection
    setTimeout(() => {
      console.log('Final states after disconnect:', states);
      
      // Find the disconnected state
      const disconnectedState = states.find(s => !s.isConnected && s.wasConnected);
      console.assert(disconnectedState, 'Should have a state with wasConnected=true after disconnection');
      console.assert(disconnectedState.wasConnected === true, 'wasConnected should be true');
      console.assert(disconnectedState.isConnected === false, 'isConnected should be false');
      
      console.log('\nAll tests passed!');
      process.exit(0);
    }, 400);
  });
});