package io.github.octaviusframework.driver.type.containter

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

    /**
     * Converts multirange to a list of all bounds (if they are of the same type).
     */
    inline fun <reified T> extractAllBounds(): List<T?> {
        val bounds = mutableListOf<T?>()
        for (range in ranges) {
            bounds.add(range.lowerBound<T>())
            bounds.add(range.upperBound<T>())
        }
        return bounds
    }

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