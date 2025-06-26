
@file:JvmName("FbgOrderStateMapperQuickFixExtensions")

package com.fbg.api.fix.integration

import com.fbg.api.fix.domain.BetFanaticsOrderState
import com.fbg.api.fix.integration.OrderStateMapper
import quickfix.field.ExecType
import quickfix.field.OrdStatus

fun OrderStateMapper.Companion.fromQuickFix(execType: ExecType, ordStatus: OrdStatus): BetFanaticsOrderState? {
    return fromFix(execType.value, ordStatus.value)
}