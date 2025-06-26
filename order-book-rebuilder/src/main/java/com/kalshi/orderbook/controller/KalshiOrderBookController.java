package com.kalshi.orderbook.controller;

import com.fbg.api.kalshi.InternalOrderBook;
import com.fbg.api.kalshi.PriceLevel;
import com.kalshi.orderbook.service.OrderBookManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/trade-api/v2")
@RequiredArgsConstructor
public class KalshiOrderBookController {
    
    private final OrderBookManager orderBookManager;
    
    /**
     * GET /trade-api/v2/markets/{ticker}/orderbook?depth={depth}
     * Kalshi-compatible order book endpoint
     */
    @GetMapping("/markets/{ticker}/orderbook")
    public ResponseEntity<Map<String, Object>> getMarketOrderBook(
            @PathVariable String ticker,
            @RequestParam(defaultValue = "10") int depth) {
        
        InternalOrderBook orderBook = orderBookManager.getOrderBook(ticker);
        if (orderBook == null) {
            return ResponseEntity.notFound().build();
        }
        
        // Get top levels for each side
        List<PriceLevel> yesLevels = orderBook.getYesSide().getSortedLevels().stream()
            .limit(depth)
            .collect(java.util.stream.Collectors.toList());
        List<PriceLevel> noLevels = orderBook.getNoSide().getSortedLevels().stream()
            .limit(depth)
            .collect(java.util.stream.Collectors.toList());
        
        // Convert to enhanced format with timing information
        List<Object> yes = yesLevels.stream()
            .map(level -> Map.of(
                "price", level.getPrice(),
                "size", level.getQuantity(),
                "timestamp", level.getLastUpdateTimestamp(),
                "internalLevelUpdatedTimestamp", level.getInternalLevelUpdatedTimestamp(),
                "updateType", level.getLastUpdateType().toString(),
                "sequenceNumber", level.getLastUpdateSequence() != null ? level.getLastUpdateSequence() : 0L
            ))
            .collect(java.util.stream.Collectors.toList());
        
        List<Object> no = noLevels.stream()
            .map(level -> Map.of(
                "price", level.getPrice(),
                "size", level.getQuantity(),
                "timestamp", level.getLastUpdateTimestamp(),
                "internalLevelUpdatedTimestamp", level.getInternalLevelUpdatedTimestamp(),
                "updateType", level.getLastUpdateType().toString(),
                "sequenceNumber", level.getLastUpdateSequence() != null ? level.getLastUpdateSequence() : 0L
            ))
            .collect(java.util.stream.Collectors.toList());
        
        Map<String, Object> response = Map.of(
            "orderbook", Map.of(
                "market_ticker", ticker,
                "yes", yes,
                "no", no,
                "lastUpdateTimestamp", orderBook.getLastUpdateTimestamp(),
                "lastTimeInternalBookUpdated", orderBook.getLastTimeInternalBookUpdated(),
                "receivedTimestamp", orderBook.getReceivedTimestamp(),
                "processedTimestamp", orderBook.getProcessedTimestamp(),
                "processingLatency", orderBook.getProcessingLatency()
            )
        );
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * GET /trade-api/v2/orderbook?ticker={ticker}&depth={depth}
     * Alternative Kalshi-compatible order book endpoint
     */
    @GetMapping("/orderbook")
    public ResponseEntity<Map<String, Object>> getOrderBook(
            @RequestParam String ticker,
            @RequestParam(defaultValue = "10") int depth) {
        
        return getMarketOrderBook(ticker, depth);
    }
}