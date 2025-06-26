package com.kalshi.mock.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kalshi.mock.controller.OrderController;
import com.kalshi.mock.dto.KalshiOrderRequest;
import com.kalshi.mock.websocket.dto.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, 
    properties = {"fix.enabled=false"})
public class MarketDataWebSocketIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private OrderController orderController;

    @Autowired
    private ObjectMapper objectMapper;

    private WebSocketStompClient stompClient;
    private String wsUrl;
    private BlockingQueue<WebSocketMessage> receivedMessages;

    @BeforeEach
    void setup() {
        wsUrl = "ws://localhost:" + port + "/ws";
        
        StandardWebSocketClient webSocketClient = new StandardWebSocketClient();
        stompClient = new WebSocketStompClient(webSocketClient);
        stompClient.setMessageConverter(new MappingJackson2MessageConverter());
        
        receivedMessages = new LinkedBlockingQueue<>();
    }

    @Test
    void testTickerUpdateOnTrade() throws Exception {
        String marketTicker = "DUMMY_TEST";
        
        // Connect to WebSocket
        StompSession session = stompClient.connectAsync(wsUrl, new WebSocketHttpHeaders(), 
            new TestStompSessionHandler()).get(5, TimeUnit.SECONDS);
        
        assertTrue(session.isConnected());
        
        // Subscribe to ticker channel
        Map<String, Object> subscribeParams = new HashMap<>();
        subscribeParams.put("cmd", "subscribe");
        subscribeParams.put("id", 1);
        
        Map<String, Object> params = new HashMap<>();
        params.put("channels", new String[]{"ticker"});
        params.put("market_tickers", new String[]{marketTicker});
        subscribeParams.put("params", params);
        
        session.send("/app/subscribe", subscribeParams);
        
        // Wait a bit for subscription to be established
        Thread.sleep(1000);
        
        // Create two orders that will match and create a trade
        KalshiOrderRequest buyOrder = new KalshiOrderRequest();
        buyOrder.setMarketTicker(marketTicker);
        buyOrder.setSide("yes");
        buyOrder.setAction("buy");
        buyOrder.setType("limit");
        buyOrder.setPrice(55);
        buyOrder.setCount(10);
        
        KalshiOrderRequest sellOrder = new KalshiOrderRequest();
        sellOrder.setMarketTicker(marketTicker);
        sellOrder.setSide("yes");
        sellOrder.setAction("sell");
        sellOrder.setType("limit");
        sellOrder.setPrice(55);
        sellOrder.setCount(10);
        
        // Place buy order
        orderController.createOrder(buyOrder, "USER-TEST-001");
        
        // Place sell order (this should trigger a trade)
        orderController.createOrder(sellOrder, "USER-TEST-002");
        
        // Wait for ticker update message
        WebSocketMessage tickerMessage = receivedMessages.poll(5, TimeUnit.SECONDS);
        assertNotNull(tickerMessage, "Should receive ticker update");
        assertEquals("ticker", tickerMessage.getType());
        
        // Verify ticker data
        Map<String, Object> tickerData = objectMapper.convertValue(tickerMessage.getMsg(), Map.class);
        assertEquals(marketTicker, tickerData.get("marketTicker"));
        assertEquals(55, tickerData.get("lastPrice"));
        assertEquals(10, tickerData.get("volume"));
        
        // Cleanup
        session.disconnect();
    }

    @Test
    void testOrderBookUpdateOnOrderPlacement() throws Exception {
        String marketTicker = "DUMMY_TEST";
        
        // Connect to WebSocket
        StompSession session = stompClient.connectAsync(wsUrl, new WebSocketHttpHeaders(), 
            new TestStompSessionHandler()).get(5, TimeUnit.SECONDS);
        
        assertTrue(session.isConnected());
        
        // Subscribe to orderbook channels
        Map<String, Object> subscribeParams = new HashMap<>();
        subscribeParams.put("cmd", "subscribe");
        subscribeParams.put("id", 2);
        
        Map<String, Object> params = new HashMap<>();
        params.put("channels", new String[]{"orderbook_snapshot", "orderbook_delta"});
        params.put("market_tickers", new String[]{marketTicker});
        subscribeParams.put("params", params);
        
        session.send("/app/subscribe", subscribeParams);
        
        // Wait for initial snapshot
        Thread.sleep(1000);
        
        // Clear any initial messages
        receivedMessages.clear();
        
        // Place a new order
        KalshiOrderRequest order = new KalshiOrderRequest();
        order.setMarketTicker(marketTicker);
        order.setSide("yes");
        order.setAction("buy");
        order.setType("limit");
        order.setPrice(45);
        order.setCount(25);
        
        orderController.createOrder(order, "USER-TEST-003");
        
        // Should receive either a delta or snapshot update
        WebSocketMessage updateMessage = receivedMessages.poll(5, TimeUnit.SECONDS);
        assertNotNull(updateMessage, "Should receive orderbook update");
        assertTrue(updateMessage.getType().startsWith("orderbook"), 
            "Should be an orderbook message");
        
        // Cleanup
        session.disconnect();
    }

    @Test
    void testOrderBookLoadsExistingOrdersOnStartup() throws Exception {
        String marketTicker = "DUMMY_TEST";
        
        // Connect to WebSocket
        StompSession session = stompClient.connectAsync(wsUrl, new WebSocketHttpHeaders(), 
            new TestStompSessionHandler()).get(5, TimeUnit.SECONDS);
        
        assertTrue(session.isConnected());
        
        // Subscribe to orderbook snapshot
        Map<String, Object> subscribeParams = new HashMap<>();
        subscribeParams.put("cmd", "subscribe");
        subscribeParams.put("id", 3);
        
        Map<String, Object> params = new HashMap<>();
        params.put("channels", new String[]{"orderbook_snapshot"});
        params.put("market_tickers", new String[]{marketTicker});
        subscribeParams.put("params", params);
        
        session.send("/app/subscribe", subscribeParams);
        
        // Should receive initial snapshot with existing orders
        WebSocketMessage snapshotMessage = receivedMessages.poll(5, TimeUnit.SECONDS);
        assertNotNull(snapshotMessage, "Should receive orderbook snapshot");
        assertEquals("orderbook_snapshot", snapshotMessage.getType());
        
        // Verify snapshot contains data
        Map<String, Object> orderbookData = objectMapper.convertValue(snapshotMessage.getMsg(), Map.class);
        assertEquals(marketTicker, orderbookData.get("marketTicker"));
        assertNotNull(orderbookData.get("yes"), "Should have YES side orderbook");
        
        // Cleanup
        session.disconnect();
    }

    private class TestStompSessionHandler extends StompSessionHandlerAdapter {
        @Override
        public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
            // Subscribe to receive all messages
            session.subscribe("/user/queue/messages", new StompFrameHandler() {
                @Override
                public Type getPayloadType(StompHeaders headers) {
                    return WebSocketMessage.class;
                }

                @Override
                public void handleFrame(StompHeaders headers, Object payload) {
                    if (payload instanceof WebSocketMessage) {
                        receivedMessages.offer((WebSocketMessage) payload);
                    }
                }
            });
        }

        @Override
        public void handleException(StompSession session, StompCommand command, 
                                   StompHeaders headers, byte[] payload, Throwable exception) {
            exception.printStackTrace();
        }
    }
}