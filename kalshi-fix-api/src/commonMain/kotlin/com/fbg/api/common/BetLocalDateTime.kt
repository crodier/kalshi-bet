package com.fbg.api.common

/**
 * A multiplatform-safe date/time type that wraps an ISO 8601 string value.
 * Provides type safety and clear intent while being compatible with both JVM and JS.
 * 
 * Format: YYYY-MM-DDTHH:MM:SS[.sss][Z|+HH:MM|-HH:MM]
 * Examples:
 * - "2024-06-24T10:30:00Z" (UTC)
 * - "2024-06-24T10:30:00-04:00" (Eastern Time)
 * - "2024-06-24T10:30:00.123Z" (with milliseconds)
 */
data class BetLocalDateTime(val value: String) {
    
    val date: String get() = value.substring(0, 10)  // YYYY-MM-DD
    val time: String get() = value.substring(11, 19)  // HH:MM:SS
}