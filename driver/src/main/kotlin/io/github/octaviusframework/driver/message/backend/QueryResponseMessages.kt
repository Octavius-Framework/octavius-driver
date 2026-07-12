package io.github.octaviusframework.driver.message.backend

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

class RowDescriptionMessage(val fields: List<FieldDescription>) : BackendMessage {
    override fun toString(): String = "RowDescription(fields=${fields.size})"

    class FieldDescription(
        val name: String,
        val tableOid: Int,
        val columnAttrNumber: Short,
        val dataTypeOid: Int,
        val dataTypeSize: Short,
        val typeModifier: Int,
        val formatCode: Short
    )
}

class DataRowMessage(
    val rawData: ByteArray,
    val columnOffsets: IntArray,
    val columnLengths: IntArray
) : BackendMessage {
    override fun toString(): String = "DataRow(columns=${columnOffsets.size})"
}
