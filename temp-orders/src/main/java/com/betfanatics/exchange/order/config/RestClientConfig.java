package com.betfanatics.exchange.order.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.web.client.OAuth2ClientHttpRequestInterceptor;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import java.net.http.HttpClient;
import java.util.concurrent.Executors;

/**
 * Configures the RestClient to use the Spring Security OAuth2 interceptor, which will
 * automatically reach out to the OAuth2 provider and append the JWT token to the Authorization
 * header of the API client call.
 */
@Configuration
public class RestClientConfig {

    @Bean
    public RestClient restClient(RestClient.Builder builder, OAuth2AuthorizedClientManager authorizedClientManager) {

        builder.requestInterceptor(new OAuth2ClientHttpRequestInterceptor(authorizedClientManager));

        final var httpClient = HttpClient
            .newBuilder()
            .version(HttpClient.Version.HTTP_2)
            .executor(Executors.newVirtualThreadPerTaskExecutor())
            .build();

        builder.requestFactory(new JdkClientHttpRequestFactory(httpClient));

        return builder.build();
    }
}