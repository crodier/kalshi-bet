package com.kalshi.orderbook.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Thread-safe Trie data structure for efficient market ticker prefix matching.
 * Optimized for concurrent reads and writes with market ticker search functionality.
 */
@Slf4j
@Component
public class MarketTrie {
    
    private final TrieNode root;
    private final ReadWriteLock lock;
    
    public MarketTrie() {
        this.root = new TrieNode();
        this.lock = new ReentrantReadWriteLock();
    }
    
    /**
     * Adds a market ticker to the trie
     */
    public void addMarket(String marketTicker) {
        if (marketTicker == null || marketTicker.trim().isEmpty()) {
            return;
        }
        
        String normalizedTicker = marketTicker.toUpperCase().trim();
        
        lock.writeLock().lock();
        try {
            TrieNode current = root;
            
            for (char c : normalizedTicker.toCharArray()) {
                current = current.children.computeIfAbsent(c, k -> new TrieNode());
            }
            
            current.isEndOfWord = true;
            current.marketTickers.add(marketTicker); // Store original case
            
            log.debug("Added market ticker to trie: {}", marketTicker);
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Removes a market ticker from the trie
     */
    public void removeMarket(String marketTicker) {
        if (marketTicker == null || marketTicker.trim().isEmpty()) {
            return;
        }
        
        String normalizedTicker = marketTicker.toUpperCase().trim();
        
        lock.writeLock().lock();
        try {
            removeMarketRecursive(root, normalizedTicker, 0, marketTicker);
            log.debug("Removed market ticker from trie: {}", marketTicker);
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    private boolean removeMarketRecursive(TrieNode node, String normalizedTicker, int index, String originalTicker) {
        if (index == normalizedTicker.length()) {
            if (!node.isEndOfWord) {
                return false;
            }
            
            node.marketTickers.remove(originalTicker);
            if (node.marketTickers.isEmpty()) {
                node.isEndOfWord = false;
            }
            
            // Return true if current node has no children and is not end of another word
            return node.children.isEmpty() && !node.isEndOfWord;
        }
        
        char c = normalizedTicker.charAt(index);
        TrieNode childNode = node.children.get(c);
        
        if (childNode == null) {
            return false;
        }
        
        boolean shouldDeleteChild = removeMarketRecursive(childNode, normalizedTicker, index + 1, originalTicker);
        
        if (shouldDeleteChild) {
            node.children.remove(c);
            // Return true if current node has no children and is not end of word
            return node.children.isEmpty() && !node.isEndOfWord;
        }
        
        return false;
    }
    
    /**
     * Finds all market tickers matching the given prefix
     * Returns them sorted alphabetically, limited by maxResults
     */
    public List<String> findMarketsWithPrefix(String prefix, int maxResults) {
        if (prefix == null) {
            prefix = "";
        }
        
        String normalizedPrefix = prefix.toUpperCase().trim();
        
        lock.readLock().lock();
        try {
            TrieNode current = root;
            
            // Navigate to the prefix node
            for (char c : normalizedPrefix.toCharArray()) {
                current = current.children.get(c);
                if (current == null) {
                    return Collections.emptyList();
                }
            }
            
            // Collect all market tickers from this node and its descendants
            Set<String> results = new TreeSet<>(); // Use TreeSet for automatic sorting
            collectAllMarkets(current, results, maxResults);
            
            return new ArrayList<>(results);
        } finally {
            lock.readLock().unlock();
        }
    }
    
    private void collectAllMarkets(TrieNode node, Set<String> results, int maxResults) {
        if (results.size() >= maxResults) {
            return;
        }
        
        if (node.isEndOfWord) {
            results.addAll(node.marketTickers);
        }
        
        for (TrieNode child : node.children.values()) {
            if (results.size() >= maxResults) {
                break;
            }
            collectAllMarkets(child, results, maxResults);
        }
    }
    
    /**
     * Gets all market tickers in the trie, sorted alphabetically
     */
    public List<String> getAllMarkets() {
        return findMarketsWithPrefix("", Integer.MAX_VALUE);
    }
    
    /**
     * Gets the total number of unique markets in the trie
     */
    public int getMarketCount() {
        lock.readLock().lock();
        try {
            Set<String> allMarkets = new HashSet<>();
            collectAllMarketsForCount(root, allMarkets);
            return allMarkets.size();
        } finally {
            lock.readLock().unlock();
        }
    }
    
    private void collectAllMarketsForCount(TrieNode node, Set<String> markets) {
        if (node.isEndOfWord) {
            markets.addAll(node.marketTickers);
        }
        
        for (TrieNode child : node.children.values()) {
            collectAllMarketsForCount(child, markets);
        }
    }
    
    /**
     * Checks if a market ticker exists in the trie
     */
    public boolean containsMarket(String marketTicker) {
        if (marketTicker == null || marketTicker.trim().isEmpty()) {
            return false;
        }
        
        String normalizedTicker = marketTicker.toUpperCase().trim();
        
        lock.readLock().lock();
        try {
            TrieNode current = root;
            
            for (char c : normalizedTicker.toCharArray()) {
                current = current.children.get(c);
                if (current == null) {
                    return false;
                }
            }
            
            return current.isEndOfWord && current.marketTickers.contains(marketTicker);
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Gets market suggestions for autocomplete
     */
    public List<String> getSuggestions(String partialTicker, int maxSuggestions) {
        return findMarketsWithPrefix(partialTicker, maxSuggestions);
    }
    
    /**
     * Clears all markets from the trie
     */
    public void clear() {
        lock.writeLock().lock();
        try {
            root.children.clear();
            root.isEndOfWord = false;
            root.marketTickers.clear();
            log.debug("Cleared all markets from trie");
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Thread-safe node for the trie structure
     */
    private static class TrieNode {
        final Map<Character, TrieNode> children;
        volatile boolean isEndOfWord;
        final Set<String> marketTickers; // Store original market tickers for this prefix
        
        TrieNode() {
            this.children = new ConcurrentHashMap<>();
            this.isEndOfWord = false;
            this.marketTickers = ConcurrentHashMap.newKeySet();
        }
    }
}