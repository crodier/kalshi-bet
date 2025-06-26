package com.fbg.api.fix.enums

enum class TradingSide(val fixValue: Char) {
    BUY('1'),
    SELL('2'),
    BUY_MINUS('3'),
    SELL_PLUS('4'),
    SELL_SHORT('5'),
    SELL_SHORT_EXEMPT('6'),
    CROSS('8'),
    CROSS_SHORT('9'),
    CROSS_SHORT_EXEMPT('A')
}