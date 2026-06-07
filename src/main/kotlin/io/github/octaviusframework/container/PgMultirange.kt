package io.github.octaviusframework.container

/**
 * Reprezentuje strukturę multirange z bazy danych.
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
     * Konwertuje multirange do listy wszystkich granic (jeśli są tego samego typu).
     */
    inline fun <reified T> extractAllBounds(): List<T?> {
        val bounds = mutableListOf<T?>()
        for (range in ranges) {
            bounds.add(range.lowerBound<T>())
            bounds.add(range.upperBound<T>())
        }
        return bounds
    }
}