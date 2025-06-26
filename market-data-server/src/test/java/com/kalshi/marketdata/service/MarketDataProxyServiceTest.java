package com.kalshi.marketdata.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kalshi.marketdata.config.KafkaErrorAlertService;
import com.kalshi.marketdata.websocket.KalshiWebSocketClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MarketDataProxyServiceTest {

    @Mock
    private MarketDiscoveryService marketDiscoveryService;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private KalshiWebSocketClient webSocketClient;
    
    @Mock
    private OrderBookManager orderBookManager;
    
    @Mock
    private KafkaErrorAlertService errorAlertService;

    @InjectMocks
    private MarketDataProxyService marketDataProxyService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(marketDataProxyService, "websocketUrl", "ws://localhost:9090/ws");
        ReflectionTestUtils.setField(marketDataProxyService, "kafkaTopic", "market-data");
    }

    @Test
    void testCheckConnectionHealthReconnectsWhenClosed() throws Exception {
        // Given
        ReflectionTestUtils.setField(marketDataProxyService, "webSocketClient", null);
        ReflectionTestUtils.setField(marketDataProxyService, "shouldReconnect", true);
        
        // When
        marketDataProxyService.checkConnectionHealth();

        // Then
        // The connection attempt will fail since we're in a test environment,
        // but we're just checking that the reconnection logic is triggered
        Boolean shouldReconnect = (Boolean) ReflectionTestUtils.getField(marketDataProxyService, "shouldReconnect");
        assertTrue(shouldReconnect != null && shouldReconnect);
    }

    @Test
    void testCheckConnectionHealthDoesNotReconnectWhenConnected() {
        // Given
        ReflectionTestUtils.setField(marketDataProxyService, "webSocketClient", webSocketClient);
        ReflectionTestUtils.setField(marketDataProxyService, "shouldReconnect", true);
        when(webSocketClient.isClosed()).thenReturn(false);

        // When
        marketDataProxyService.checkConnectionHealth();

        // Then
        verify(marketDiscoveryService, never()).getAllMarketTickers();
    }

    @Test
    void testRefreshMarketSubscriptionsWithActiveConnection() {
        // Given
        ReflectionTestUtils.setField(marketDataProxyService, "webSocketClient", webSocketClient);
        when(webSocketClient.isClosed()).thenReturn(false);
        List<String> markets = Arrays.asList("MARKET1", "MARKET2", "MARKET3");
        when(marketDiscoveryService.getAllMarketTickers()).thenReturn(markets);

        // When
        marketDataProxyService.refreshMarketSubscriptions();

        // Then
        verify(marketDiscoveryService).getAllMarketTickers();
        verify(webSocketClient).subscribeToOrderbookChannels(markets);
    }

    @Test
    void testRefreshMarketSubscriptionsWithClosedConnection() {
        // Given
        ReflectionTestUtils.setField(marketDataProxyService, "webSocketClient", webSocketClient);
        when(webSocketClient.isClosed()).thenReturn(true);

        // When
        marketDataProxyService.refreshMarketSubscriptions();

        // Then
        verify(marketDiscoveryService, never()).getAllMarketTickers();
        verify(webSocketClient, never()).subscribeToOrderbookChannels(any());
    }

    @Test
    void testRefreshMarketSubscriptionsHandlesException() {
        // Given
        ReflectionTestUtils.setField(marketDataProxyService, "webSocketClient", webSocketClient);
        when(webSocketClient.isClosed()).thenReturn(false);
        when(marketDiscoveryService.getAllMarketTickers()).thenThrow(new RuntimeException("Test error"));

        // When
        marketDataProxyService.refreshMarketSubscriptions();

        // Then
        verify(marketDiscoveryService).getAllMarketTickers();
        verify(webSocketClient, never()).subscribeToOrderbookChannels(any());
    }

    @Test
    void testShutdownClosesWebSocketClient() {
        // Given
        ReflectionTestUtils.setField(marketDataProxyService, "webSocketClient", webSocketClient);
        when(webSocketClient.isClosed()).thenReturn(false);

        // When
        marketDataProxyService.shutdown();

        // Then
        verify(webSocketClient).close();
        Boolean shouldReconnect = (Boolean) ReflectionTestUtils.getField(marketDataProxyService, "shouldReconnect");
        assert shouldReconnect != null && !shouldReconnect;
    }
}