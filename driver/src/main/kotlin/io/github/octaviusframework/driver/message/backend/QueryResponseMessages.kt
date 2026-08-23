package io.github.octaviusframework.driver.message.backend

/**
 * Response indicating that a Parse command has completed successfully.
 */
internal object ParseCompleteMessage : BackendMessage {
    override fun toString(): String = "ParseComplete"
}

/**
 * Response indicating that an empty query string was sent to the backend.
 */
internal object EmptyQueryResponseMessage : BackendMessage {
    override fun toString(): String = "EmptyQueryResponse"
}

/**
 * Response indicating that a Bind command has completed successfully.
 */
internal object BindCompleteMessage : BackendMessage {
    override fun toString(): String = "BindComplete"
}

/**
 * Response indicating that a query returned no data.
 */
internal object NoDataMessage : BackendMessage {
    override fun toString(): String = "NoData"
}

/**
 * Response indicating that an SQL command has completed successfully.
 *
 * @property tag The command tag string from the server.
 */
internal class CommandCompleteMessage(val tag: String) : BackendMessage {
    override fun toString(): String = "CommandComplete(tag=$tag)"
}

/**
 * Response containing the description of rows returned by a query.
 *
 * @property fields List of field descriptions for the returned rows.
 */
internal class RowDescriptionMessage(val fields: List<FieldDescription>) : BackendMessage {
    override fun toString(): String = "RowDescription(fields=${fields.size})"
}

/**
 * Represents a single row of data from a query response.
 * Note: The underlying arrays (`rawData`, `columnOffsets`, `columnLengths`) may be shared buffers 
 * reused by the connection to avoid memory allocations. They should be processed synchronously 
 * (e.g. eagerly deserialized) and not stored for later use, as their contents will be overwritten 
 * by subsequent rows unless the row data size exceeded the configured `maxCachedRowSize`.
 */
internal class DataRowMessage(
    val rawData: ByteArray,
    val columnOffsets: IntArray,
    val columnLengths: IntArray
) : BackendMessage {
    override fun toString(): String = "DataRow(columns=${columnOffsets.size})"
}

/**
 * Response indicating that portal execution was suspended because the row limit was reached.
 */
internal object PortalSuspendedMessage : BackendMessage {
    override fun toString(): String = "PortalSuspended"
}
