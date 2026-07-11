package io.github.octaviusframework.driver.codec.standard

import io.github.octaviusframework.driver.codec.PgByteWriter
import io.github.octaviusframework.driver.codec.TypeCodec

internal object StringCodec : TypeCodec<String> {
    override val pgTypeName = "text"
    override val pgSchema: String = "pg_catalog"
    override val oid: Int = 25
    override val kotlinClass = String::class
    override val isDefaultForKotlinType = true

    override val fromBinary: (ByteArray, Int, Int) -> String = { data, offset, length -> String(data, offset, length, Charsets.UTF_8) }
    override val toBinary: (String, PgByteWriter) -> Unit = { value, writer -> writer.writeBytes(value.toByteArray(Charsets.UTF_8)) }
}

internal object VarcharCodec : TypeCodec<String> {
    override val pgTypeName = "varchar"
    override val pgSchema: String = "pg_catalog"
    override val oid: Int = 1043
    override val kotlinClass = String::class
    override val fromBinary = StringCodec.fromBinary
    override val toBinary = StringCodec.toBinary
}

internal object BpcharCodec : TypeCodec<String> {
    override val pgTypeName = "bpchar"
    override val pgSchema: String = "pg_catalog"
    override val oid: Int = 1042
    override val kotlinClass = String::class
    override val fromBinary = StringCodec.fromBinary
    override val toBinary = StringCodec.toBinary
}

internal object UnknownCodec : TypeCodec<String> {
    override val pgTypeName = "unknown"
    override val pgSchema: String = "pg_catalog"
    override val oid: Int = 705
    override val kotlinClass = String::class
    override val fromBinary = StringCodec.fromBinary
    override val toBinary = StringCodec.toBinary
}


internal object JsonbCodec : TypeCodec<String> {
    override val pgTypeName = "jsonb"
    override val pgSchema: String = "pg_catalog"
    override val oid: Int = 3802
    override val kotlinClass = String::class

    override val fromBinary: (ByteArray, Int, Int) -> String = { data, offset, length ->
        val version = data[offset]
        if (version == 1.toByte()) {
            String(data, offset + 1, length - 1, Charsets.UTF_8)
        } else {
            error("Unsupported jsonb version byte: $version")
        }
    }

    override val toBinary: (String, PgByteWriter) -> Unit = { value, writer ->
        writer.writeByte(1.toByte())
        writer.writeBytes(value.toByteArray(Charsets.UTF_8))
    }
}

internal object JsonCodec : TypeCodec<String> {
    override val pgTypeName = "json"
    override val pgSchema: String = "pg_catalog"
    override val oid: Int = 114
    override val kotlinClass = String::class

    override val fromBinary: (ByteArray, Int, Int) -> String = { data, offset, length ->
        String(data, offset, length, Charsets.UTF_8)
    }

    override val toBinary: (String, PgByteWriter) -> Unit = { value, writer ->
        writer.writeBytes(value.toByteArray(Charsets.UTF_8))
    }
}