package com.betfanatics.exchange.order.config;

import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@Profile({"local", "dev"})
@Slf4j
@EnableWebMvc
public class ApiDocWebConfig implements WebMvcConfigurer {
    @Value("${api.hub.url}")
    private String apiHubUrl;

    @Bean
    public WebMvcConfigurer corsConfigurer() {
        log.debug("apiHubUrl from config: {}", apiHubUrl);

        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(@NotNull CorsRegistry registry) {
                registry.addMapping("/**")
                        .allowedOrigins(apiHubUrl)
                        .allowedMethods("GET", "POST") //update if necessary
                        .allowedHeaders("*"); //update if necessary
            }
        };
    }
}
