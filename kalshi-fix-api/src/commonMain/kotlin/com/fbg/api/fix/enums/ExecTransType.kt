
package com.fbg.api.fix.enums

enum class ExecTransType(val fixValue: Char) {
    NEW('0'),
    CANCEL('1'),
    CORRECT('2'),
    STATUS('3')
}