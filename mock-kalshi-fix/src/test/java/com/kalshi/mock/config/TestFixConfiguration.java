package com.kalshi.mock.config;

import com.kalshi.mock.FixServerService;
import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;

@TestConfiguration
@Profile("test")
public class TestFixConfiguration {
    
    @Bean
    public FixServerService fixServerService() {
        // Return a mock FixServerService for tests
        return Mockito.mock(FixServerService.class);
    }
}