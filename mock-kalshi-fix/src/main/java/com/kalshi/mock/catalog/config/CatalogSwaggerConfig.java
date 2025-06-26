package com.kalshi.mock.catalog.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger/OpenAPI configuration for catalog API endpoints
 */
@Configuration
@OpenAPIDefinition(
    info = @Info(
        title = "Kalshi Catalog API",
        version = "2.0",
        description = "Mock implementation of Kalshi's catalog management API"
    ),
    servers = {
        @Server(url = "/", description = "Default Server")
    }
)
@SecurityScheme(
    name = "ApiKeyAuth",
    type = SecuritySchemeType.APIKEY,
    paramName = "KALSHI-ACCESS-KEY",
    in = io.swagger.v3.oas.annotations.enums.SecuritySchemeIn.HEADER,
    description = "API key for authentication. Use 'admin-' prefix for admin operations."
)
public class CatalogSwaggerConfig {
    // Configuration is handled via annotations
}