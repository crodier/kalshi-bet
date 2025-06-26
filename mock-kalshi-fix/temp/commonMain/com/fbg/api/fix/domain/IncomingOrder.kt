// IncomingOrder.kt
package com.fbg.api.fix.domain

import com.fbg.api.fix.enums.OrderType
import com.fbg.api.fix.enums.TimeInForce
import com.fbg.api.fix.enums.TradingSide

/**
 * Critical API for OEMS, to create an order.
 *
 * This is the message the OEMS (Order+Execution management system) receives
 * from the FBG domain.  This is translated into FIX using the Enums and 'extensions'
 * in this class.
 */

sealed class IncomingOrder {
    // link to the FBG order domain - TODO - what is it called in FBG
    abstract val fbgOrderId: String
    abstract val createTimestamp: Long // don't send orders stale > 1 minute (stuck)
    abstract val shortUUID: String // safety / idempotence

    data class NewOrder(
        override val fbgOrderId: String,
        override val createTimestamp: Long,
        override val shortUUID: String,

        val side: TradingSide,
        val orderType: OrderType,
        val timeInForce: TimeInForce,
        val quantity: Double,
        val price: Double?,
        val symbol: String,
        val clientOrderId: String
    ) : IncomingOrder()
    
    data class CancelOrder(
        override val fbgOrderId: String,
        override val createTimestamp: Long,
        override val shortUUID: String,
        val originalClientOrderId: String,
        val clientOrderId: String,
        val side: TradingSide,
        val symbol: String
    ) : IncomingOrder()
    
    data class ModifyOrder(
        override val fbgOrderId: String,
        override val createTimestamp: Long,
        override val shortUUID: String,
        val originalClientOrderId: String,
        val clientOrderId: String,
        val side: TradingSide,
        val orderType: OrderType,
        val timeInForce: TimeInForce,
        val quantity: Double?,
        val price: Double?,
        val symbol: String
    ) : IncomingOrder()
}