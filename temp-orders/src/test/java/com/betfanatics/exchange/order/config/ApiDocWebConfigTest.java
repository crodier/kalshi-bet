package com.betfanatics.exchange.order.config;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.CorsRegistration;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;


@ExtendWith(MockitoExtension.class)
class ApiDocWebConfigTest {

    @InjectMocks
    private ApiDocWebConfig apiDocWebConfig;

    @Mock
    private CorsRegistry corsRegistry;

    @Mock
    private CorsRegistration corsRegistration;

    private final String apiHubUrl = "https://example.com";

    @BeforeEach
    void setUp() {

        // Manually inject the property value
        ReflectionTestUtils.setField(apiDocWebConfig, "apiHubUrl", apiHubUrl);

        // Mock behavior of addMapping to return a CorsRegistration instance
        when(corsRegistry.addMapping("/**")).thenReturn(corsRegistration);

        // Allow method chaining on CorsRegistration mock
        when(corsRegistration.allowedOrigins(apiHubUrl)).thenReturn(corsRegistration);
        when(corsRegistration.allowedMethods("GET", "POST")).thenReturn(corsRegistration);
        when(corsRegistration.allowedHeaders("*")).thenReturn(corsRegistration);
    }

    @Test
    void testCorsConfigurer() {
        WebMvcConfigurer configurer = apiDocWebConfig.corsConfigurer();
        configurer.addCorsMappings(corsRegistry);

        // Verify that CORS is configured correctly
        verify(corsRegistry).addMapping("/**");
        verify(corsRegistration).allowedOrigins(apiHubUrl);
        verify(corsRegistration).allowedMethods("GET", "POST");
        verify(corsRegistration).allowedHeaders("*");
    }
}
