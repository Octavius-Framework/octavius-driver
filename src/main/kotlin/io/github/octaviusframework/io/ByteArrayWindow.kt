package io.github.octaviusframework.io

/**
 * Reprezentuje wycinek głównego bufora wiersza.
 * Zero kopiowania bajtów!
 */
class ByteArrayWindow(
    val data: ByteArray,
    val offset: Int,
    val length: Int
) {
    /**
     * Tworzy nowe "pod-okno" dla zagnieżdżonych struktur (np. tablicy w kompozycie).
     */
    fun slice(relativeOffset: Int, sliceLength: Int): ByteArrayWindow {
        require(relativeOffset + sliceLength <= length) { "Slice out of bounds" }
        return ByteArrayWindow(data, this.offset + relativeOffset, sliceLength)
    }
}
