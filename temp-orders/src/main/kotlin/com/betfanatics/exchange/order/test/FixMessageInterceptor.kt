package com.betfanatics.exchange.order.test

import quickfix.Message
import quickfix.SessionID

/**
 * Interface for intercepting FIX messages before they are sent to QuickFIX/J.
 * This is used for testing purposes to capture and verify outgoing FIX messages.
 */
interface FixMessageInterceptor {
    
    /**
     * Called when a FIX message is about to be sent to the target.
     * 
     * @param message The FIX message being sent
     * @param sessionId The session ID for the FIX connection
     * @param messageType The message type (D, F, G, etc.)
     * @param orderId The order ID associated with the message (if applicable)
     */
    fun onOutgoingMessage(message: Message, sessionId: SessionID, messageType: String, orderId: String?)
    
    /**
     * Called when a FIX message is received from the exchange.
     * 
     * @param message The FIX message received
     * @param sessionId The session ID for the FIX connection  
     * @param messageType The message type (8, etc.)
     * @param orderId The order ID associated with the message (if applicable)
     */
    fun onIncomingMessage(message: Message, sessionId: SessionID, messageType: String, orderId: String?)
    
    /**
     * Called when an admin message is sent or received.
     * 
     * @param message The admin message
     * @param sessionId The session ID for the FIX connection
     * @param messageType The message type (A, 0, etc.)
     * @param direction "OUTGOING" or "INCOMING"
     */
    fun onAdminMessage(message: Message, sessionId: SessionID, messageType: String, direction: String)
}

/**
 * Registry for managing FIX message interceptors.
 * This allows tests to register interceptors to capture messages.
 */
object FixMessageInterceptorRegistry {
    
    private val interceptors = mutableListOf<FixMessageInterceptor>()
    
    /**
     * Register an interceptor to receive FIX message events.
     */
    fun register(interceptor: FixMessageInterceptor) {
        interceptors.add(interceptor)
    }
    
    /**
     * Unregister an interceptor.
     */
    fun unregister(interceptor: FixMessageInterceptor) {
        interceptors.remove(interceptor)
    }
    
    /**
     * Clear all registered interceptors.
     */
    fun clear() {
        interceptors.clear()
    }
    
    /**
     * Notify all interceptors of an outgoing message.
     */
    fun notifyOutgoingMessage(message: Message, sessionId: SessionID, messageType: String, orderId: String?) {
        interceptors.forEach { it.onOutgoingMessage(message, sessionId, messageType, orderId) }
    }
    
    /**
     * Notify all interceptors of an incoming message.
     */
    fun notifyIncomingMessage(message: Message, sessionId: SessionID, messageType: String, orderId: String?) {
        interceptors.forEach { it.onIncomingMessage(message, sessionId, messageType, orderId) }
    }
    
    /**
     * Notify all interceptors of an admin message.
     */
    fun notifyAdminMessage(message: Message, sessionId: SessionID, messageType: String, direction: String) {
        interceptors.forEach { it.onAdminMessage(message, sessionId, messageType, direction) }
    }
}