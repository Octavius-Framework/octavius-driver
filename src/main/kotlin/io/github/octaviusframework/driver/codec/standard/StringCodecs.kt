package io.github.octaviusframework.driver.codec.standard

import io.github.octaviusframework.driver.codec.TypeCodec
import io.github.octaviusframework.driver.io.ByteArrayWindow

internal object StringCodec : TypeCodec<String> {
    override val pgTypeName = "text"
    override val oid: UInt = 25u
    override val kotlinClass = String::class
    override val isDefaultForKotlinType = true

    override val fromBinary: (ByteArrayWindow) -> String = { String(it.data, it.offset, it.length, Charsets.UTF_8) }
    override val toBinary: (String) -> ByteArray = { it.toByteArray(Charsets.UTF_8) }
}

internal object VarcharCodec : TypeCodec<String> {
    override val pgTypeName = "varchar"
    override val oid: UInt = 1043u
    override val kotlinClass = String::class
    override val isDefaultForKotlinType = false
    override val fromBinary = StringCodec.fromBinary
    override val toBinary = StringCodec.toBinary
}

internal object BpcharCodec : TypeCodec<String> {
    override val pgTypeName = "bpchar"
    override val oid: UInt = 1042u
    override val kotlinClass = String::class
    override val isDefaultForKotlinType = false
    override val fromBinary = StringCodec.fromBinary
    override val toBinary = StringCodec.toBinary
}

internal object JsonbCodec : TypeCodec<String> {
    override val pgTypeName = "jsonb"
    override val oid: UInt = 3802u
    override val kotlinClass = String::class
    override val isDefaultForKotlinType = false

    override val fromBinary: (ByteArrayWindow) -> String = {
        val version = it.data[it.offset]
        if (version == 1.toByte()) {
            String(it.data, it.offset + 1, it.length - 1, Charsets.UTF_8)
        } else {
            error("Unsupported jsonb version byte: $version")
        }
    }

    override val toBinary: (String) -> ByteArray = {
        val stringBytes = it.toByteArray(Charsets.UTF_8)
        val result = ByteArray(stringBytes.size + 1)
        result[0] = 1.toByte()
        stringBytes.copyInto(result, 1)
        result
    }
}

internal object JsonCodec : TypeCodec<String> {
    override val pgTypeName = "json"
    override val oid: UInt = 114u
    override val kotlinClass = String::class
    override val isDefaultForKotlinType = false

    override val fromBinary: (ByteArrayWindow) -> String = {
        String(it.data, it.offset, it.length, Charsets.UTF_8)
    }

    override val toBinary: (String) -> ByteArray = {
        it.toByteArray(Charsets.UTF_8)
    }
}

internal object UnknownCodec : TypeCodec<String> {
    override val pgTypeName = "unknown"
    override val oid: UInt = 705u
    override val kotlinClass = String::class
    override val isDefaultForKotlinType = false
    override val fromBinary = StringCodec.fromBinary
    override val toBinary = StringCodec.toBinary
}