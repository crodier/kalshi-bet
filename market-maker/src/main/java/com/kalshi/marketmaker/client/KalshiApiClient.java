package com.kalshi.marketmaker.client;

import com.fbg.api.kalshi.KalshiOrderRequest;
import com.fbg.api.kalshi.KalshiOrderbookResponse;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;

@Slf4j
@Component
@RequiredArgsConstructor
public class KalshiApiClient {
    
    private final WebClient kalshiWebClient;
    
    /**
     * Get order book for a market
     */
    public Mono<KalshiOrderbookResponse> getOrderbook(String marketTicker) {
        return kalshiWebClient.get()
                .uri("/trade-api/v2/markets/{ticker}/orderbook", marketTicker)
                .retrieve()
                .bodyToMono(KalshiOrderbookResponse.class)
                .retryWhen(Retry.backoff(3, Duration.ofSeconds(1)))
                .doOnError(error -> log.error("Failed to get orderbook for {}: {}", marketTicker, error.getMessage()));
    }
    
    /**
     * Place an order
     */
    public Mono<JsonNode> placeOrder(KalshiOrderRequest orderRequest) {
        return kalshiWebClient.post()
                .uri("/trade-api/v2/portfolio/orders")
                .bodyValue(orderRequest)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .retryWhen(Retry.backoff(3, Duration.ofSeconds(1)))
                .doOnError(error -> log.error("Failed to place order: {}", error.getMessage()));
    }
    
    /**
     * Get open orders
     */
    public Mono<JsonNode> getOpenOrders(String marketTicker) {
        return kalshiWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/trade-api/v2/portfolio/orders")
                        .queryParam("market_ticker", marketTicker)
                        .queryParam("status", "open")
                        .build())
                .retrieve()
                .bodyToMono(JsonNode.class)
                .retryWhen(Retry.backoff(3, Duration.ofSeconds(1)))
                .doOnError(error -> log.error("Failed to get open orders: {}", error.getMessage()));
    }
    
    /**
     * Cancel an order
     */
    public Mono<JsonNode> cancelOrder(String orderId) {
        return kalshiWebClient.delete()
                .uri("/trade-api/v2/portfolio/orders/{orderId}", orderId)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .retryWhen(Retry.backoff(3, Duration.ofSeconds(1)))
                .doOnError(error -> log.error("Failed to cancel order {}: {}", orderId, error.getMessage()));
    }
    
    /**
     * Get market info
     */
    public Mono<JsonNode> getMarket(String marketTicker) {
        return kalshiWebClient.get()
                .uri("/trade-api/v2/markets/{ticker}", marketTicker)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .retryWhen(Retry.backoff(3, Duration.ofSeconds(1)))
                .doOnError(error -> log.error("Failed to get market {}: {}", marketTicker, error.getMessage()));
    }
}