package com.fbg.api.fix.integration

import com.fbg.api.fix.domain.BetFanaticsOrderState
import com.fbg.api.fix.enums.ExecutionType

// Clean mapper that only depends on core enums
class OrderStateMapper {
    companion object {
        fun fromFix(execTypeChar: Char, ordStatusChar: Char): BetFanaticsOrderState? {
            val execType = executionTypeFromFix(execTypeChar)
            // Add your mapping logic here
            return when (execType) {
                ExecutionType.NEW -> BetFanaticsOrderState.NEW
                ExecutionType.PARTIAL_FILL -> BetFanaticsOrderState.PARTIALLY_FILLED
                ExecutionType.FILL -> BetFanaticsOrderState.FILLED
                ExecutionType.CANCELED -> BetFanaticsOrderState.CANCELLED
                ExecutionType.REJECTED -> BetFanaticsOrderState.REJECTED
                ExecutionType.EXPIRED -> BetFanaticsOrderState.EXPIRED
                ExecutionType.SUSPENDED -> BetFanaticsOrderState.SUSPENDED
                ExecutionType.REPLACED -> BetFanaticsOrderState.REPLACED
                else -> BetFanaticsOrderState.ERROR
            }
        }
    }
}