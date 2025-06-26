package com.betfanatics.exchange.order.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import lombok.Getter;

@Configuration
@ConfigurationProperties(prefix = "exchange.order.grpc.server")
@Getter
public class GrpcServerProperties {
    private final int keepAliveTimeSeconds = 30;
    private final int keepAliveTimeoutSeconds = 5;
    private final int permitKeepAliveTimeSeconds = 10;
    private final boolean permitKeepAliveWithoutCalls = true;
}
