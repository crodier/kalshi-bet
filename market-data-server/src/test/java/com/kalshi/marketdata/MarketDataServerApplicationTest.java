package com.kalshi.marketdata;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
    "mock.kalshi.rest.url=http://localhost:9999",
    "mock.kalshi.websocket.url=ws://localhost:9999/ws",
    "spring.kafka.bootstrap-servers=localhost:9999"
})
class MarketDataServerApplicationTest {

    @Test
    void contextLoads() {
        // This test ensures the Spring context loads successfully
    }
}