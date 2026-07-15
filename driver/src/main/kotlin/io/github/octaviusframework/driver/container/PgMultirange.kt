package io.github.octaviusframework.driver.container

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
    val size: Int get() = ranges.size

    operator fun get(index: Int): PgRange = ranges[index]

    fun toList(): List<PgRange> = ranges

    companion object {
        fun create(
            multirangeOid: Int,
            rangeOid: Int,
            ranges: List<PgRange>
        ): PgMultirange {
            return PgMultirange(multirangeOid, rangeOid, ranges)
        }
    }
}
