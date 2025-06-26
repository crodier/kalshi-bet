package com.betfanatics.exchange.order.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component
import java.math.BigDecimal

@Component
@ConfigurationProperties(prefix = "exchange.order.risk-limits")
data class RiskLimitsConfig(
    var maxBetAmount: BigDecimal = BigDecimal.ZERO,
    var maxUserTotalRisk: BigDecimal = BigDecimal.ZERO
)