package io.github.octaviusframework.driver.message.backend

import io.github.octaviusframework.driver.row.FieldDescription

internal object ParseCompleteMessage : BackendMessage {
    override fun toString(): String = "ParseComplete"
}

internal object EmptyQueryResponseMessage : BackendMessage {
    override fun toString(): String = "EmptyQueryResponse"
}

internal object BindCompleteMessage : BackendMessage {
    override fun toString(): String = "BindComplete"
}

internal object NoDataMessage : BackendMessage {
    override fun toString(): String = "NoData"
}

internal class CommandCompleteMessage(val tag: String) : BackendMessage {
    override fun toString(): String = "CommandComplete(tag=$tag)"
}

internal class RowDescriptionMessage(val fields: List<FieldDescription>) : BackendMessage {
    override fun toString(): String = "RowDescription(fields=${fields.size})"
}

class DataRowMessage(
    val rawData: ByteArray,
    val columnOffsets: IntArray,
    val columnLengths: IntArray
) : BackendMessage {
    override fun toString(): String = "DataRow(columns=${columnOffsets.size})"
}

internal object PortalSuspendedMessage : BackendMessage {
    override fun toString(): String = "PortalSuspended"
}
