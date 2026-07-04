package io.github.octaviusframework.driver.type.container

/**
 * Represents a multirange structure from the database.
 */
class PgMultirange internal constructor(
    val multirangeOid: UInt,
    val rangeOid: UInt,
    val ranges: List<PgRange>
) : PgContainer {
    override fun detach() {
        ranges.forEach { it.detach() }
    }

    val size: Int get() = ranges.size

    operator fun get(index: Int): PgRange = ranges[index]

    fun toList(): List<PgRange> = ranges

    companion object {
        fun create(
            multirangeOid: UInt,
            rangeOid: UInt,
            ranges: List<PgRange>
        ): PgMultirange {
            return PgMultirange(multirangeOid, rangeOid, ranges)
        }
    }
}