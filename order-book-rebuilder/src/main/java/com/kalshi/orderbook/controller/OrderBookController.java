package com.kalshi.orderbook.controller;

import com.kalshi.orderbook.dto.BulkOrderBookRequest;
import com.kalshi.orderbook.dto.OrderBookSnapshot;
import com.fbg.api.kalshi.InternalOrderBook;
import com.fbg.api.kalshi.PriceLevel;
import com.kalshi.orderbook.service.OrderBookManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/v1/orderbook")
@RequiredArgsConstructor
public class OrderBookController {
    
    private final OrderBookManager orderBookManager;
    
    @GetMapping("/{marketTicker}")
    public ResponseEntity<OrderBookSnapshot> getOrderBook(
            @PathVariable String marketTicker,
            @RequestParam(defaultValue = "5") int depth) {
        
        InternalOrderBook orderBook = orderBookManager.getOrderBook(marketTicker);
        if (orderBook == null) {
            return ResponseEntity.notFound().build();
        }
        
        OrderBookSnapshot snapshot = buildSnapshot(orderBook, depth, false);
        return ResponseEntity.ok(snapshot);
    }
    
    @PostMapping("/bulk")
    public ResponseEntity<Map<String, OrderBookSnapshot>> getBulkOrderBooks(
            @RequestBody BulkOrderBookRequest request) {
        
        Map<String, InternalOrderBook> orderBooks = 
            orderBookManager.getOrderBooksForMarkets(request.getMarketTickers());
        
        Map<String, OrderBookSnapshot> snapshots = orderBooks.entrySet().stream()
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                entry -> buildSnapshot(
                    entry.getValue(), 
                    request.getDepth(), 
                    request.isIncludeAllLevels()
                )
            ));
        
        return ResponseEntity.ok(snapshots);
    }
    
    @GetMapping("/all")
    public ResponseEntity<List<OrderBookSnapshot>> getAllOrderBooks(
            @RequestParam(defaultValue = "1") int depth) {
        
        List<OrderBookSnapshot> snapshots = orderBookManager.getAllOrderBooks().stream()
            .map(orderBook -> buildSnapshot(orderBook, depth, false))
            .collect(Collectors.toList());
        
        return ResponseEntity.ok(snapshots);
    }
    
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        Map<String, Object> stats = Map.of(
            "totalOrderBooks", orderBookManager.getOrderBookCount(),
            "activeMarkets", orderBookManager.getAllOrderBooks().size(), // Remove market status filtering for now
            "totalLevels", orderBookManager.getAllOrderBooks().stream()
                .mapToInt(ob -> ob.getYesSide().getLevelCount() + ob.getNoSide().getLevelCount())
                .sum()
        );
        
        return ResponseEntity.ok(stats);
    }
    
    private OrderBookSnapshot buildSnapshot(InternalOrderBook orderBook, int depth, boolean includeAllLevels) {
        // Get sorted levels for each side
        List<PriceLevel> yesSideLevels = orderBook.getYesSide().getSortedLevels();
        List<PriceLevel> noSideLevels = orderBook.getNoSide().getSortedLevels();
        
        // Limit depth if not including all levels
        if (!includeAllLevels) {
            yesSideLevels = yesSideLevels.stream().limit(depth).collect(Collectors.toList());
            noSideLevels = noSideLevels.stream().limit(depth).collect(Collectors.toList());
        }
        
        return OrderBookSnapshot.builder()
            .marketTicker(orderBook.getMarketTicker())
            .bestBid(orderBook.getBestYesLevel())  // Yes side is the bid equivalent
            .bestAsk(orderBook.getBestNoLevel())   // No side is the ask equivalent
            .bids(yesSideLevels)
            .asks(noSideLevels)
            .lastUpdateTimestamp(orderBook.getLastUpdateTimestamp())
            .lastTimeInternalBookUpdated(orderBook.getLastTimeInternalBookUpdated())
            .receivedTimestamp(orderBook.getReceivedTimestamp())
            .processedTimestamp(orderBook.getProcessedTimestamp())
            .processingLatency(orderBook.getProcessingLatency())
            .bidLevels(orderBook.getYesSide().getLevelCount())
            .askLevels(orderBook.getNoSide().getLevelCount())
            .build();
    }
}