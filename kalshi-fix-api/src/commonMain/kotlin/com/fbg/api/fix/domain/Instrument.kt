package com.fbg.api.fix.domain

import com.fbg.api.common.BetDecimal

/**
 * Instrument using BetDecimal for multiplatform compatibility
 */
data class Instrument(
    val symbol: String,                     // Tag 55 - Ticker symbol
    val symbolSfx: String? = null,          // Tag 65 - Symbol suffix
    val securityID: String? = null,         // Tag 48 - Security identifier
    val securityIDSource: String? = null,   // Tag 22 - Security identifier source
    val securityType: String? = null,       // Tag 167 - Security type
    val maturityMonthYear: String? = null,  // Tag 200 - Maturity month year
    val maturityDate: String? = null,       // Tag 541 - Maturity date
    val strikePrice: BetDecimal? = null,    // Tag 202 - Strike price
    val putOrCall: String? = null,          // Tag 201 - Put or call indicator
    val contractMultiplier: BetDecimal? = null, // Tag 231 - Contract multiplier
    val couponRate: BetDecimal? = null,     // Tag 223 - Coupon rate
    val securityExchange: String? = null,   // Tag 207 - Security exchange
    val issuer: String? = null,             // Tag 106 - Issuer
    val securityDesc: String? = null,       // Tag 107 - Security description
    val currency: String? = null            // Tag 15 - Currency
)