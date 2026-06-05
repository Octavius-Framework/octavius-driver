package io.github.octaviusframework.containter

/**
 * Reprezentuje Multirange, czyli zbiór niekolidujących się zakresów (PgRange).
 */
class PgMultirange internal constructor(
    val ranges: List<PgRange>
) {
    val size: Int get() = ranges.size
    
    operator fun get(index: Int): PgRange = ranges[index]
    
    fun toList(): List<PgRange> = ranges
}