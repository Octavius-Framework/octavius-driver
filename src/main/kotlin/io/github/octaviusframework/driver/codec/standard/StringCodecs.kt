package io.github.octaviusframework.driver.codec.standard

import io.github.octaviusframework.driver.codec.PgByteWriter
import io.github.octaviusframework.driver.codec.TypeCodec

internal object StringCodec : TypeCodec<String> {
    override val pgTypeName = "text"
    override val oid: Int = 25
    override val kotlinClass = String::class
    override val isDefaultForKotlinType = true

    override val fromBinary: (ByteArray, Int, Int) -> String = { data, offset, length -> String(data, offset, length, Charsets.UTF_8) }
    override val toBinary: (String, PgByteWriter) -> Unit = { value, writer -> writer.writeBytes(value.toByteArray(Charsets.UTF_8)) }
}

internal object NameCodec : TypeCodec<String> {
    override val pgTypeName = "name"
    override val oid: Int = 19
    override val kotlinClass = String::class
    override val isDefaultForKotlinType = false
    override val fromBinary = StringCodec.fromBinary
    override val toBinary = StringCodec.toBinary
}

internal object CharCodec : TypeCodec<String> {
    override val pgTypeName = "char"
    override val oid: Int = 18
    override val kotlinClass = String::class
    override val isDefaultForKotlinType = false
    override val fromBinary = StringCodec.fromBinary
    override val toBinary = StringCodec.toBinary
}

internal object VarcharCodec : TypeCodec<String> {
    override val pgTypeName = "varchar"
    override val oid: Int = 1043
    override val kotlinClass = String::class
    override val isDefaultForKotlinType = false
    override val fromBinary = StringCodec.fromBinary
    override val toBinary = StringCodec.toBinary
}

internal object BpcharCodec : TypeCodec<String> {
    override val pgTypeName = "bpchar"
    override val oid: Int = 1042
    override val kotlinClass = String::class
    override val isDefaultForKotlinType = false
    override val fromBinary = StringCodec.fromBinary
    override val toBinary = StringCodec.toBinary
}

internal object JsonbCodec : TypeCodec<String> {
    override val pgTypeName = "jsonb"
    override val oid: Int = 3802
    override val kotlinClass = String::class
    override val isDefaultForKotlinType = false

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
    override val oid: Int = 114
    override val kotlinClass = String::class
    override val isDefaultForKotlinType = false

    override val fromBinary: (ByteArray, Int, Int) -> String = { data, offset, length ->
        String(data, offset, length, Charsets.UTF_8)
    }

    override val toBinary: (String, PgByteWriter) -> Unit = { value, writer ->
        writer.writeBytes(value.toByteArray(Charsets.UTF_8))
    }
}

internal object UnknownCodec : TypeCodec<String> {
    override val pgTypeName = "unknown"
    override val oid: Int = 705
    override val kotlinClass = String::class
    override val isDefaultForKotlinType = false
    override val fromBinary = StringCodec.fromBinary
    override val toBinary = StringCodec.toBinary
}
