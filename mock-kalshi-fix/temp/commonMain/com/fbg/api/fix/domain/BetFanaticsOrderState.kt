package com.fbg.api.fix.domain

enum class BetFanaticsOrderState {
    NEW,             // Initial state when order is first created
    PENDING,         // Order is being processed
    PARTIALLY_FILLED,// Order is partially executed
    FILLED,          // Order is fully executed
    CANCELLED,       // Order has been canceled
    REJECTED,        // Order was rejected
    EXPIRED,         // Order has expired
    SUSPENDED,       // Order is temporarily suspended
    REPLACED,        // Order was modified/replaced
    ERROR           // Order is in the error state
}
