package com.betfanatics.exchange.order.config;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class AppReadinessHealthIndicator implements HealthIndicator {

    /**
     * This event will be raised when an ApplicationContext gets initialized or refreshed.
     */
    @EventListener
    public void handleContextRefresh(ContextRefreshedEvent event) {

        // TODO:  Check infrastructure here.  Instead, we'll just initialize a timestamp to simulate a delay
    }

    @Override
    public Health health() {
        return Health.up().build();
    }
}
