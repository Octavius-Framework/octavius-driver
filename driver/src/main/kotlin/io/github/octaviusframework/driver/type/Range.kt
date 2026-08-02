package io.github.octaviusframework.driver.type

import kotlin.reflect.KClass

/**
 * Represents a generic, user-friendly PostgreSQL range.
 * This class is designed to be used in application code, mapping directly to/from [io.github.octaviusframework.driver.container.PgRange].
 *
 * @param T The type of the range bounds (e.g., Int, LocalDate, or a data class).
 */
data class Range<T : Any>(
    val elementClass: KClass<T>,
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
        inline fun <reified T : Any> empty(): Range<T> = Range(
            elementClass = T::class,
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

/**
 * Creates a range.
 */
inline fun <reified T : Any> rangeOf(
    lowerBound: T? = null,
    upperBound: T? = null,
    isLowerInclusive: Boolean = true,
    isUpperInclusive: Boolean = false,
    isLowerInfinite: Boolean = lowerBound == null,
    isUpperInfinite: Boolean = upperBound == null,
    isLowerNull: Boolean = false,
    isUpperNull: Boolean = false
): Range<T> = Range(
    elementClass = T::class,
    lowerBound = lowerBound,
    upperBound = upperBound,
    isLowerInclusive = isLowerInclusive,
    isUpperInclusive = isUpperInclusive,
    isLowerInfinite = isLowerInfinite,
    isUpperInfinite = isUpperInfinite,
    isLowerNull = isLowerNull,
    isUpperNull = isUpperNull,
    isEmpty = false
)
