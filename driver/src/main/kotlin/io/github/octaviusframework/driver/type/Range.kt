package io.github.octaviusframework.driver.type

/**
 * Represents a generic, user-friendly PostgreSQL range.
 * This class is designed to be used in application code, mapping directly to/from [io.github.octaviusframework.driver.container.PgRange].
 *
 * @param T The type of the range bounds (e.g., Int, LocalDate, or a data class).
 */
data class Range<T>(
    val lowerBound: T? = null,
    val upperBound: T? = null,
    val isLowerInclusive: Boolean = true,
    val isUpperInclusive: Boolean = false,
    val isLowerInfinite: Boolean = lowerBound == null,
    val isUpperInfinite: Boolean = upperBound == null,
    val isLowerNull: Boolean = false,
    val isUpperNull: Boolean = false,
    val isEmpty: Boolean = false
) {
    companion object {
        /**
         * Creates an empty range.
         */
        fun <T> empty(): Range<T> = Range(
            lowerBound = null,
            upperBound = null,
            isLowerInclusive = false,
            isUpperInclusive = false,
            isLowerInfinite = false,
            isUpperInfinite = false,
            isLowerNull = false,
            isUpperNull = false,
            isEmpty = true
        )
    }
}
