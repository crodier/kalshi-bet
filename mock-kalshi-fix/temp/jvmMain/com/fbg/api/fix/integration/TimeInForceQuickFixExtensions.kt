@file:JvmName("TimeInForceQuickFixExtensions")

package com.fbg.api.fix.integration

import com.fbg.api.fix.enums.TimeInForce
import quickfix.field.TimeInForce as QuickFixTimeInForce

fun TimeInForce.toQuickfix(): QuickFixTimeInForce = QuickFixTimeInForce(fixValue)

// Standalone functions instead of companion extensions
fun timeInForceFromQuickfix(tif: QuickFixTimeInForce): TimeInForce? = timeInForceFromFix(tif.value)

fun timeInForceFromFix(fixValue: Char): TimeInForce? =
    TimeInForce.entries.find { it.fixValue == fixValue }