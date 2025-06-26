
@file:JvmName("ExecutionTypeQuickFixExtensions")

package com.fbg.api.fix.integration

import com.fbg.api.fix.enums.ExecutionType
import quickfix.field.ExecType

fun ExecutionType.toQuickfix(): ExecType = ExecType(fixValue)

// Standalone functions instead of companion extensions
fun executionTypeFromQuickfix(execType: ExecType): ExecutionType? = executionTypeFromFix(execType.value)

fun executionTypeFromFix(fixValue: Char): ExecutionType? =
    ExecutionType.entries.find { it.fixValue == fixValue }