package io.github.octaviusframework.driver.type.range

import kotlin.reflect.KClass

/**
 * Represents a generic, user-friendly PostgreSQL multirange.
 * This class is designed to be used in application code, mapping directly to/from [io.github.octaviusframework.driver.container.PgMultirange].
 *
 * @param T The type of the range bounds.
 */
data class MultiRange<T : Any>(
    val elementClass: KClass<T>,
    val ranges: List<Range<T>>
) {
    constructor(elementClass: KClass<T>, vararg ranges: Range<T>) : this(elementClass, ranges.toList())

    companion object {
        /**
         * Creates an empty multirange.
         */
        inline fun <reified T : Any> empty(): MultiRange<T> = MultiRange(T::class, emptyList())
    }
}

/**
 * Creates a multirange.
 */
inline fun <reified T : Any> multiRangeOf(vararg ranges: Range<T>): MultiRange<T> = MultiRange(T::class, ranges.toList())

/**
 * Creates a multirange.
 */
inline fun <reified T : Any> multiRangeOf(ranges: List<Range<T>>): MultiRange<T> = MultiRange(T::class, ranges)
