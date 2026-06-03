package io.github.octaviusframework.network.messages

import io.github.octaviusframework.io.PgOutputStream
import java.nio.charset.StandardCharsets

class ParseMessage(
    private val statementName: String,
    private val query: String,
    private val parameterTypes: List<Int> = emptyList() // Lista OID-ów parametrów (może być pusta do odgadnięcia przez Postgresa)
) : FrontendMessage {
    override fun encode(out: PgOutputStream) {
        val nameBytes = statementName.toByteArray(StandardCharsets.UTF_8)
        val queryBytes = query.toByteArray(StandardCharsets.UTF_8)
        val length = 4 + nameBytes.size + 1 + queryBytes.size + 1 + 2 + (parameterTypes.size * 4)

        out.writeByte('P'.code.toByte())
        out.writeInt(length)
        out.writeCString(statementName)
        out.writeCString(query)
        out.writeShort(parameterTypes.size.toShort())
        parameterTypes.forEach { out.writeInt(it) }
    }
}

class BindMessage(
    private val portalName: String,
    private val statementName: String,
    private val parameterValues: List<ByteArray?>, // null oznacza NULL w bazie
    private val parameterFormats: List<Short>, // 0 dla tekstu, 1 dla binarki (dla każdego parametru, albo jeden dla wszystkich)
    private val resultFormats: List<Short> = listOf(1) // domyślnie chcemy wszystko w binarce
) : FrontendMessage {
    override fun encode(out: PgOutputStream) {
        val portalBytes = portalName.toByteArray(StandardCharsets.UTF_8)
        val statementBytes = statementName.toByteArray(StandardCharsets.UTF_8)

        var parametersLength = 0
        parameterValues.forEach { if (it != null) parametersLength += 4 + it.size else parametersLength += 4 }

        val length = 4 + portalBytes.size + 1 + statementBytes.size + 1 +
                2 + (parameterFormats.size * 2) +
                2 + parametersLength +
                2 + (resultFormats.size * 2)

        out.writeByte('B'.code.toByte())
        out.writeInt(length)
        out.writeCString(portalName)
        out.writeCString(statementName)

        // Formaty parametrów
        out.writeShort(parameterFormats.size.toShort())
        parameterFormats.forEach { out.writeShort(it) }

        // Wartości parametrów
        out.writeShort(parameterValues.size.toShort())
        parameterValues.forEach { value ->
            if (value == null) {
                out.writeInt(-1)
            } else {
                out.writeInt(value.size)
                out.writeBytes(value)
            }
        }

        // Formaty wyników
        out.writeShort(resultFormats.size.toShort())
        resultFormats.forEach { out.writeShort(it) }
    }
}

class DescribeMessage(private val targetType: Char /* 'S' dla Statement, 'P' dla Portal */, private val name: String) : FrontendMessage {
    override fun encode(out: PgOutputStream) {
        val nameBytes = name.toByteArray(StandardCharsets.UTF_8)
        val length = 4 + 1 + nameBytes.size + 1

        out.writeByte('D'.code.toByte())
        out.writeInt(length)
        out.writeByte(targetType.code.toByte())
        out.writeCString(name)
    }
}

class ExecuteMessage(private val portalName: String, private val maxRows: Int = 0) : FrontendMessage {
    override fun encode(out: PgOutputStream) {
        val portalBytes = portalName.toByteArray(StandardCharsets.UTF_8)
        val length = 4 + portalBytes.size + 1 + 4

        out.writeByte('E'.code.toByte())
        out.writeInt(length)
        out.writeCString(portalName)
        out.writeInt(maxRows)
    }
}

class SyncMessage : FrontendMessage {
    override fun encode(out: PgOutputStream) {
        out.writeByte('S'.code.toByte())
        out.writeInt(4)
    }
}
