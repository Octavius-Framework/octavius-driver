package io.github.octaviusframework.driver.type

/**
 * Represents a generic, user-friendly PostgreSQL multirange.
 * This class is designed to be used in application code, mapping directly to/from [io.github.octaviusframework.driver.container.PgMultirange].
 *
 * @param T The type of the range bounds.
 */
data class MultiRange<T>(
    val ranges: List<Range<T>>
) {
    constructor(vararg ranges: Range<T>) : this(ranges.toList())

    companion object {
        /**
         * Creates an empty multirange.
         */
        fun <T> empty(): MultiRange<T> = MultiRange(emptyList())
    }
}
