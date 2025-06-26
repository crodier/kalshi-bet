package com.betfanatics.exchange.order.dto

import com.betfanatics.exchange.order.actor.common.OrderRequestDTO
import java.math.BigDecimal
import java.time.Instant

/**
 * Complete mapping of FIX ExecutionReport (35=8) to API response
 * Based on FIX 5.0 SP2 specification
 */
data class ExecutionReportDTO(
    // Order Identifiers
    val clOrdId: String,              // Tag 11 - Client Order ID
    val betOrderId: String,           // Our internal order ID (extracted from ClOrdID)
    val orderId: String?,             // Tag 37 - Exchange Order ID
    val origClOrdId: String?,         // Tag 41 - Original Client Order ID (for cancel/replace)
    val execId: String,               // Tag 17 - Execution ID
    
    // Status Fields
    val execType: String,             // Tag 150 - Execution Type (0=New, 1=PartialFill, 2=Fill, etc.)
    val execTypeDesc: String,         // Human-readable execution type
    val ordStatus: String,            // Tag 39 - Order Status (0=New, 1=PartiallyFilled, 2=Filled, etc.)
    val ordStatusDesc: String,        // Human-readable order status
    val ordRejReason: String?,        // Tag 103 - Order Reject Reason
    val execRestatementReason: String?, // Tag 378 - Exec Restatement Reason
    
    // Order Details
    val symbol: String,               // Tag 55 - Symbol (Kalshi market ticker)
    val side: String,                 // Tag 54 - Side (1=Buy, 2=Sell)
    val sideDesc: String,             // Human-readable side
    val orderQty: BigDecimal,         // Tag 38 - Order Quantity
    val ordType: String,              // Tag 40 - Order Type (1=Market, 2=Limit)
    val ordTypeDesc: String,          // Human-readable order type
    val price: BigDecimal?,           // Tag 44 - Price (for limit orders)
    val stopPx: BigDecimal?,          // Tag 99 - Stop Price
    val timeInForce: String?,         // Tag 59 - Time in Force (0=Day, 1=GTC, 3=IOC, 4=FOK)
    val timeInForceDesc: String?,     // Human-readable time in force
    val expireTime: Instant?,         // Tag 126 - Expire Time (for GTD orders)
    
    // Execution Details
    val lastQty: BigDecimal?,         // Tag 32 - Last Quantity (this fill)
    val lastPx: BigDecimal?,          // Tag 31 - Last Price (this fill)
    val cumQty: BigDecimal,           // Tag 14 - Cumulative Quantity
    val avgPx: BigDecimal?,           // Tag 6 - Average Price
    val leavesQty: BigDecimal,        // Tag 151 - Leaves Quantity (remaining)
    val commission: BigDecimal?,      // Tag 12 - Commission
    val commType: String?,            // Tag 13 - Commission Type
    val grossTradeAmt: BigDecimal?,   // Tag 381 - Gross Trade Amount
    val netMoney: BigDecimal?,        // Tag 118 - Net Money
    
    // Timestamps
    val transactTime: Instant,        // Tag 60 - Transaction Time
    val sendingTime: Instant?,        // Tag 52 - Sending Time
    val origTime: Instant?,           // Tag 42 - Original Time (for cancel/replace)
    
    // Text and Additional Info
    val text: String?,                // Tag 58 - Free format text
    val secondaryOrderId: String?,    // Tag 198 - Secondary Order ID
    val secondaryExecId: String?,     // Tag 527 - Secondary Execution ID
    val execRefId: String?,           // Tag 19 - Execution Reference ID
    
    // Party Information (from repeating group)
    val account: String?,             // Extracted from Party repeating group
    val userId: String?,              // Extracted from Party ID
    
    // Settlement Information
    val settlDate: String?,           // Tag 64 - Settlement Date
    val settlType: String?,           // Tag 63 - Settlement Type
    
    // Our Additional Fields
    val betOrder: OrderRequestDTO?,   // Original order that created this execution
    val receivedTime: Instant = Instant.now(), // When we received this report
    
    // Kalshi Specific Fields
    val marketType: String?,          // Extracted from symbol parsing
    val expiryDate: String?,          // Extracted from symbol parsing
    val strikePrice: BigDecimal?      // Extracted from symbol parsing
)

/**
 * Builder for creating ExecutionReportDTO from FIX messages
 */
class ExecutionReportDTOBuilder {
    var clOrdId: String = ""
    var betOrderId: String = ""
    var orderId: String? = null
    var origClOrdId: String? = null
    var execId: String = ""
    var execType: String = ""
    var execTypeDesc: String = ""
    var ordStatus: String = ""
    var ordStatusDesc: String = ""
    var ordRejReason: String? = null
    var execRestatementReason: String? = null
    var symbol: String = ""
    var side: String = ""
    var sideDesc: String = ""
    var orderQty: BigDecimal = BigDecimal.ZERO
    var ordType: String = ""
    var ordTypeDesc: String = ""
    var price: BigDecimal? = null
    var stopPx: BigDecimal? = null
    var timeInForce: String? = null
    var timeInForceDesc: String? = null
    var expireTime: Instant? = null
    var lastQty: BigDecimal? = null
    var lastPx: BigDecimal? = null
    var cumQty: BigDecimal = BigDecimal.ZERO
    var avgPx: BigDecimal? = null
    var leavesQty: BigDecimal = BigDecimal.ZERO
    var commission: BigDecimal? = null
    var commType: String? = null
    var grossTradeAmt: BigDecimal? = null
    var netMoney: BigDecimal? = null
    var transactTime: Instant = Instant.now()
    var sendingTime: Instant? = null
    var origTime: Instant? = null
    var text: String? = null
    var secondaryOrderId: String? = null
    var secondaryExecId: String? = null
    var execRefId: String? = null
    var account: String? = null
    var userId: String? = null
    var settlDate: String? = null
    var settlType: String? = null
    var betOrder: OrderRequestDTO? = null
    var marketType: String? = null
    var expiryDate: String? = null
    var strikePrice: BigDecimal? = null
    
    fun build(): ExecutionReportDTO = ExecutionReportDTO(
        clOrdId = clOrdId,
        betOrderId = betOrderId,
        orderId = orderId,
        origClOrdId = origClOrdId,
        execId = execId,
        execType = execType,
        execTypeDesc = execTypeDesc,
        ordStatus = ordStatus,
        ordStatusDesc = ordStatusDesc,
        ordRejReason = ordRejReason,
        execRestatementReason = execRestatementReason,
        symbol = symbol,
        side = side,
        sideDesc = sideDesc,
        orderQty = orderQty,
        ordType = ordType,
        ordTypeDesc = ordTypeDesc,
        price = price,
        stopPx = stopPx,
        timeInForce = timeInForce,
        timeInForceDesc = timeInForceDesc,
        expireTime = expireTime,
        lastQty = lastQty,
        lastPx = lastPx,
        cumQty = cumQty,
        avgPx = avgPx,
        leavesQty = leavesQty,
        commission = commission,
        commType = commType,
        grossTradeAmt = grossTradeAmt,
        netMoney = netMoney,
        transactTime = transactTime,
        sendingTime = sendingTime,
        origTime = origTime,
        text = text,
        secondaryOrderId = secondaryOrderId,
        secondaryExecId = secondaryExecId,
        execRefId = execRefId,
        account = account,
        userId = userId,
        settlDate = settlDate,
        settlType = settlType,
        betOrder = betOrder,
        marketType = marketType,
        expiryDate = expiryDate,
        strikePrice = strikePrice
    )
}