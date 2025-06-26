package com.kalshi.mock;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
@TestPropertySource(properties = {
    "quickfix.enabled=false"
})
class MockApplicationTests {

    @Autowired
    private MockFixClient mockFixClient;


    @Test
    void contextLoads() {
        assertNotNull(mockFixClient);
    }

    @Test
    void testSending77PriceIOCMarketOrderIsFilled() {
        
    }
    
}
