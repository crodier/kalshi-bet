@file:JvmName("OrderTypeQuickFixExtensions")

package com.fbg.api.fix.integration

import com.fbg.api.fix.enums.OrderType
import quickfix.field.OrdType

fun OrderType.toQuickfix(): OrdType = OrdType(fixValue)

// Standalone functions instead of companion extensions
fun orderTypeFromQuickfix(ordType: OrdType): OrderType? = orderTypeFromFix(ordType.value)

fun orderTypeFromFix(fixValue: Char): OrderType? =
    OrderType.entries.find { it.fixValue == fixValue }