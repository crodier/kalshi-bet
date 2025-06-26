package com.kalshi.marketmaker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class MarketMakerApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(MarketMakerApplication.class, args);
    }
}