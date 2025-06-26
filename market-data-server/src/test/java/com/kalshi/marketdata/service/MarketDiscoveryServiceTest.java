package com.kalshi.marketdata.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MarketDiscoveryServiceTest {

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private MarketDiscoveryService marketDiscoveryService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(marketDiscoveryService, "mockKalshiRestUrl", "http://localhost:9090");
        ReflectionTestUtils.setField(marketDiscoveryService, "restTemplate", restTemplate);
    }

    @Test
    void testGetAllMarketTickersSinglePage() {
        // Given
        String mockResponse = """
            {
                "markets": [
                    {"ticker": "MARKET1", "event_ticker": "EVENT1"},
                    {"ticker": "MARKET2", "event_ticker": "EVENT1"},
                    {"ticker": "MARKET3", "event_ticker": "EVENT2"}
                ],
                "cursor": null
            }
            """;
        
        when(restTemplate.getForObject(anyString(), eq(String.class))).thenReturn(mockResponse);

        // When
        List<String> tickers = marketDiscoveryService.getAllMarketTickers();

        // Then
        assertEquals(3, tickers.size());
        assertTrue(tickers.contains("MARKET1"));
        assertTrue(tickers.contains("MARKET2"));
        assertTrue(tickers.contains("MARKET3"));
        
        verify(restTemplate, times(1)).getForObject(anyString(), eq(String.class));
    }

    @Test
    void testGetAllMarketTickersMultiplePages() {
        // Given
        String firstPageResponse = """
            {
                "markets": [
                    {"ticker": "MARKET1", "event_ticker": "EVENT1"},
                    {"ticker": "MARKET2", "event_ticker": "EVENT1"}
                ],
                "cursor": "next-page-cursor"
            }
            """;
            
        String secondPageResponse = """
            {
                "markets": [
                    {"ticker": "MARKET3", "event_ticker": "EVENT2"}
                ],
                "cursor": null
            }
            """;
        
        when(restTemplate.getForObject(contains("limit=100"), eq(String.class)))
            .thenReturn(firstPageResponse);
        when(restTemplate.getForObject(contains("cursor=next-page-cursor"), eq(String.class)))
            .thenReturn(secondPageResponse);

        // When
        List<String> tickers = marketDiscoveryService.getAllMarketTickers();

        // Then
        assertEquals(3, tickers.size());
        assertTrue(tickers.contains("MARKET1"));
        assertTrue(tickers.contains("MARKET2"));
        assertTrue(tickers.contains("MARKET3"));
        
        verify(restTemplate, times(2)).getForObject(anyString(), eq(String.class));
    }

    @Test
    void testGetAllMarketTickersHandlesError() {
        // Given
        when(restTemplate.getForObject(anyString(), eq(String.class)))
            .thenThrow(new RuntimeException("Connection error"));

        // When
        List<String> tickers = marketDiscoveryService.getAllMarketTickers();

        // Then
        assertNotNull(tickers);
        assertTrue(tickers.isEmpty());
    }

    @Test
    void testGetAllMarketTickersHandlesEmptyResponse() {
        // Given
        String emptyResponse = """
            {
                "markets": [],
                "cursor": null
            }
            """;
        
        when(restTemplate.getForObject(anyString(), eq(String.class))).thenReturn(emptyResponse);

        // When
        List<String> tickers = marketDiscoveryService.getAllMarketTickers();

        // Then
        assertNotNull(tickers);
        assertTrue(tickers.isEmpty());
    }
}