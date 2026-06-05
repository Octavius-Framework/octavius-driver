package io.github.octaviusframework.network.messages

import io.github.octaviusframework.io.ByteArrayWindow

object ParseCompleteMessage : BackendMessage {
    override fun toString(): String = "ParseComplete"
}

object EmptyQueryResponseMessage : BackendMessage {
    override fun toString(): String = "EmptyQueryResponse"
}

object BindCompleteMessage : BackendMessage {
    override fun toString(): String = "BindComplete"
}

object NoDataMessage : BackendMessage {
    override fun toString(): String = "NoData"
}

class CommandCompleteMessage(val tag: String) : BackendMessage {
    override fun toString(): String = "CommandComplete(tag=$tag)"
}

class RowDescriptionMessage(val fields: List<FieldDescription>) : BackendMessage {
    override fun toString(): String = "RowDescription(fields=${fields.size})"
    
    class FieldDescription(
        val name: String,
        val tableOid: UInt,
        val columnAttrNumber: Short,
        val dataTypeOid: UInt,
        val dataTypeSize: Short,
        val typeModifier: Int,
        val formatCode: Short
    )
}

class DataRowMessage(val columns: List<ByteArrayWindow?>) : BackendMessage {
    override fun toString(): String = "DataRow(columns=${columns.size})"
}
