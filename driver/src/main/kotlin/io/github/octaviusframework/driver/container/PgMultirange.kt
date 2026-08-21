package io.github.octaviusframework.driver.container

import io.github.octaviusframework.driver.exception.MappingException
import io.github.octaviusframework.driver.exception.MappingExceptionReason

/**
 * Represents a PostgreSQL multirange structure from the database.
 *
 * @property multirangeOid OID of the multirange type.
 * @property rangeOid OID of the base range type contained in this multirange.
 * @property ranges List of ranges contained in this multirange.
 */
class PgMultirange internal constructor(
    val multirangeOid: Int,
    val rangeOid: Int,
    val ranges: List<PgRange>
) : PgContainer {
    override val containerOid: Int get() = multirangeOid

    /** How many ranges this multirange holds. */
    val size: Int get() = ranges.size

    /**
     * Returns the range at [index].
     *
     * PostgreSQL normalizes a multirange on the way in, merging anything that overlaps or touches and
     * sorting what remains, so the ranges arrive disjoint and in ascending order however they were written.
     *
     * @param index Zero-based index.
     * @return The range at that position.
     * @throws MappingException `COLUMN_NOT_FOUND` if [index] is outside [ranges].
     */
    operator fun get(index: Int): PgRange {
        if (index !in ranges.indices) throw MappingException(
            MappingExceptionReason.COLUMN_NOT_FOUND,
            details = "Range index out of bounds: $index (multirange holds ${ranges.size} ranges)"
        )
        return ranges[index]
    }

    /**
     * Returns the ranges as a list.
     *
     * @return [ranges] itself, not a copy.
     */
    fun toList(): List<PgRange> = ranges

    companion object {
        /**
         * Creates a multirange from ranges that are already of the right range type.
         *
         * Nothing is normalized or validated here; the server does that when the value reaches it.
         *
         * @param multirangeOid OID of the multirange type.
         * @param rangeOid OID of the range type it contains.
         * @param ranges The ranges to hold.
         * @return The constructed [PgMultirange].
         */
        fun create(
            multirangeOid: Int,
            rangeOid: Int,
            ranges: List<PgRange>
        ): PgMultirange {
            return PgMultirange(multirangeOid, rangeOid, ranges)
        }
    }
}
