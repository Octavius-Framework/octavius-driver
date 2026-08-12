package io.github.octaviusframework.driver.copy

/**
 * Base interface for COPY operations.
 */
interface CopyOperation : AutoCloseable {
    /**
     * Returns true if the copy operation is still active.
     */
    val isActive: Boolean

    /**
     * Cancels the copy operation.
     */
    fun cancelCopy()

    override fun close() {
        if (isActive) {
            cancelCopy()
        }
    }
}


