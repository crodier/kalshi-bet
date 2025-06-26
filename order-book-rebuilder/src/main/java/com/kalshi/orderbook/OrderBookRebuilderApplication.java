package com.kalshi.orderbook;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class OrderBookRebuilderApplication {
    public static void main(String[] args) {
        SpringApplication.run(OrderBookRebuilderApplication.class, args);
    }
}