package com.kalshi.marketdata.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class MarketDiscoveryService {
    
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    @Value("${mock.kalshi.rest.url}")
    private String mockKalshiRestUrl;
    
    /**
     * Fetches all available market tickers from the mock Kalshi server
     */
    public List<String> getAllMarketTickers() {
        List<String> allTickers = new ArrayList<>();
        String cursor = null;
        int totalMarkets = 0;
        
        try {
            do {
                String url = mockKalshiRestUrl + "/trade-api/v2/markets?limit=100";
                if (cursor != null) {
                    url += "&cursor=" + cursor;
                }
                
                log.debug("Fetching markets from: {}", url);
                String response = restTemplate.getForObject(url, String.class);
                
                JsonNode root = objectMapper.readTree(response);
                JsonNode markets = root.get("markets");
                
                if (markets != null && markets.isArray()) {
                    for (JsonNode market : markets) {
                        String ticker = market.get("ticker").asText();
                        allTickers.add(ticker);
                    }
                    totalMarkets += markets.size();
                }
                
                // Check for next cursor
                JsonNode nextCursorNode = root.get("cursor");
                cursor = (nextCursorNode != null && !nextCursorNode.isNull()) ? nextCursorNode.asText() : null;
                
            } while (cursor != null);
            
            log.info("Discovered {} markets from mock Kalshi server", totalMarkets);
            
        } catch (Exception e) {
            log.error("Error fetching market tickers", e);
        }
        
        return allTickers;
    }
}