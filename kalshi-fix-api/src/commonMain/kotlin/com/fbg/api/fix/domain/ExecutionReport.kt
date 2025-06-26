package com.fbg.api.fix.domain

import com.fbg.api.fix.enums.*
import com.fbg.api.common.BetDecimal
import com.fbg.api.common.BetLocalDateTime

/**
 * ExecutionReport using BetDecimal and BetLocalDateTime for multiplatform compatibility
 * This allows compilation to both JVM and JS targets
 */
data class ExecutionReport(
    // Required Fields (FIX 5.0SP2)
    val orderID: String,                    // Tag 37 - Unique identifier for Order
    val execID: String,                     // Tag 17 - Unique identifier for execution
    val execType: ExecutionType,            // Tag 150 - Describes the purpose of the ExecutionReport
    val ordStatus: OrderStatus,             // Tag 39 - Current status of order
    val side: TradingSide,                  // Tag 54 - Side of order
    val leavesQty: BetDecimal,              // Tag 151 - Quantity open for further execution
    val cumQty: BetDecimal,                 // Tag 14 - Total quantity filled
    
    // Conditional Required Fields
    val instrument: Instrument,             // Component - Instrument identification
    val lastQty: BetDecimal? = null,        // Tag 32 - Quantity of shares bought/sold
    val lastPx: BetDecimal? = null,         // Tag 31 - Price of this execution
    
    // Optional Common Fields
    val clOrdID: String? = null,            // Tag 11 - Client Order ID
    val origClOrdID: String? = null,        // Tag 41 - Original Client Order ID
    val execRefID: String? = null,          // Tag 19 - Reference identifier for execution
    val execTransType: ExecTransType? = null, // Tag 20 - Transaction type
    val orderQty: BetDecimal? = null,       // Tag 38 - Quantity ordered
    val ordType: OrderType? = null,         // Tag 40 - Order type
    val price: BetDecimal? = null,          // Tag 44 - Price per unit of quantity
    val stopPx: BetDecimal? = null,         // Tag 99 - Stop price
    val timeInForce: TimeInForce? = null,   // Tag 59 - Specifies how long the order remains in effect
    val avgPx: BetDecimal? = null,          // Tag 6 - Calculated average price of all fills
    val ordRejReason: String? = null,       // Tag 103 - Reason order was rejected
    val text: String? = null,               // Tag 58 - Free format text string
    val transactTime: BetLocalDateTime? = null, // Tag 60 - Time of execution/order creation
    val settlDate: String? = null,          // Tag 64 - Settlement date (YYYYMMDD)
    val tradeDate: String? = null,          // Tag 75 - Trade date (YYYYMMDD)
    
    // Commission and Settlement Fields
    val commission: BetDecimal? = null,     // Tag 12 - Commission charged
    val commType: CommissionType? = null,   // Tag 13 - Commission type
    val commCurrency: String? = null,       // Tag 479 - Commission currency
    val fundRenewWaiv: String? = null,      // Tag 497 - Fund renewal waiver
    val grossTradeAmt: BetDecimal? = null,  // Tag 381 - Total amount traded
    val netMoney: BetDecimal? = null,       // Tag 118 - Net money
    val settlCurrency: String? = null,      // Tag 120 - Currency used for settlement
    val settlCurrAmt: BetDecimal? = null,   // Tag 119 - Amount of settlement currency
    val settlCurrFxRate: BetDecimal? = null, // Tag 155 - Settlement currency FX rate
    
    // Market Data and Execution Details
    val lastSpotRate: String? = null,       // Tag 194 - Last spot rate
    val lastForwardPoints: String? = null,  // Tag 195 - Last forward points
    val lastMkt: String? = null,            // Tag 30 - Market of execution
    val tradingSessionID: String? = null,   // Tag 336 - Trading session identifier
    val tradingSessionSubID: String? = null, // Tag 625 - Trading session sub-identifier
    val lastCapacity: String? = null,       // Tag 29 - Capacity of customer placing the order
    val orderCapacity: OrderCapacity? = null, // Tag 528 - Capacity of the order
    val lastLiquidityIndicator: LastLiquidityIndicator? = null, // Tag 851 - Liquidity indicator
    
    // Timing Fields
    val orderExpireDate: String? = null,    // Tag 432 - Order expiration date (YYYYMMDD)
    val expireTime: BetLocalDateTime? = null,  // Tag 126 - Time/Date of order expiration
    val effectiveTime: BetLocalDateTime? = null, // Tag 168 - Effective time
    val maturityDate: String? = null,       // Tag 541 - Instrument maturity date
    
    // Account and Party Information
    val account: String? = null,            // Tag 1 - Account mnemonic
    val accountType: String? = null,        // Tag 581 - Account type
    val custOrderCapacity: String? = null,  // Tag 582 - Customer order capacity
    val clearingFirm: String? = null,       // Tag 439 - Clearing firm
    val clearingAccount: String? = null,    // Tag 440 - Clearing account
    
    // Multi-leg and Strategy Fields
    val multiLegReportingType: String? = null, // Tag 442 - Multi-leg reporting type
    val legRefID: String? = null,           // Tag 654 - Reference ID for leg
    
    // Execution Instructions and Handling
    val execInst: String? = null,           // Tag 18 - Instructions for order handling
    val handlInst: String? = null,          // Tag 21 - Handling instructions
    val minQty: BetDecimal? = null,         // Tag 110 - Minimum quantity
    val maxFloor: BetDecimal? = null,       // Tag 111 - Maximum floor quantity
    val displayQty: BetDecimal? = null,     // Tag 1138 - Display quantity
    val refreshQty: BetDecimal? = null,     // Tag 1088 - Refresh quantity
    
    // Regulatory and Compliance
    val complianceID: String? = null,       // Tag 376 - Compliance ID
    val solicitedFlag: Boolean? = null,     // Tag 377 - Solicited flag
    val execPriceType: String? = null,      // Tag 484 - Execution price type
    val execPriceAdjustment: BetDecimal? = null, // Tag 485 - Execution price adjustment
    val priorityIndicator: String? = null,  // Tag 638 - Priority indicator
    val priceImprovement: BetDecimal? = null, // Tag 639 - Price improvement
    
    // Additional FIX 5.0 Fields
    val secondaryOrderID: String? = null,   // Tag 198 - Secondary order ID  
    val orderCategory: String? = null,      // Tag 1115 - Order category
    val origCrossID: String? = null,        // Tag 551 - Original cross ID
    val crossID: String? = null,            // Tag 548 - Cross ID
    val crossType: String? = null,          // Tag 549 - Cross type
    val execRestatementReason: String? = null, // Tag 378 - Execution restatement reason
    
    // Extensibility
    val metadata: Map<String, Any> = emptyMap()
)