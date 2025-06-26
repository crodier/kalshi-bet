package com.betfanatics.exchange.order.config;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import lombok.extern.slf4j.Slf4j;
// import net.devh.boot.grpc.server.serverfactory.GrpcServerConfigurer;

@Configuration
@Slf4j
public class GrpcServerConfig {

    @Bean(destroyMethod = "shutdown")
    ExecutorService grpcExecutor() {
        log.info("Configuring virtual thread per task gRPC executor with unbounded threads");
        return Executors.newVirtualThreadPerTaskExecutor();
    }

//    @Bean
//    public GrpcServerConfigurer keepAliveServerConfigurer(
//        ExecutorService grpcExecutor,
//        GrpcServerProperties grpcServerProperties) {
//
//        return serverBuilder -> serverBuilder
//            .executor(grpcExecutor)
//            .keepAliveTime(grpcServerProperties.getKeepAliveTimeSeconds(), TimeUnit.SECONDS)
//            .keepAliveTimeout(grpcServerProperties.getKeepAliveTimeoutSeconds(), TimeUnit.SECONDS)
//            .permitKeepAliveTime(grpcServerProperties.getPermitKeepAliveTimeSeconds(), TimeUnit.SECONDS)
//            .permitKeepAliveWithoutCalls(grpcServerProperties.isPermitKeepAliveWithoutCalls());
//    }
}