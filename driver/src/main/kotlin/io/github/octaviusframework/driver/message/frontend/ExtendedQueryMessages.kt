package io.github.octaviusframework.driver.message.frontend

import io.github.octaviusframework.driver.io.PgOutputStream
import java.nio.charset.StandardCharsets

/**
 * Message sent to the backend to parse a prepared statement.
 *
 * @property statementName The name of the destination prepared statement (empty for unnamed).
 * @property query The query string to be parsed.
 * @property parameterTypes List of OIDs of the parameter data types.
 */
internal class ParseMessage(
    private val statementName: String,
    private val query: String,
    private val parameterTypes: IntArray = IntArray(0) // List of parameter OIDs (can be empty to be inferred by Postgres)
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

/**
 * Message sent to the backend to bind a prepared statement to a portal.
 *
 * @property portalName The name of the destination portal (empty for unnamed).
 * @property statementName The name of the source prepared statement (empty for unnamed).
 * @property parameterCount The number of parameter values.
 * @property parameterValues Pre-serialized parameter values including their lengths.
 * @property parameterFormats The format codes for the parameters (0 for text, 1 for binary).
 * @property resultFormats The format codes for the result columns.
 */
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

/**
 * Message sent to the backend to describe a statement or portal.
 *
 * @property targetType The type of object to describe ('S' for Statement, 'P' for Portal).
 * @property name The name of the statement or portal to describe.
 */
internal class DescribeMessage(private val targetType: Char, private val name: String) :
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

/**
 * Message sent to the backend to execute a portal.
 *
 * @property portalName The name of the portal to execute (empty for unnamed).
 * @property maxRows The maximum number of rows to return (0 for no limit).
 */
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

/**
 * Message sent to the backend to synchronize the connection state and signal the end of a sequence of extended query messages.
 */
internal class SyncMessage : FrontendMessage {
    override fun encode(out: PgOutputStream) {
        out.writeByte('S'.code.toByte())
        out.writeInt(4)
    }
}

/**
 * Message sent to the backend to force it to flush its output buffer.
 */
internal class FlushMessage : FrontendMessage {
    override fun encode(out: PgOutputStream) {
        out.writeByte('H'.code.toByte())
        out.writeInt(4)
    }
}

/**
 * Message sent to the backend to close a statement or portal.
 *
 * @property targetType The type of object to close ('S' for Statement, 'P' for Portal).
 * @property name The name of the statement or portal to close.
 */
internal class CloseMessage(private val targetType: Char, private val name: String) : FrontendMessage {
    override fun encode(out: PgOutputStream) {
        val nameBytes = name.toByteArray(StandardCharsets.UTF_8)
        val length = 4 + 1 + nameBytes.size + 1
        out.writeByte('C'.code.toByte())
        out.writeInt(length)
        out.writeByte(targetType.code.toByte())
        out.writeCString(name)
    }
}


