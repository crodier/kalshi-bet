package com.betfanatics.exchange.order.projection

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table
import java.math.BigDecimal
import java.time.Instant

@Table("order_projection")
data class OrderProjection(
    @Id
    val orderId: String,
    val userId: String?,
    val symbol: String?,
    val side: String?,
    val amount: BigDecimal?,
    val filledQty: BigDecimal?,
    val status: String?,
    val timestamp: Instant?
) 