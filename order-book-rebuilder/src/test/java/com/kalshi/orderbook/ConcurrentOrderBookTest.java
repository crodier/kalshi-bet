package com.kalshi.orderbook;

import com.kalshi.orderbook.model.OrderBook;
import com.kalshi.orderbook.model.OrderBookLevel;
import com.kalshi.orderbook.service.OrderBookManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

public class ConcurrentOrderBookTest {
    
    private OrderBookManager orderBookManager;
    private Random random = new Random();
    
    @BeforeEach
    void setUp() {
        orderBookManager = new OrderBookManager();
    }
    
    @Test
    void testConcurrentReadWrite() throws InterruptedException {
        String marketTicker = "TEST_MARKET";
        OrderBook orderBook = orderBookManager.getOrCreateOrderBook(marketTicker);
        
        int numWriters = 10;
        int numReaders = 20;
        int operationsPerThread = 1000;
        
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(numWriters + numReaders);
        
        AtomicLong writeCount = new AtomicLong();
        AtomicLong readCount = new AtomicLong();
        
        ExecutorService executor = Executors.newFixedThreadPool(numWriters + numReaders);
        
        // Start writer threads
        for (int i = 0; i < numWriters; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    for (int j = 0; j < operationsPerThread; j++) {
                        int price = 30 + random.nextInt(40); // 30-70 cents
                        long size = random.nextInt(10000);
                        
                        if (random.nextBoolean()) {
                            orderBook.updateYesSide(price, size, System.currentTimeMillis());
                        } else {
                            orderBook.updateNoSide(price, size, System.currentTimeMillis());
                        }
                        writeCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    endLatch.countDown();
                }
            });
        }
        
        // Start reader threads
        for (int i = 0; i < numReaders; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    for (int j = 0; j < operationsPerThread; j++) {
                        // Perform various read operations
                        OrderBookLevel bestYes = orderBook.getBestYes();
                        OrderBookLevel bestNo = orderBook.getBestNo();
                        List<OrderBookLevel> topYes = orderBook.getTopYes(5);
                        List<OrderBookLevel> topNo = orderBook.getTopNo(5);
                        
                        readCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    endLatch.countDown();
                }
            });
        }
        
        long startTime = System.currentTimeMillis();
        startLatch.countDown(); // Start all threads
        
        assertTrue(endLatch.await(30, TimeUnit.SECONDS));
        long endTime = System.currentTimeMillis();
        
        executor.shutdown();
        
        System.out.println("Concurrent test completed:");
        System.out.println("  Duration: " + (endTime - startTime) + "ms");
        System.out.println("  Total writes: " + writeCount.get());
        System.out.println("  Total reads: " + readCount.get());
        System.out.println("  Writes/sec: " + (writeCount.get() * 1000 / (endTime - startTime)));
        System.out.println("  Reads/sec: " + (readCount.get() * 1000 / (endTime - startTime)));
        
        assertNotNull(orderBook.getBestYes());
        assertNotNull(orderBook.getBestNo());
    }
    
    @Test
    void testLargeScalePerformance() throws InterruptedException {
        int numMarkets = 1000;
        int updatesPerSecond = 1000;
        int testDurationSeconds = 5;
        
        // Create markets
        List<String> markets = new ArrayList<>();
        for (int i = 0; i < numMarkets; i++) {
            String ticker = "MARKET_" + i;
            markets.add(ticker);
            OrderBook ob = orderBookManager.getOrCreateOrderBook(ticker);
            // Initialize with some data
            for (int j = 0; j < 5; j++) {
                ob.updateYesSide(50 - j, 1000 + j * 100, System.currentTimeMillis());
                ob.updateNoSide(52 + j, 1000 + j * 100, System.currentTimeMillis());
            }
        }
        
        ExecutorService executor = Executors.newFixedThreadPool(50);
        CountDownLatch startLatch = new CountDownLatch(1);
        AtomicLong updateCount = new AtomicLong();
        AtomicLong readCount = new AtomicLong();
        
        // Schedule updates
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(10);
        long startTime = System.currentTimeMillis();
        
        // Writer tasks
        for (int i = 0; i < 10; i++) {
            scheduler.scheduleAtFixedRate(() -> {
                for (int j = 0; j < updatesPerSecond / 10; j++) {
                    String market = markets.get(random.nextInt(markets.size()));
                    OrderBook ob = orderBookManager.getOrderBook(market);
                    
                    int price = 30 + random.nextInt(40); // 30-70 cents
                    long size = random.nextInt(10000);
                    
                    if (random.nextBoolean()) {
                        ob.updateYesSide(price, size, System.currentTimeMillis());
                    } else {
                        ob.updateNoSide(price, size, System.currentTimeMillis());
                    }
                    updateCount.incrementAndGet();
                }
            }, 0, 1000, TimeUnit.MILLISECONDS);
        }
        
        // Reader tasks
        for (int i = 0; i < 20; i++) {
            executor.submit(() -> {
                while (System.currentTimeMillis() - startTime < testDurationSeconds * 1000) {
                    String market = markets.get(random.nextInt(markets.size()));
                    OrderBook ob = orderBookManager.getOrderBook(market);
                    
                    ob.getBestYes();
                    ob.getBestNo();
                    ob.getTopYes(3);
                    ob.getTopNo(3);
                    
                    readCount.incrementAndGet();
                }
            });
        }
        
        // Wait for test duration
        Thread.sleep(testDurationSeconds * 1000);
        
        scheduler.shutdown();
        executor.shutdown();
        
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;
        
        System.out.println("\nLarge scale performance test results:");
        System.out.println("  Markets: " + numMarkets);
        System.out.println("  Duration: " + duration + "ms");
        System.out.println("  Total updates: " + updateCount.get());
        System.out.println("  Total reads: " + readCount.get());
        System.out.println("  Updates/sec: " + (updateCount.get() * 1000 / duration));
        System.out.println("  Reads/sec: " + (readCount.get() * 1000 / duration));
        
        assertEquals(numMarkets, orderBookManager.getOrderBookCount());
        assertTrue(updateCount.get() > updatesPerSecond * (testDurationSeconds - 1));
    }
}