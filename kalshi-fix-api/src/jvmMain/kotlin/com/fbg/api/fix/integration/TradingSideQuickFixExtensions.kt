@file:JvmName("TradingSideQuickFixExtensions")

package com.fbg.api.fix.integration

import com.fbg.api.fix.enums.TradingSide
import quickfix.field.Side

fun TradingSide.toQuickfix(): Side = Side(fixValue)

// Standalone functions instead of companion extensions
fun tradingSideFromQuickfix(side: Side): TradingSide? = tradingSideFromFix(side.value)

fun tradingSideFromFix(fixValue: Char): TradingSide? = 
    TradingSide.entries.find { it.fixValue == fixValue }