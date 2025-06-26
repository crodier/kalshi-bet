package com.fbg.fix

import com.fbg.api.fix.integration.OrderStateMapper
import com.fbg.api.fix.enums.ExecutionType
import com.fbg.api.fix.enums.OrderType
import com.fbg.api.fix.enums.TimeInForce
import com.fbg.api.fix.enums.TradingSide
import com.fbg.api.fix.integration.executionTypeFromFix
import com.fbg.api.fix.integration.toQuickfix
import com.fbg.api.fix.integration.tradingSideFromQuickfix
import quickfix.fix44.NewOrderSingle

fun main() {
    // Creating and using the enums
    val side = TradingSide.BUY
    val execType = ExecutionType.NEW
    val orderType = OrderType.LIMIT
    val timeInForce = TimeInForce.DAY

    // Converting to QuickFIX/J fields (JVM only)
    val quickfixSide = side.toQuickfix()
    val quickfixExecType = execType.toQuickfix()
    val quickfixOrderType = orderType.toQuickfix()


    // Converting from QuickFIX/J
    val receivedSide = tradingSideFromQuickfix(quickfixSide)
    val receivedExecType = executionTypeFromFix('0') // NEW

    // Using in your trading API
    data class OrderRequest(
        val side: TradingSide,
        val orderType: OrderType,
        val timeInForce: TimeInForce
    ) {
        fun toNewOrderSingle(): NewOrderSingle {
            val message = NewOrderSingle()
            message.set(side.toQuickfix())
            message.set(orderType.toQuickfix())
            message.set(timeInForce.toQuickfix())
            return message
        }
    }

    // Test the mapping
    val orderState = OrderStateMapper.fromFix('0', '0') // NEW execution
    println("Order State: $orderState")
}