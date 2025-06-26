package com.kalshi.orderbook.service;

import com.kalshi.orderbook.test.TestDataGenerator;
import com.fbg.api.kalshi.MarketDataEnvelope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.Random;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "test.data.simulator.enabled", havingValue = "true")
public class TestDataSimulator {
    
    private final MarketDataProcessor marketDataProcessor;
    private final Random random = new Random();
    
    private final List<String> testMarkets = List.of(
        "MARKET_MAKER",
        "TEST_MARKET_1",
        "TEST_MARKET_2",
        "TEST_MARKET_3",
        "BTCUSD-2024",
        "ETHUSD-2024"
    );
    
    @PostConstruct
    public void init() {
        log.info("Test data simulator enabled - sending initial snapshots");
        
        // Send initial snapshots for all test markets
        for (String market : testMarkets) {
            MarketDataEnvelope snapshot = TestDataGenerator.createSnapshot(market, 5, 5);
            marketDataProcessor.processMarketData(snapshot);
        }
    }
    
    @Scheduled(fixedDelayString = "${test.data.simulator.update-interval-ms:1000}")
    public void sendRandomUpdate() {
        // Pick a random market
        String market = testMarkets.get(random.nextInt(testMarkets.size()));
        
        // 80% chance of delta, 20% chance of new snapshot
        MarketDataEnvelope envelope;
        if (random.nextDouble() < 0.8) {
            envelope = TestDataGenerator.createDelta(market, 
                random.nextInt(3) + 1, 
                random.nextInt(3) + 1);
        } else {
            envelope = TestDataGenerator.createSnapshot(market, 5, 5);
        }
        
        log.debug("Sending test update for market: {} type: {}", market, envelope.getChannel());
        marketDataProcessor.processMarketData(envelope);
    }
}