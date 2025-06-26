package com.betfanatics.exchange.order.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(MockitoExtension.class)
class AppLivenessHealthIndicatorTest {

    private final HealthIndicator healthIndicator = new AppLivenessHealthIndicator();

    @Test
    void testHealth() {
        Health health = healthIndicator.health();
        assertEquals(Health.up().build(), health);
    }
}
