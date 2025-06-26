
package com.fbg.api.fix.enums

enum class CommissionType(val fixValue: Char) {
    PER_UNIT('1'),
    PERCENT('2'),
    ABSOLUTE('3'),
    PERCENTAGE_WAIVED_CASH_DISCOUNT('4'),
    PERCENTAGE_WAIVED_ENHANCED_UNITS('5'),
    POINTS_PER_BOND_OR_CONTRACT('6')
}