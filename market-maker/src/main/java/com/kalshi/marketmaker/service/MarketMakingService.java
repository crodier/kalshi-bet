package com.kalshi.marketmaker.service;

import com.kalshi.marketmaker.client.KalshiApiClient;
import com.kalshi.marketmaker.model.MarketState;
import com.fbg.api.kalshi.KalshiOrderRequest;
import com.fbg.api.kalshi.KalshiOrderbookResponse;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
@RequiredArgsConstructor
public class MarketMakingService {
    
    private final KalshiApiClient kalshiApiClient;
    
    @Value("${market.maker.symbol}")
    private String marketSymbol;
    
    @Value("${market.maker.spread}")
    private int spread;
    
    @Value("${market.maker.min-price}")
    private int minPrice;
    
    @Value("${market.maker.max-price}")
    private int maxPrice;
    
    @Value("${market.maker.quantity}")
    private int quantity;
    
    private final AtomicInteger currentMidPrice = new AtomicInteger(50); // Start at 50 cents
    private boolean movingUp = true;
    
    @PostConstruct
    public void initialize() {
        log.info("Initializing market maker for {} market", marketSymbol);
        checkAndCreateMarket().subscribe();
    }
    
    @Scheduled(fixedDelayString = "${market.maker.update-interval}")
    public void updateMarket() {
        log.debug("Updating market prices for {}", marketSymbol);
        
        // Update mid price
        int midPrice = currentMidPrice.get();
        if (movingUp) {
            midPrice += 1;
            if (midPrice >= maxPrice - spread/2) {
                movingUp = false;
            }
        } else {
            midPrice -= 1;
            if (midPrice <= minPrice + spread/2) {
                movingUp = true;
            }
        }
        currentMidPrice.set(midPrice);
        
        // Cancel existing orders and place new ones
        cancelAllOrders()
                .then(placeMarketMakingOrders())
                .subscribe(
                        result -> log.info("Market updated successfully. Mid price: {} cents", currentMidPrice.get()),
                        error -> log.error("Failed to update market: {}", error.getMessage())
                );
    }
    
    private Mono<Void> checkAndCreateMarket() {
        return kalshiApiClient.getMarket(marketSymbol)
                .doOnNext(market -> log.info("Found {} market", marketSymbol))
                .onErrorResume(error -> {
                    log.warn("Market {} not found, will be created by mock server", marketSymbol);
                    return Mono.empty();
                })
                .then();
    }
    
    private Mono<Void> cancelAllOrders() {
        return kalshiApiClient.getOpenOrders(marketSymbol)
                .flatMapMany(response -> {
                    JsonNode orders = response.get("orders");
                    List<String> orderIds = new ArrayList<>();
                    long thirtyMinutesAgo = System.currentTimeMillis() - (30 * 60 * 1000);
                    
                    if (orders != null && orders.isArray()) {
                        orders.forEach(order -> {
                            JsonNode orderIdNode = order.get("order_id");
                            JsonNode createdTimeNode = order.get("created_time");
                            
                            if (orderIdNode != null) {
                                // Check if order is older than 30 minutes
                                if (createdTimeNode != null) {
                                    long createdTime = createdTimeNode.asLong();
                                    if (createdTime < thirtyMinutesAgo) {
                                        log.info("Canceling old order {}: created {} minutes ago", 
                                                orderIdNode.asText(), 
                                                (System.currentTimeMillis() - createdTime) / (60 * 1000));
                                        orderIds.add(orderIdNode.asText());
                                    } else {
                                        log.debug("Keeping recent order {}: created {} seconds ago", 
                                                orderIdNode.asText(), 
                                                (System.currentTimeMillis() - createdTime) / 1000);
                                    }
                                } else {
                                    // If no timestamp, cancel it as a safety measure
                                    log.warn("Order {} has no timestamp, canceling as safety measure", orderIdNode.asText());
                                    orderIds.add(orderIdNode.asText());
                                }
                            }
                        });
                    }
                    
                    log.info("Found {} orders to cancel (older than 30 minutes)", orderIds.size());
                    return Flux.fromIterable(orderIds);
                })
                .flatMap(orderId -> kalshiApiClient.cancelOrder(orderId)
                        .onErrorResume(error -> {
                            log.warn("Failed to cancel order {}: {}", orderId, error.getMessage());
                            return Mono.empty();
                        }))
                .then();
    }
    
    private Mono<Void> placeMarketMakingOrders() {
        int midPrice = currentMidPrice.get();
        int bidPrice = midPrice - spread/2;
        int askPrice = midPrice + spread/2;
        
        // Create buy order (bid)
        KalshiOrderRequest buyOrder = new KalshiOrderRequest(
                marketSymbol,
                "yes",
                "buy",
                "limit",
                quantity,
                bidPrice,
                "GTC",
                "MM-BUY-" + System.currentTimeMillis()
        );
        
        // Create sell order (ask)
        KalshiOrderRequest sellOrder = new KalshiOrderRequest(
                marketSymbol,
                "yes",
                "sell",
                "limit",
                quantity,
                askPrice,
                "GTC",
                "MM-SELL-" + System.currentTimeMillis()
        );
        
        return Mono.when(
                kalshiApiClient.placeOrder(buyOrder)
                        .doOnNext(result -> log.debug("Placed buy order at {} cents", bidPrice))
                        .onErrorResume(error -> {
                            log.error("Failed to place buy order: {}", error.getMessage());
                            return Mono.empty();
                        }),
                kalshiApiClient.placeOrder(sellOrder)
                        .doOnNext(result -> log.debug("Placed sell order at {} cents", askPrice))
                        .onErrorResume(error -> {
                            log.error("Failed to place sell order: {}", error.getMessage());
                            return Mono.empty();
                        })
        );
    }
}