package com.fbg.api.kalshi

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Kalshi fill (trade execution)
 */
@Serializable
data class KalshiFill(
    @SerialName("trade_id")
    val tradeId: String,
    
    @SerialName("order_id")
    val orderId: String,
    
    @SerialName("market_ticker")
    val marketTicker: String,
    
    val side: String,
    val action: String,
    
    @SerialName("yes_price")
    val yesPrice: Int,
    
    @SerialName("no_price")
    val noPrice: Int,
    
    val count: Int,
    
    @SerialName("is_taker")
    val isTaker: Boolean,
    
    @SerialName("created_time")
    val createdTime: String
)

/**
 * Kalshi fills response
 */
@Serializable
data class KalshiFillsResponse(
    val fills: List<KalshiFill>,
    val cursor: String? = null
)

/**
 * Kalshi position
 */
@Serializable
data class KalshiPosition(
    @SerialName("market_id")
    val marketId: String,
    
    @SerialName("market_ticker")
    val marketTicker: String,
    
    @SerialName("position")
    val position: Int,
    
    @SerialName("average_price")
    val averagePrice: Double,
    
    @SerialName("realized_pnl")
    val realizedPnl: Long,
    
    @SerialName("total_traded")
    val totalTraded: Long,
    
    @SerialName("resting_orders_count")
    val restingOrdersCount: Int
)

/**
 * Kalshi positions response
 */
@Serializable
data class KalshiPositionsResponse(
    @SerialName("market_positions")
    val marketPositions: List<KalshiPosition>
)