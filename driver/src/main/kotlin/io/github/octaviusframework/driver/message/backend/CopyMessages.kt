package io.github.octaviusframework.driver.message.backend

/**
 * Response indicating that the backend is ready to receive COPY IN data.
 */
internal class CopyInResponseMessage(
    val format: Byte,
    val numColumns: Short,
    val columnFormats: List<Short>
) : BackendMessage {
    override fun toString(): String = "CopyInResponse(format=$format, numColumns=$numColumns)"
}

/**
 * Response indicating that the backend is ready to send COPY OUT data.
 */
internal class CopyOutResponseMessage(
    val format: Byte,
    val numColumns: Short,
    val columnFormats: List<Short>
) : BackendMessage {
    override fun toString(): String = "CopyOutResponse(format=$format, numColumns=$numColumns)"
}

/**
 * Response indicating that the backend is ready for COPY BOTH.
 */
internal class CopyBothResponseMessage(
    val format: Byte,
    val numColumns: Short,
    val columnFormats: List<Short>
) : BackendMessage {
    override fun toString(): String = "CopyBothResponse(format=$format, numColumns=$numColumns)"
}

/**
 * Contains data during a COPY operation (received from the backend).
 */
internal class BackendCopyDataMessage(val data: ByteArray) : BackendMessage {
    override fun toString(): String = "CopyData(size=${data.size})"
}

/**
 * Indicates completion of a COPY operation by the backend.
 */
internal object BackendCopyDoneMessage : BackendMessage {
    override fun toString(): String = "CopyDone"
}
