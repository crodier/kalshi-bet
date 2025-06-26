package com.kalshi.mock.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.Components;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI kalshiOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Mock Kalshi Trading API")
                        .description("Mock implementation of Kalshi's Trading API for testing and development")
                        .version("v2")
                        .contact(new Contact()
                                .name("Mock Kalshi Team")
                                .url("https://kalshi.com")))
                .servers(Arrays.asList(
                        new Server().url("http://localhost:9090/trade-api/v2").description("Local Development Server")
                ))
                .addSecurityItem(new SecurityRequirement().addList("ApiKeyAuth"))
                .components(new Components()
                        .addSecuritySchemes("ApiKeyAuth", new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name("KALSHI-ACCESS-KEY")
                                .description("API Key Authentication")));
    }
}