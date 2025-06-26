package com.betfanatics.exchange.order.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.web.client.OAuth2ClientHttpRequestInterceptor;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class RestClientConfigTest {

    @Mock
    private RestClient.Builder restClientBuilder;

    @Mock
    private OAuth2AuthorizedClientManager authorizedClientManager;

    @Mock
    private RestClient restClient;

    @InjectMocks
    private RestClientConfig restClientConfig;

    @Test
    void testRestClient_shouldConfigureOAuth2Interceptor() {

        // When
        when(restClientBuilder.requestInterceptor(any())).thenReturn(restClientBuilder);
        when(restClientBuilder.build()).thenReturn(restClient);

        RestClient result = restClientConfig.restClient(restClientBuilder, authorizedClientManager);

        // Then
        assertThat(result).isNotNull();
        assertThat(result).isEqualTo(restClient);

        verify(restClientBuilder, times(1)).requestInterceptor(any(
            OAuth2ClientHttpRequestInterceptor.class));
        verify(restClientBuilder, times(1)).requestFactory(any());
        verify(restClientBuilder, times(1)).build();
        verifyNoMoreInteractions(restClientBuilder);
    }
}
