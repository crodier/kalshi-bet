package com.fbg.api.common

/**
 * A multiplatform-safe date type that wraps an ISO 8601 date string value (YYYY-MM-DD).
 * Provides type safety and clear intent while being compatible with both JVM and JS.
 * 
 * Format: YYYY-MM-DD
 * Examples:
 * - "2024-06-24"
 * - "2023-12-31"
 * - "2024-01-01"
 */
data class BetLocalDate(val value: String)