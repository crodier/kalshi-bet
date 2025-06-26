
package com.fbg.api.fix.enums

enum class OrderCapacity(val fixValue: Char) {
    AGENCY('A'),
    PROPRIETARY('G'),
    INDIVIDUAL('I'),
    PRINCIPAL('P'),
    RISKLESS_PRINCIPAL('R'),
    AGENT_FOR_OTHER_MEMBER('W')
}