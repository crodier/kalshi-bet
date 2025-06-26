package com.kalshi.mock.service;

import com.kalshi.mock.catalog.service.MarketService;
import com.kalshi.mock.event.OrderBookEvent;
import com.kalshi.mock.event.OrderBookEventListener;
import com.kalshi.mock.event.OrderBookEventPublisher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;

/**
 * Service that listens for order book events and updates market data
 */
@Service
public class MarketDataUpdateService implements OrderBookEventListener {
    
    @Autowired
    private MarketService marketService;
    
    @Autowired
    private OrderBookEventPublisher eventPublisher;
    
    @PostConstruct
    public void init() {
        eventPublisher.addListener(this);
    }
    
    @Override
    public void onOrderBookEvent(OrderBookEvent event) {
        if (event.getType() == OrderBookEvent.EventType.TICKER_UPDATE) {
            handleTickerUpdate(event);
        }
    }
    
    private void handleTickerUpdate(OrderBookEvent event) {
        OrderBookEvent.TickerData tickerData = (OrderBookEvent.TickerData) event.getData();
        
        // Update market prices in database
        BigDecimal lastPrice = BigDecimal.valueOf(tickerData.getLastPrice());
        BigDecimal bestBid = BigDecimal.valueOf(tickerData.getBestBid());
        BigDecimal bestAsk = BigDecimal.valueOf(tickerData.getBestAsk());
        
        marketService.updateMarketPrices(
            tickerData.getMarketTicker(),
            bestBid,  // yes bid
            bestAsk,  // yes ask
            bestBid,  // no bid (simplified)
            bestAsk,  // no ask (simplified)
            lastPrice
        );
        
        // Update volume
        marketService.updateMarketVolume(tickerData.getMarketTicker(), tickerData.getVolume());
    }
}