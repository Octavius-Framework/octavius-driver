package io.github.octaviusframework.driver.type.range

import kotlin.reflect.KClass

/**
 * Represents a generic, user-friendly PostgreSQL multirange.
 * This class is designed to be used in application code, mapping directly to/from [io.github.octaviusframework.driver.container.PgMultirange].
 *
 * A multirange is an ordered set of non-overlapping ranges: PostgreSQL merges anything that overlaps or
 * touches and sorts what remains, so [ranges] comes back normalized however it was written. The
 * per-range normalization of discrete subtypes described on [Range] applies to each range inside it too.
 *
 * @param T The type of the range bounds.
 * @property elementClass The class of the bounds, needed to resolve the PostgreSQL multirange type.
 * @property ranges The ranges this multirange holds.
 */
data class MultiRange<T : Any>(
    val elementClass: KClass<T>,
    val ranges: List<Range<T>>
) {
    /**
     * Creates a multirange from ranges given as a `vararg`.
     *
     * @param elementClass The class of the bounds.
     * @param ranges The ranges to hold.
     */
    constructor(elementClass: KClass<T>, vararg ranges: Range<T>) : this(elementClass, ranges.toList())

    companion object {
        /**
         * Creates an empty multirange - one holding no ranges at all.
         *
         * @param T The type of the range bounds.
         * @return An empty multirange.
         */
        inline fun <reified T : Any> empty(): MultiRange<T> = MultiRange(T::class, emptyList())
    }
}

/**
 * Creates a multirange, inferring the PostgreSQL multirange type from [T].
 *
 * @param T The type of the range bounds.
 * @param ranges The ranges to hold; they need not be sorted or disjoint.
 * @return The constructed multirange.
 */
inline fun <reified T : Any> multiRangeOf(vararg ranges: Range<T>): MultiRange<T> = MultiRange(T::class, ranges.toList())

/**
 * Creates a multirange, inferring the PostgreSQL multirange type from [T].
 *
 * @param T The type of the range bounds.
 * @param ranges The ranges to hold; they need not be sorted or disjoint.
 * @return The constructed multirange.
 */
inline fun <reified T : Any> multiRangeOf(ranges: List<Range<T>>): MultiRange<T> = MultiRange(T::class, ranges)
