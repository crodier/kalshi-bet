package com.kalshi.marketdata.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.net.URI;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KalshiWebSocketClientTest {

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @Mock
    private com.kalshi.marketdata.service.OrderBookManager orderBookManager;

    private ObjectMapper objectMapper;
    private KalshiWebSocketClient webSocketClient;
    private final String kafkaTopic = "test-topic";

    @BeforeEach
    void setUp() throws Exception {
        objectMapper = new ObjectMapper();
        URI uri = new URI("ws://localhost:9090/test");
        webSocketClient = new KalshiWebSocketClient(uri, kafkaTemplate, objectMapper, orderBookManager, kafkaTopic);
    }

    @Test
    void testOnMessageCreatesEnvelopeWithTimestamps() throws Exception {
        // Given
        String testMessage = "{\"channel\":\"ticker_v2\",\"market_ticker\":\"TEST-MARKET\",\"seq\":12345,\"data\":{\"price\":50}}";
        CompletableFuture<SendResult<String, String>> future = CompletableFuture.completedFuture(null);
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenReturn(future);
        when(orderBookManager.shouldPublishMessage(any())).thenReturn(true);

        // When
        long beforeTimestamp = System.currentTimeMillis();
        webSocketClient.onMessage(testMessage);
        long afterTimestamp = System.currentTimeMillis();

        // Then
        ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);

        verify(kafkaTemplate).send(topicCaptor.capture(), keyCaptor.capture(), valueCaptor.capture());

        assertEquals(kafkaTopic, topicCaptor.getValue());
        assertEquals("TEST-MARKET", keyCaptor.getValue());

        Map<String, Object> envelope = objectMapper.readValue(valueCaptor.getValue(), Map.class);
        
        // Verify envelope structure
        assertNotNull(envelope.get("payload"));
        assertNotNull(envelope.get("receivedTimestamp"));
        assertNotNull(envelope.get("publishedTimestamp"));
        assertEquals("ticker_v2", envelope.get("channel"));
        assertEquals("TEST-MARKET", envelope.get("marketTicker"));
        assertEquals(12345, envelope.get("sequence"));
        assertEquals("kalshi-websocket", envelope.get("source"));
        assertEquals(1, envelope.get("version"));

        // Verify timestamps are reasonable
        long receivedTs = ((Number) envelope.get("receivedTimestamp")).longValue();
        long publishedTs = ((Number) envelope.get("publishedTimestamp")).longValue();
        
        assertTrue(receivedTs >= beforeTimestamp);
        assertTrue(receivedTs <= afterTimestamp);
        assertTrue(publishedTs >= receivedTs);
        assertTrue(publishedTs <= afterTimestamp);
    }

    @Test
    void testOnMessageWithoutMarketTicker() throws Exception {
        // Given
        String testMessage = "{\"channel\":\"market_lifecycle_v2\",\"data\":{\"event\":\"opened\"}}";
        CompletableFuture<SendResult<String, String>> future = CompletableFuture.completedFuture(null);
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenReturn(future);
        when(orderBookManager.shouldPublishMessage(any())).thenReturn(true);

        // When
        webSocketClient.onMessage(testMessage);

        // Then
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(eq(kafkaTopic), keyCaptor.capture(), anyString());
        
        assertEquals("all-markets", keyCaptor.getValue());
    }

    @Test
    void testSubscribeToAllMarkets() throws Exception {
        // Given
        webSocketClient = spy(webSocketClient);
        doNothing().when(webSocketClient).send(anyString());

        // When
        webSocketClient.subscribeToAllMarkets();

        // Then
        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(webSocketClient).send(messageCaptor.capture());

        Map<String, Object> sentMessage = objectMapper.readValue(messageCaptor.getValue(), Map.class);
        assertEquals("subscribe", sentMessage.get("cmd"));
        
        Map<String, Object> params = (Map<String, Object>) sentMessage.get("params");
        assertNotNull(params.get("channels"));
        assertTrue(params.get("channels").toString().contains("ticker_v2"));
        assertTrue(params.get("channels").toString().contains("trade"));
        assertTrue(params.get("channels").toString().contains("market_lifecycle_v2"));
    }

    @Test
    void testOnMessageHandlesException() {
        // Given
        String invalidMessage = "not-valid-json";
        
        // When/Then - should not throw
        assertDoesNotThrow(() -> webSocketClient.onMessage(invalidMessage));
        
        // Verify no kafka send was attempted
        verify(kafkaTemplate, never()).send(anyString(), anyString(), anyString());
        verify(orderBookManager, never()).shouldPublishMessage(any());
    }
}