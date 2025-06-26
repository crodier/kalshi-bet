package com.betfanatics.exchange.model

data class OrderRequest(
    val symbol: String,
    val side: String, // "buy" or "sell"
    val quantity: Double,
    val price: Double? = null
) 