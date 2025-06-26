package com.fbg.api.kalshi

import kotlinx.serialization.Serializable

/**
 * Kalshi orderbook response matching the exact API structure
 */
@Serializable
data class KalshiOrderbookResponse(
    val orderbook: KalshiOrderbook
)

/**
 * Kalshi orderbook data
 * Each inner list is [price_in_cents, quantity]
 */
@Serializable
data class KalshiOrderbook(
    val yes: List<List<Int>>, // Buy YES orders (bids) - sorted high to low
    val no: List<List<Int>>   // Buy NO orders - sorted low to high
)

/**
 * Kalshi market data
 */
@Serializable
data class KalshiMarket(
    val ticker: String,
    val event_ticker: String,
    val market_type: String,
    val title: String,
    val subtitle: String,
    val yes_ask: Int?,
    val yes_bid: Int?,
    val no_ask: Int?,
    val no_bid: Int?,
    val last_price: Int?,
    val previous_yes_ask: Int?,
    val previous_yes_bid: Int?,
    val previous_no_ask: Int?,
    val previous_no_bid: Int?,
    val volume: Long,
    val volume_24h: Long,
    val open_interest: Long,
    val status: String,
    val result: String?,
    val open_time: String,
    val close_time: String,
    val expiration_time: String?,
    val expected_expiration_time: String?
)

/**
 * Kalshi markets response
 */
@Serializable
data class KalshiMarketsResponse(
    val markets: List<KalshiMarket>,
    val cursor: String? = null
)

/**
 * Kalshi single market response
 */
@Serializable
data class KalshiMarketResponse(
    val market: KalshiMarket
)