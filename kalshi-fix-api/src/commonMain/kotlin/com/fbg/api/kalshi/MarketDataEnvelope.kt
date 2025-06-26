package com.fbg.api.kalshi

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Envelope for market data messages that includes timing metadata
 * for tracking when data was received and when it's being published to Kafka
 */
@Serializable
data class MarketDataEnvelope(
    /**
     * The original WebSocket message payload as received from Kalshi
     */
    val payload: JsonElement,
    
    /**
     * Timestamp when the market data update was received from the WebSocket
     * (in milliseconds since epoch)
     */
    val receivedTimestamp: Long,
    
    /**
     * Timestamp when this envelope is being published to Kafka
     * (in milliseconds since epoch)
     */
    val publishedTimestamp: Long,
    
    /**
     * Optional channel identifier from the WebSocket message
     * (e.g., "orderbook_snapshot", "orderbook_delta", "ticker_v2", "trade")
     */
    val channel: String? = null,
    
    /**
     * Optional market ticker if applicable
     */
    val marketTicker: String? = null,
    
    /**
     * Optional sequence number from the original message
     */
    val sequence: Long? = null,
    
    /**
     * Source identifier (e.g., "kalshi-websocket")
     */
    val source: String = "kalshi-websocket",
    
    /**
     * Version of the envelope format
     */
    val version: Int = 1
) {
    /**
     * Calculate the latency between receipt and publishing (in milliseconds)
     */
    fun latencyMs(): Long = publishedTimestamp - receivedTimestamp
}

/**
 * Factory for creating MarketDataEnvelope instances
 */
object MarketDataEnvelopeFactory {
    /**
     * Create a new envelope for a received WebSocket message
     */
    fun create(
        payload: JsonElement,
        receivedTimestamp: Long = System.currentTimeMillis(),
        channel: String? = null,
        marketTicker: String? = null,
        sequence: Long? = null
    ): MarketDataEnvelope {
        return MarketDataEnvelope(
            payload = payload,
            receivedTimestamp = receivedTimestamp,
            publishedTimestamp = System.currentTimeMillis(),
            channel = channel,
            marketTicker = marketTicker,
            sequence = sequence
        )
    }
}