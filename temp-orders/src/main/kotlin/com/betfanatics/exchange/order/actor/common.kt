package com.betfanatics.exchange.order.actor.common

import org.apache.pekko.cluster.sharding.typed.javadsl.EntityRef
import java.math.BigDecimal

enum class OrderSide {
    BUY, SELL
}

enum class OrderType {
    MARKET, LIMIT
}

enum class TimeInForce {
    GTC, // Good Till Cancelled
    IOC, // Immediate or Cancel
    FOK  // Fill or Kill
}

data class OrderRequestDTO(
    val orderId: String,
    val symbol: String,
    val side: OrderSide,
    val quantity: BigDecimal,
    val price: BigDecimal? = null,
    val orderType: OrderType,
    val timeInForce: TimeInForce,
    val userId: String
)

typealias OrderActorResolver = (String) -> EntityRef<com.betfanatics.exchange.order.actor.OrderActor.Command>
typealias OrderProcessManagerResolver = (String) -> EntityRef<com.betfanatics.exchange.order.actor.OrderProcessManager.Command>
typealias PositionActorResolver = (String) -> EntityRef<com.betfanatics.exchange.order.actor.PositionActor.Command> 