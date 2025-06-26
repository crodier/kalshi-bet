package com.kalshi.orderbook.service;

import com.fbg.api.kalshi.MarketDataEnvelope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class MarketDataKafkaConsumer {
    
    private final MarketDataProcessor marketDataProcessor;
    
    @KafkaListener(
        topics = "${kafka.topic.market-data:market-data-updates}",
        groupId = "${spring.kafka.consumer.group-id:order-book-rebuilder}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeMarketData(MarketDataEnvelope envelope) {
        log.debug("Received market data for ticker: {} channel: {}", 
            envelope.getMarketTicker(), envelope.getChannel());
        
        marketDataProcessor.processMarketData(envelope);
    }
}