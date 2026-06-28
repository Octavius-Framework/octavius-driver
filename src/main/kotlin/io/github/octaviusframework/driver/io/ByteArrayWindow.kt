package io.github.octaviusframework.driver.io

/**
 * Represents a slice of the main row buffer.
 * Zero byte copying!
 */
class ByteArrayWindow(
    var data: ByteArray,
    var offset: Int,
    val length: Int
) {
    /**
     * Creates a new "sub-window" for nested structures (e.g. array in a composite).
     */
    fun slice(relativeOffset: Int, sliceLength: Int): ByteArrayWindow {
        require(relativeOffset + sliceLength <= length) { "Slice out of bounds" }
        return ByteArrayWindow(data, this.offset + relativeOffset, sliceLength)
    }

    /**
     * Copies the slice to a new, separate array, releasing the reference to the entire buffer.
     */
    fun detach() {
        if (offset == 0 && data.size == length) return
        val newData = data.copyOfRange(offset, offset + length)
        this.data = newData
        this.offset = 0
    }
}
