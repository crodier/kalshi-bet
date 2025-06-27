package com.kalshi.orderbook.service;

import com.fbg.api.kalshi.InternalOrderBook;
import com.kalshi.orderbook.util.MarketTrie;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderBookManager {
    
    private final ConcurrentHashMap<String, InternalOrderBook> orderBooks = new ConcurrentHashMap<>();
    private final MarketTrie marketTrie;
    
    public InternalOrderBook getOrCreateOrderBook(String marketTicker) {
        return orderBooks.computeIfAbsent(marketTicker, k -> {
            log.info("Creating new order book for market: {}", k);
            marketTrie.addMarket(k);
            // Create an empty InternalOrderBook using the builder
            return new com.fbg.api.kalshi.InternalOrderBookBuilder(k, System.currentTimeMillis())
                .build();
        });
    }
    
    public InternalOrderBook getOrderBook(String marketTicker) {
        return orderBooks.get(marketTicker);
    }
    
    public Collection<InternalOrderBook> getAllOrderBooks() {
        return orderBooks.values();
    }
    
    public Map<String, InternalOrderBook> getOrderBooksForMarkets(Collection<String> marketTickers) {
        return marketTickers.stream()
            .filter(orderBooks::containsKey)
            .collect(Collectors.toMap(
                ticker -> ticker,
                orderBooks::get
            ));
    }
    
    public boolean hasOrderBook(String marketTicker) {
        return orderBooks.containsKey(marketTicker);
    }
    
    public int getOrderBookCount() {
        return orderBooks.size();
    }
    
    public void removeOrderBook(String marketTicker) {
        InternalOrderBook removed = orderBooks.remove(marketTicker);
        if (removed != null) {
            marketTrie.removeMarket(marketTicker);
            log.info("Removed order book for market: {}", marketTicker);
        }
    }
    
    public void updateOrderBook(String marketTicker, InternalOrderBook newOrderBook) {
        orderBooks.put(marketTicker, newOrderBook);
    }
    
    // Market search functionality using the trie
    public List<String> searchMarkets(String prefix, int maxResults) {
        return marketTrie.findMarketsWithPrefix(prefix, maxResults);
    }
    
    public List<String> getAllMarketTickers() {
        return marketTrie.getAllMarkets();
    }
    
    public List<String> getMarketSuggestions(String partialTicker, int maxSuggestions) {
        return marketTrie.getSuggestions(partialTicker, maxSuggestions);
    }
}