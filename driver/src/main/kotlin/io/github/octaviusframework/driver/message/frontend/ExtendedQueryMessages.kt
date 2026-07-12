package io.github.octaviusframework.driver.message.frontend

import io.github.octaviusframework.driver.io.PgOutputStream
import java.nio.charset.StandardCharsets

internal class ParseMessage(
    private val statementName: String,
    private val query: String,
    private val parameterTypes: List<Int> = emptyList() // List of parameter OIDs (can be empty to be inferred by Postgres)
) : FrontendMessage {
    override fun encode(out: PgOutputStream) {
        val nameBytes = statementName.toByteArray(StandardCharsets.UTF_8)
        val queryBytes = query.toByteArray(StandardCharsets.UTF_8)
        val length = 4 + nameBytes.size + 1 + queryBytes.size + 1 + 2 + (parameterTypes.size * 4)

        out.writeByte('P'.code.toByte())
        out.writeInt(length)
        out.writeCString(statementName)
        out.writeCString(query)
        out.writeShort(parameterTypes.size)
        parameterTypes.forEach { out.writeInt(it) }
    }
}

internal class BindMessage(
    private val portalName: String,
    private val statementName: String,
    private val parameterCount: Int,
    private val parameterValues: ByteArray, // Pre-serialized values including lengths
    private val parameterFormats: List<Int>, // 0 for text, 1 for binary (for each parameter, or one for all)
    private val resultFormats: List<Int> = listOf(1) // default to binary for all
) : FrontendMessage {
    override fun encode(out: PgOutputStream) {
        val portalBytes = portalName.toByteArray(StandardCharsets.UTF_8)
        val statementBytes = statementName.toByteArray(StandardCharsets.UTF_8)

        val length = 4 + portalBytes.size + 1 + statementBytes.size + 1 +
                2 + (parameterFormats.size * 2) +
                2 + parameterValues.size +
                2 + (resultFormats.size * 2)

        out.writeByte('B'.code.toByte())
        out.writeInt(length)
        out.writeCString(portalName)
        out.writeCString(statementName)

        // Parameter formats
        out.writeShort(parameterFormats.size)
        parameterFormats.forEach { out.writeShort(it) }

        // Parameter values
        out.writeShort(parameterCount)
        if (parameterValues.isNotEmpty()) {
            out.writeBytes(parameterValues)
        }

        // Result formats
        out.writeShort(resultFormats.size)
        resultFormats.forEach { out.writeShort(it) }
    }
}

internal class DescribeMessage(private val targetType: Char /* 'S' dla Statement, 'P' dla Portal */, private val name: String) :
    FrontendMessage {
    override fun encode(out: PgOutputStream) {
        val nameBytes = name.toByteArray(StandardCharsets.UTF_8)
        val length = 4 + 1 + nameBytes.size + 1

        out.writeByte('D'.code.toByte())
        out.writeInt(length)
        out.writeByte(targetType.code.toByte())
        out.writeCString(name)
    }
}

internal class ExecuteMessage(private val portalName: String, private val maxRows: Int = 0) : FrontendMessage {
    override fun encode(out: PgOutputStream) {
        val portalBytes = portalName.toByteArray(StandardCharsets.UTF_8)
        val length = 4 + portalBytes.size + 1 + 4

        out.writeByte('E'.code.toByte())
        out.writeInt(length)
        out.writeCString(portalName)
        out.writeInt(maxRows)
    }
}

internal class SyncMessage : FrontendMessage {
    override fun encode(out: PgOutputStream) {
        out.writeByte('S'.code.toByte())
        out.writeInt(4)
    }
}


