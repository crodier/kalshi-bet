package com.fbg.api.market

enum class Channel {
    orderbook_snapshot,
    orderbook_delta,
    ticker,
    ticker_v2,
    trade,
    fill
}

enum class Side {
    yes,
    no
}

enum class UpdateAction {
    add_markets,
    delete_markets
}

data class PriceLevel(
    val price: Int,
    val size: Int
)

data class OrderbookSnapshot(
    val market_ticker: String,
    val yes: List<PriceLevel>? = null,
    val no: List<PriceLevel>? = null
)

data class OrderbookDelta(
    val market_ticker: String,
    val price: Int,
    val delta: Int,
    val side: Side
)

data class TickerV2(
    val market_ticker: String,
    val price: Int? = null,
    val yes_bid: Int? = null,
    val yes_ask: Int? = null,
    val volume_delta: Int? = null,
    val open_interest_delta: Int? = null
)

data class Fill(
    val trade_id: String,
    val order_id: String,
    val market_ticker: String,
    val is_taker: Boolean,
    val side: Side,
    val count: Int
)

data class SubscribeParams(
    val channels: List<String>,
    val market_tickers: List<String>? = null
)

data class UnsubscribeParams(
    val sids: List<String>
)

data class UpdateSubscriptionParams(
    val sids: List<String>,
    val market_tickers: List<String>,
    val action: UpdateAction
)

data class SubscribeCommand(
    val id: Int,
    val cmd: String = "subscribe",
    val params: SubscribeParams
)

data class UnsubscribeCommand(
    val id: Int,
    val cmd: String = "unsubscribe",
    val params: UnsubscribeParams
)

data class UpdateSubscriptionCommand(
    val id: Int,
    val cmd: String = "update_subscription",
    val params: UpdateSubscriptionParams
)

data class SubscriptionResponse(
    val id: Int,
    val status: String,
    val msg: String? = null,
    val sids: List<String>? = null
)

data class ErrorResponse(
    val error: String,
    val code: Int? = null
)