package io.github.octaviusframework.driver.container

/**
 * Represents a multirange structure from the database.
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
