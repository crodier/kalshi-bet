package com.fbg.api.kalshi

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Kalshi order request matching the actual API structure
 */
@Serializable
data class KalshiOrderRequest(
    @SerialName("market_ticker")
    val marketTicker: String,
    
    val side: String, // "yes" or "no"
    val action: String, // "buy" or "sell"
    val type: String, // "limit" or "market"
    val count: Int,
    val price: Int? = null, // Price in cents (required for limit orders)
    
    @SerialName("time_in_force")
    val timeInForce: String? = "GTC", // "GTC", "IOC", or "FOK"
    
    @SerialName("client_order_id")
    val clientOrderId: String? = null
)

/**
 * Kalshi order response
 */
@Serializable
data class KalshiOrderResponse(
    val order: KalshiOrder
)

/**
 * Kalshi order details
 */
@Serializable
data class KalshiOrder(
    @SerialName("order_id")
    val orderId: String,
    
    @SerialName("user_id")
    val userId: String,
    
    @SerialName("market_id")
    val marketId: String,
    
    @SerialName("market_ticker")
    val marketTicker: String,
    
    val side: String,
    val action: String,
    val type: String,
    val status: String,
    
    @SerialName("yes_price")
    val yesPrice: Int? = null,
    
    @SerialName("no_price")
    val noPrice: Int? = null,
    
    @SerialName("limit_price")
    val limitPrice: Int? = null,
    
    @SerialName("order_count")
    val orderCount: Int,
    
    @SerialName("remaining_count")
    val remainingCount: Int,
    
    @SerialName("client_order_id")
    val clientOrderId: String? = null,
    
    @SerialName("created_time")
    val createdTime: String,
    
    @SerialName("last_update_time")
    val lastUpdateTime: String? = null,
    
    @SerialName("expiration_time")
    val expirationTime: String? = null
)

/**
 * Kalshi cancel order response
 */
@Serializable
data class KalshiCancelOrderResponse(
    val order: KalshiOrder,
    val canceled: Boolean,
    val message: String? = null
)

/**
 * List of orders response
 */
@Serializable
data class KalshiOrdersResponse(
    val orders: List<KalshiOrder>,
    val cursor: String? = null
)