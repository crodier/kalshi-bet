package com.kalshi.marketdata.service;

import com.kalshi.marketdata.model.MarketInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Market search service with Trie-based prefix matching for fast market filtering
 */
@Service
@Slf4j
public class MarketSearchService {
    
    @Autowired
    private MarketDiscoveryService marketDiscoveryService;
    
    @Autowired
    private OrderBookManager orderBookManager;
    
    // Trie for efficient prefix searching
    private final TrieNode root = new TrieNode();
    
    // Cache of market information
    private final Map<String, MarketInfo> marketCache = new ConcurrentHashMap<>();
    
    @PostConstruct
    public void initialize() {
        refreshMarketCache();
    }
    
    /**
     * Search markets using prefix matching with the Trie
     */
    public List<MarketInfo> searchMarkets(String query) {
        if (query == null || query.trim().isEmpty()) {
            return getAllMarkets();
        }
        
        String normalizedQuery = query.trim().toLowerCase();
        Set<String> matchingTickers = new HashSet<>();
        
        // Find prefix matches
        List<String> prefixMatches = findPrefixMatches(normalizedQuery);
        matchingTickers.addAll(prefixMatches);
        
        // Find infix matches (substring search)
        List<String> infixMatches = findInfixMatches(normalizedQuery);
        matchingTickers.addAll(infixMatches);
        
        // Convert tickers to MarketInfo and sort by relevance
        return matchingTickers.stream()
                .map(marketCache::get)
                .filter(Objects::nonNull)
                .sorted(this::compareByRelevance)
                .limit(50) // Limit results to prevent overwhelming UI
                .collect(Collectors.toList());
    }
    
    /**
     * Get all available markets
     */
    public List<MarketInfo> getAllMarkets() {
        return new ArrayList<>(marketCache.values())
                .stream()
                .sorted(this::compareByRelevance)
                .collect(Collectors.toList());
    }
    
    /**
     * Update market information (called when new market data arrives)
     */
    public void updateMarketInfo(String ticker, boolean isActive, long messageCount) {
        MarketInfo marketInfo = marketCache.computeIfAbsent(ticker, MarketInfo::new);
        marketInfo.setActive(isActive);
        marketInfo.setLastUpdate(Instant.now());
        marketInfo.setMessageCount(messageCount);
        marketInfo.setBootstrapped(orderBookManager.isMarketBootstrapped(ticker));
        
        // Add to Trie if not already present
        addToTrie(ticker.toLowerCase());
        
        log.debug("Updated market info for: {} (active: {}, messages: {})", ticker, isActive, messageCount);
    }
    
    /**
     * Refresh the market cache from discovery service
     */
    public void refreshMarketCache() {
        try {
            List<String> marketTickers = marketDiscoveryService.getAllMarketTickers();
            
            for (String ticker : marketTickers) {
                MarketInfo existingInfo = marketCache.get(ticker);
                if (existingInfo == null) {
                    MarketInfo newInfo = new MarketInfo(ticker);
                    newInfo.setBootstrapped(orderBookManager.isMarketBootstrapped(ticker));
                    marketCache.put(ticker, newInfo);
                    addToTrie(ticker.toLowerCase());
                }
            }
            
            log.info("Refreshed market cache with {} markets", marketCache.size());
            
        } catch (Exception e) {
            log.error("Failed to refresh market cache", e);
        }
    }
    
    private void addToTrie(String word) {
        TrieNode current = root;
        for (char c : word.toCharArray()) {
            current.children.putIfAbsent(c, new TrieNode());
            current = current.children.get(c);
        }
        current.isEndOfWord = true;
        current.words.add(word);
    }
    
    private List<String> findPrefixMatches(String prefix) {
        TrieNode current = root;
        
        // Navigate to prefix node
        for (char c : prefix.toCharArray()) {
            if (!current.children.containsKey(c)) {
                return Collections.emptyList();
            }
            current = current.children.get(c);
        }
        
        // Collect all words with this prefix
        List<String> results = new ArrayList<>();
        collectAllWords(current, results);
        return results;
    }
    
    private void collectAllWords(TrieNode node, List<String> results) {
        results.addAll(node.words);
        for (TrieNode child : node.children.values()) {
            collectAllWords(child, results);
        }
    }
    
    private List<String> findInfixMatches(String query) {
        return marketCache.keySet().stream()
                .filter(ticker -> ticker.toLowerCase().contains(query))
                .collect(Collectors.toList());
    }
    
    private int compareByRelevance(MarketInfo a, MarketInfo b) {
        // Sort by: active first, then by recent activity, then alphabetically
        if (a.isActive() != b.isActive()) {
            return a.isActive() ? -1 : 1;
        }
        
        if (a.getLastUpdate() != null && b.getLastUpdate() != null) {
            int timeComparison = b.getLastUpdate().compareTo(a.getLastUpdate());
            if (timeComparison != 0) {
                return timeComparison;
            }
        }
        
        return a.getTicker().compareTo(b.getTicker());
    }
    
    /**
     * Trie node for efficient prefix matching
     */
    private static class TrieNode {
        Map<Character, TrieNode> children = new HashMap<>();
        boolean isEndOfWord = false;
        Set<String> words = new HashSet<>();
    }
}