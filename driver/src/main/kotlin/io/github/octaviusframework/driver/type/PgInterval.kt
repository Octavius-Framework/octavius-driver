package io.github.octaviusframework.driver.type

/**
 * Represents a PostgreSQL interval type.
 *
 * @property time The time part of the interval in microseconds.
 * @property days The number of days in the interval.
 * @property months The number of months in the interval.
 */
data class PgInterval(
    val time: Long,
    val days: Int,
    val months: Int
)
