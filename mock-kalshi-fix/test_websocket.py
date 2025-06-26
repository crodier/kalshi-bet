#!/usr/bin/env python3
import websocket
import json
import time

def on_message(ws, message):
    print("Received:")
    try:
        msg = json.loads(message)
        print(json.dumps(msg, indent=2))
    except:
        print(message)

def on_error(ws, error):
    print(f"Error: {error}")

def on_close(ws, close_status_code, close_msg):
    print("Connection closed")

def on_open(ws):
    print("Connected to WebSocket")
    
    # Subscribe to orderbook_snapshot channel
    subscribe_cmd = {
        "id": 1,
        "cmd": "subscribe", 
        "params": {
            "channels": ["orderbook_snapshot"],
            "market_tickers": ["INXD-23DEC29-B5000", "BTCZ-23DEC31-B50000"]
        }
    }
    
    print("Sending subscribe command...")
    ws.send(json.dumps(subscribe_cmd))

if __name__ == "__main__":
    ws_url = "ws://localhost:9090/trade-api/ws/v2"
    
    ws = websocket.WebSocketApp(ws_url,
                                on_open=on_open,
                                on_message=on_message,
                                on_error=on_error,
                                on_close=on_close)
    
    print(f"Connecting to {ws_url}...")
    ws.run_forever()