package com.betfanatics.exchange.order.config;

import org.springframework.context.annotation.Profile;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.OAuthFlow;
import io.swagger.v3.oas.annotations.security.OAuthFlows;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;

/*
 * This class is not needed if auth is not required for the apis
 */
@OpenAPIDefinition(security = @SecurityRequirement(name = "oauth2"))
@SecurityScheme(
        name = "oauth2",
        type = SecuritySchemeType.OAUTH2,
        flows = @OAuthFlows(
                clientCredentials =  @OAuthFlow(
                        tokenUrl = "${springdoc.oAuthFlow.tokenUrl}"
                )
        )
)
@Profile({"local", "dev"})
public class OpenApiConfig {
    // No additional configuration needed
}