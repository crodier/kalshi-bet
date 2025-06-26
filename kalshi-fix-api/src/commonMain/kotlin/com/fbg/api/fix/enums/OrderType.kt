package com.fbg.api.fix.enums

enum class OrderType(val fixValue: Char) {
    MARKET('1'),
    LIMIT('2'),
    STOP('3'),
    STOP_LIMIT('4'),
    MARKET_ON_CLOSE('5'),
    WITH_OR_WITHOUT('6'),
    LIMIT_OR_BETTER('7'),
    LIMIT_WITH_OR_WITHOUT('8'),
    ON_BASIS('9'),
    ON_CLOSE('A'),
    PEGGED('P'),
    PREVIOUSLY_QUOTED('D')
}