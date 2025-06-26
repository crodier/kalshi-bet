
package com.fbg.api.fix.enums

enum class LastLiquidityIndicator(val fixValue: Int) {
    ADDED_LIQUIDITY(1),
    REMOVED_LIQUIDITY(2),
    LIQUIDITY_ROUTED_OUT(3),
    AUCTION(4)
}