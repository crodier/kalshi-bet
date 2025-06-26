package com.fbg.api.common

/**
 * A multiplatform-safe decimal type that wraps a String value.
 * Provides type safety and clear intent while being compatible with both JVM and JS.
 */
data class BetDecimal(val value: String)