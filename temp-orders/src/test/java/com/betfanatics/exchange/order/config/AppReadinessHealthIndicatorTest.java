package com.betfanatics.exchange.order.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.actuate.health.Health;
import org.springframework.context.event.ContextRefreshedEvent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class AppReadinessHealthIndicatorTest {

    private AppReadinessHealthIndicator healthIndicator;

    @BeforeEach
    void setUp() {
        healthIndicator = new AppReadinessHealthIndicator();
    }

    @Test
    void testHealth() {

        // Given
        ContextRefreshedEvent event = mock(ContextRefreshedEvent.class);
        healthIndicator.handleContextRefresh(event);

        // When
        Health health = healthIndicator.health();

        // Then
        assertEquals(Health.up().build(), health);
    }
}
