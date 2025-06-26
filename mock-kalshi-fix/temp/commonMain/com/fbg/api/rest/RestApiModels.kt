package com.fbg.api.rest

import com.fbg.api.market.Side

data class MarketResponse(
    val market: Market
)

data class MarketsResponse(
    val markets: List<Market>
)

data class Market(
    val id: String,
    val ticker: String,
    val event_id: String,
    val status: String,
    val yes_bid: Int?,
    val yes_ask: Int?,
    val no_bid: Int?,
    val no_ask: Int?,
    val last_price: Int?,
    val volume: Long,
    val volume_24h: Long,
    val open_interest: Long,
    val open_time: Long,
    val close_time: Long,
    val expected_expiration_time: Long?,
    val expiration_time: Long?,
    val result: String?,
    val can_close_early: Boolean,
    val cap_strike: Int?,
    val floor_strike: Int?
)

data class OrderbookResponse(
    val orderbook: Orderbook
)

data class Orderbook(
    val yes: List<List<Int>>?,
    val no: List<List<Int>>?
)

data class EventsResponse(
    val events: List<Event>
)

data class Event(
    val id: String,
    val ticker: String,
    val category: String,
    val sub_category: String?,
    val title: String,
    val description: String?,
    val status: String,
    val markets: List<Market>?
)

data class SeriesResponse(
    val series: List<Series>
)

data class Series(
    val id: String,
    val ticker: String,
    val title: String,
    val category: String,
    val sub_category: String?,
    val status: String,
    val frequency: String?,
    val settlement_timer: Int?
)

data class TradesResponse(
    val trades: List<Trade>,
    val cursor: String?
)

data class Trade(
    val trade_id: String,
    val market_id: String,
    val market_ticker: String,
    val price: Int,
    val count: Int,
    val side: Side,
    val created_time: Long,
    val yes_price: Int?,
    val no_price: Int?
)

data class OrderRequest(
    val side: Side,
    val symbol: String,
    val order_type: String,
    val quantity: Int,
    val price: Int?,
    val time_in_force: String?,
    val client_order_id: String?
)

data class OrderResponse(
    val order: Order
)

data class Order(
    val id: String,
    val client_order_id: String?,
    val user_id: String,
    val side: Side,
    val symbol: String,
    val order_type: String,
    val quantity: Int,
    val filled_quantity: Int,
    val remaining_quantity: Int,
    val price: Int?,
    val avg_fill_price: Int?,
    val status: String,
    val time_in_force: String?,
    val created_time: Long,
    val updated_time: Long,
    val expiration_time: Long?
)

data class OrdersResponse(
    val orders: List<Order>,
    val cursor: String?
)

data class BatchOrderRequest(
    val orders: List<OrderRequest>
)

data class BatchOrderResponse(
    val orders: List<Order>
)

data class BatchCancelRequest(
    val order_ids: List<String>?,
    val client_order_ids: List<String>?
)

data class BatchCancelResponse(
    val canceled: List<String>,
    val not_canceled: List<String>
)

data class AmendOrderRequest(
    val order_id: String?,
    val client_order_id: String?,
    val price: Int?,
    val quantity: Int?
)

data class DecreaseOrderRequest(
    val order_id: String?,
    val client_order_id: String?,
    val reduce_by: Int?,
    val reduce_to: Int?
)

data class FillsResponse(
    val fills: List<Fill>,
    val cursor: String?
)

data class Fill(
    val id: String,
    val order_id: String,
    val market_id: String,
    val market_ticker: String,
    val side: Side,
    val price: Int,
    val count: Int,
    val is_taker: Boolean,
    val created_time: Long,
    val trade_id: String
)

data class PositionsResponse(
    val positions: List<Position>
)

data class Position(
    val market_id: String,
    val market_ticker: String,
    val quantity: Int,
    val avg_price: Int,
    val side: Side,
    val realized_pnl: Int,
    val total_cost: Int
)

data class BalanceResponse(
    val balance: Balance
)

data class Balance(
    val total_balance: Long,
    val available_balance: Long,
    val clearing_balance: Long,
    val withdrawable_balance: Long
)

data class SettlementsResponse(
    val settlements: List<Settlement>,
    val cursor: String?
)

data class Settlement(
    val id: String,
    val market_id: String,
    val market_ticker: String,
    val side: Side,
    val quantity: Int,
    val price: Int,
    val pnl: Int,
    val created_time: Long,
    val settlement_time: Long
)

data class QuoteRequest(
    val market_id: String,
    val side: Side,
    val quantity: Int,
    val price: Int,
    val expiration_time: Long?
)

data class QuoteResponse(
    val quote: Quote
)

data class Quote(
    val id: String,
    val user_id: String,
    val market_id: String,
    val side: Side,
    val quantity: Int,
    val price: Int,
    val status: String,
    val created_time: Long,
    val expiration_time: Long?
)

data class CandlesticksResponse(
    val series: List<Candlestick>,
    val cursor: String?
)

data class Candlestick(
    val timestamp: Long,
    val open: Int,
    val high: Int,
    val low: Int,
    val close: Int,
    val volume: Long
)

data class ErrorResponse(
    val error: ErrorDetail
)

data class ErrorDetail(
    val code: String,
    val message: String,
    val details: Map<String, Any>?
)