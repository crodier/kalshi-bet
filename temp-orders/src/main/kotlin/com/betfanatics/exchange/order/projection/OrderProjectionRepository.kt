package com.betfanatics.exchange.order.projection

import org.springframework.data.r2dbc.repository.Modifying
import org.springframework.data.r2dbc.repository.Query
import org.springframework.data.repository.reactive.ReactiveCrudRepository
import org.springframework.stereotype.Repository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.math.BigDecimal
import java.time.Instant

@Repository
interface OrderProjectionRepository : ReactiveCrudRepository<OrderProjection, String> {

    @Modifying
    @Query("""
        INSERT INTO order_projection (order_id, user_id, symbol, side, amount, filled_qty, status, timestamp)
        VALUES (:orderId, :userId, :symbol, :side, :amount, :filledQty, :status, :timestamp)
    """)
    fun insertOrder(
        orderId: String,
        userId: String,
        symbol: String,
        side: String,
        amount: BigDecimal,
        filledQty: BigDecimal,
        status: String,
        timestamp: Instant
    ): Mono<Void>

    @Modifying
    @Query("""
        UPDATE order_projection
        SET filled_qty = :filledQty, status = :status, timestamp = :timestamp
        WHERE order_id = :orderId
    """)
    fun updateOrderFilled(
        orderId: String,
        filledQty: BigDecimal,
        status: String,
        timestamp: Instant
    ): Mono<Void>

    @Query("""
        SELECT * FROM order_projection
        WHERE user_id = :userId
        ORDER BY timestamp DESC
        LIMIT :size OFFSET :offset
    """)
    fun findPortfolioByUserId(
        userId: String,
        size: Int,
        offset: Int
    ): Flux<OrderProjection>

    @Query("""
        SELECT COUNT(*) FROM order_projection WHERE user_id = :userId
    """)
    fun countPortfolioByUserId(userId: String): Mono<Long>
}