package com.betfanatics.exchange.order.health

import org.springframework.boot.actuate.health.Health
import org.springframework.boot.actuate.health.HealthIndicator
import org.springframework.stereotype.Component
import quickfix.SessionID
import java.time.Instant
import java.time.Duration

@Component
class FixHealthIndicator : HealthIndicator {
    
    @Volatile
    private var isConnected: Boolean = false
    
    @Volatile
    private var lastConnectedTime: Instant? = null
    
    @Volatile
    private var lastDisconnectedTime: Instant? = null
    
    @Volatile
    private var sessionId: SessionID? = null
    
    @Volatile
    private var connectionError: String? = null
    
    fun setConnected(sessionId: SessionID) {
        this.isConnected = true
        this.sessionId = sessionId
        this.lastConnectedTime = Instant.now()
        this.connectionError = null
    }
    
    fun setDisconnected(error: String? = null) {
        this.isConnected = false
        this.lastDisconnectedTime = Instant.now()
        this.connectionError = error
    }
    
    override fun health(): Health {
        val builder = if (isConnected) {
            Health.up()
        } else {
            Health.down()
        }
        
        builder.withDetail("connected", isConnected)
        
        sessionId?.let {
            builder.withDetail("sessionId", it.toString())
        }
        
        lastConnectedTime?.let {
            builder.withDetail("lastConnectedTime", it.toString())
            if (isConnected) {
                val connectedDuration = Duration.between(it, Instant.now())
                builder.withDetail("connectedDurationSeconds", connectedDuration.toSeconds())
            }
        }
        
        lastDisconnectedTime?.let {
            builder.withDetail("lastDisconnectedTime", it.toString())
            if (!isConnected) {
                val disconnectedDuration = Duration.between(it, Instant.now())
                builder.withDetail("disconnectedDurationSeconds", disconnectedDuration.toSeconds())
            }
        }
        
        connectionError?.let {
            builder.withDetail("lastError", it)
        }
        
        if (!isConnected) {
            builder.withDetail("status", "FIX connection is not established")
        }
        
        return builder.build()
    }
}