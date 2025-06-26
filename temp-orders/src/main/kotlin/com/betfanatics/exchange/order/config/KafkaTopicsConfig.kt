package com.betfanatics.exchange.order.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties(prefix = "exchange.order.kafka-config.topics")
data class KafkaTopicsConfig(
    var fixOrderTopic: Topic = Topic(),
    var fixExecutionTopic: Topic = Topic(),
    var backorderTopic: Topic = Topic(),
    var fixErrorTopic: Topic = Topic()
) {
    data class Topic(
        var name: String = ""
    )
}