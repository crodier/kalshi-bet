package com.kalshi.mock.websocket.service;

import com.kalshi.mock.websocket.dto.SubscriptionResponse;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.WebSocketSession;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class SubscriptionManager {
    
    // Session ID -> Subscription ID -> Subscription Info
    private final Map<String, Map<String, SubscriptionInfo>> sessionSubscriptions = new ConcurrentHashMap<>();
    
    // Market Ticker -> Channel -> Set of Session IDs
    private final Map<String, Map<String, Set<String>>> marketSubscribers = new ConcurrentHashMap<>();
    
    // Subscription ID generator
    private final AtomicInteger subscriptionIdGenerator = new AtomicInteger(1);
    
    public static class SubscriptionInfo {
        private final String sid;
        private final String channel;
        private final List<String> marketTickers;
        private final String sessionId;
        
        public SubscriptionInfo(String sid, String channel, List<String> marketTickers, String sessionId) {
            this.sid = sid;
            this.channel = channel;
            this.marketTickers = marketTickers;
            this.sessionId = sessionId;
        }
        
        // Getters
        public String getSid() { return sid; }
        public String getChannel() { return channel; }
        public List<String> getMarketTickers() { return marketTickers; }
        public String getSessionId() { return sessionId; }
    }
    
    public SubscriptionResponse.Subscription subscribe(String sessionId, String channel, List<String> marketTickers) {
        String sid = "sub_" + subscriptionIdGenerator.getAndIncrement();
        
        // Store subscription info
        SubscriptionInfo info = new SubscriptionInfo(sid, channel, marketTickers, sessionId);
        sessionSubscriptions.computeIfAbsent(sessionId, k -> new ConcurrentHashMap<>())
                           .put(sid, info);
        
        // Update market subscribers
        for (String ticker : marketTickers) {
            marketSubscribers.computeIfAbsent(ticker, k -> new ConcurrentHashMap<>())
                            .computeIfAbsent(channel, k -> Collections.synchronizedSet(new HashSet<>()))
                            .add(sessionId);
        }
        
        return new SubscriptionResponse.Subscription(sid, channel, marketTickers);
    }
    
    public boolean unsubscribe(String sessionId, String sid) {
        Map<String, SubscriptionInfo> subscriptions = sessionSubscriptions.get(sessionId);
        if (subscriptions == null) {
            return false;
        }
        
        SubscriptionInfo info = subscriptions.remove(sid);
        if (info == null) {
            return false;
        }
        
        // Remove from market subscribers
        for (String ticker : info.getMarketTickers()) {
            Map<String, Set<String>> channelSubs = marketSubscribers.get(ticker);
            if (channelSubs != null) {
                Set<String> sessions = channelSubs.get(info.getChannel());
                if (sessions != null) {
                    sessions.remove(sessionId);
                    if (sessions.isEmpty()) {
                        channelSubs.remove(info.getChannel());
                    }
                }
                if (channelSubs.isEmpty()) {
                    marketSubscribers.remove(ticker);
                }
            }
        }
        
        return true;
    }
    
    public SubscriptionResponse.Subscription updateSubscription(String sessionId, String sid, List<String> newMarketTickers) {
        Map<String, SubscriptionInfo> subscriptions = sessionSubscriptions.get(sessionId);
        if (subscriptions == null) {
            return null;
        }
        
        SubscriptionInfo oldInfo = subscriptions.get(sid);
        if (oldInfo == null) {
            return null;
        }
        
        // Remove old market subscriptions
        for (String ticker : oldInfo.getMarketTickers()) {
            Map<String, Set<String>> channelSubs = marketSubscribers.get(ticker);
            if (channelSubs != null) {
                Set<String> sessions = channelSubs.get(oldInfo.getChannel());
                if (sessions != null) {
                    sessions.remove(sessionId);
                }
            }
        }
        
        // Create new subscription info
        SubscriptionInfo newInfo = new SubscriptionInfo(sid, oldInfo.getChannel(), newMarketTickers, sessionId);
        subscriptions.put(sid, newInfo);
        
        // Add new market subscriptions
        for (String ticker : newMarketTickers) {
            marketSubscribers.computeIfAbsent(ticker, k -> new ConcurrentHashMap<>())
                            .computeIfAbsent(oldInfo.getChannel(), k -> Collections.synchronizedSet(new HashSet<>()))
                            .add(sessionId);
        }
        
        return new SubscriptionResponse.Subscription(sid, oldInfo.getChannel(), newMarketTickers);
    }
    
    public void removeSession(String sessionId) {
        Map<String, SubscriptionInfo> subscriptions = sessionSubscriptions.remove(sessionId);
        if (subscriptions == null) {
            return;
        }
        
        // Remove all subscriptions for this session
        for (SubscriptionInfo info : subscriptions.values()) {
            for (String ticker : info.getMarketTickers()) {
                Map<String, Set<String>> channelSubs = marketSubscribers.get(ticker);
                if (channelSubs != null) {
                    Set<String> sessions = channelSubs.get(info.getChannel());
                    if (sessions != null) {
                        sessions.remove(sessionId);
                        if (sessions.isEmpty()) {
                            channelSubs.remove(info.getChannel());
                        }
                    }
                    if (channelSubs.isEmpty()) {
                        marketSubscribers.remove(ticker);
                    }
                }
            }
        }
    }
    
    public Set<String> getSubscribedSessions(String marketTicker, String channel) {
        Map<String, Set<String>> channelSubs = marketSubscribers.get(marketTicker);
        if (channelSubs == null) {
            return Collections.emptySet();
        }
        
        Set<String> sessions = channelSubs.get(channel);
        return sessions != null ? new HashSet<>(sessions) : Collections.emptySet();
    }
    
    public List<SubscriptionResponse.Subscription> getSessionSubscriptions(String sessionId) {
        Map<String, SubscriptionInfo> subscriptions = sessionSubscriptions.get(sessionId);
        if (subscriptions == null) {
            return Collections.emptyList();
        }
        
        List<SubscriptionResponse.Subscription> result = new ArrayList<>();
        for (SubscriptionInfo info : subscriptions.values()) {
            result.add(new SubscriptionResponse.Subscription(info.getSid(), info.getChannel(), info.getMarketTickers()));
        }
        return result;
    }
}