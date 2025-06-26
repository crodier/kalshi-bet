package com.betfanatics.exchange.order.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.boot.jdbc.DataSourceBuilder
import javax.sql.DataSource

// This is so manky, but when adding r2dbc, spring stopped making the datasource bean
// and we need to manually create it.
// We need this for flyway only
// TODO find a less horrid fix
@Configuration
open class DataSourceInitializerConfig {
    @Bean
    open fun dataSource(
        @Value("\${spring.datasource.url}") url: String,
        @Value("\${spring.datasource.username}") username: String,
        @Value("\${spring.datasource.password}") password: String
    ): DataSource =
        DataSourceBuilder.create()
            .url(url)
            .username(username)
            .password(password)
            .build()
} 