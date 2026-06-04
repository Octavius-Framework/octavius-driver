package io.github.octaviusframework.types

import java.nio.ByteBuffer
import kotlin.reflect.KClass

interface TypeHandler<T : Any> {
    val pgTypeName: String
    val pgSchema: String get() = "pg_catalog"
    val kotlinClass: KClass<T>
    val isDefaultForKotlinType: Boolean get() = false
    
    val fromBinary: (ByteArray) -> T
    val toBinary: (T) -> ByteArray
    
    val fromPgString: (String) -> T
    val toPgString: (T) -> String
}

object ShortHandler : TypeHandler<Short> {
    override val pgTypeName = "int2"
    override val kotlinClass = Short::class
    override val isDefaultForKotlinType = true
    override val fromBinary: (ByteArray) -> Short = { ByteBuffer.wrap(it).short }
    override val toBinary: (Short) -> ByteArray = { ByteBuffer.allocate(2).putShort(it).array() }
    override val fromPgString: (String) -> Short = { it.toShort() }
    override val toPgString: (Short) -> String = { it.toString() }
}

object IntHandler : TypeHandler<Int> {
    override val pgTypeName = "int4"
    override val kotlinClass = Int::class
    override val isDefaultForKotlinType = true
    override val fromBinary: (ByteArray) -> Int = { ByteBuffer.wrap(it).int }
    override val toBinary: (Int) -> ByteArray = { ByteBuffer.allocate(4).putInt(it).array() }
    override val fromPgString: (String) -> Int = { it.toInt() }
    override val toPgString: (Int) -> String = { it.toString() }
}

object LongHandler : TypeHandler<Long> {
    override val pgTypeName = "int8"
    override val kotlinClass = Long::class
    override val isDefaultForKotlinType = true
    override val fromBinary: (ByteArray) -> Long = { ByteBuffer.wrap(it).long }
    override val toBinary: (Long) -> ByteArray = { ByteBuffer.allocate(8).putLong(it).array() }
    override val fromPgString: (String) -> Long = { it.toLong() }
    override val toPgString: (Long) -> String = { it.toString() }
}

object FloatHandler : TypeHandler<Float> {
    override val pgTypeName = "float4"
    override val kotlinClass = Float::class
    override val isDefaultForKotlinType = true
    override val fromBinary: (ByteArray) -> Float = { ByteBuffer.wrap(it).float }
    override val toBinary: (Float) -> ByteArray = { ByteBuffer.allocate(4).putFloat(it).array() }
    override val fromPgString: (String) -> Float = { it.toFloat() }
    override val toPgString: (Float) -> String = { it.toString() }
}

object DoubleHandler : TypeHandler<Double> {
    override val pgTypeName = "float8"
    override val kotlinClass = Double::class
    override val isDefaultForKotlinType = true
    override val fromBinary: (ByteArray) -> Double = { ByteBuffer.wrap(it).double }
    override val toBinary: (Double) -> ByteArray = { ByteBuffer.allocate(8).putDouble(it).array() }
    override val fromPgString: (String) -> Double = { it.toDouble() }
    override val toPgString: (Double) -> String = { it.toString() }
}

object BooleanHandler : TypeHandler<Boolean> {
    override val pgTypeName = "bool"
    override val kotlinClass = Boolean::class
    override val isDefaultForKotlinType = true
    override val fromBinary: (ByteArray) -> Boolean = { it[0].toInt() != 0 }
    override val toBinary: (Boolean) -> ByteArray = { byteArrayOf(if (it) 1 else 0) }
    override val fromPgString: (String) -> Boolean = { it == "t" || it == "true" }
    override val toPgString: (Boolean) -> String = { if (it) "t" else "f" }
}

object StringHandler : TypeHandler<String> {
    override val pgTypeName = "text"
    override val kotlinClass = String::class
    override val isDefaultForKotlinType = true
    
    override val fromBinary: (ByteArray) -> String = { String(it, Charsets.UTF_8) }
    override val toBinary: (String) -> ByteArray = { it.toByteArray(Charsets.UTF_8) }
    
    override val fromPgString: (String) -> String = { it }
    override val toPgString: (String) -> String = { it }
}

object ByteArrayHandler : TypeHandler<ByteArray> {
    override val pgTypeName = "bytea"
    override val kotlinClass = ByteArray::class
    override val isDefaultForKotlinType = true
    
    override val fromBinary: (ByteArray) -> ByteArray = { it }
    override val toBinary: (ByteArray) -> ByteArray = { it }
    
    override val fromPgString: (String) -> ByteArray = {
        if (it.startsWith("\\x"))
            hexStringToByteArray(it.substring(2))
        else throw UnsupportedOperationException("Unsupported bytea format. Only hex format (e.g. '\\xDEADBEEF') is supported.")
    }
    override val toPgString: (ByteArray) -> String = {
        byteArrayToHexString(it)
    }

    private fun byteArrayToHexString(bytes: ByteArray): String {
        val hexChars = "0123456789ABCDEF"
        val result = StringBuilder(bytes.size * 2 + 2).append("\\x")
        for (b in bytes) {
            val i = b.toInt() and 0xFF
            result.append(hexChars[i shr 4]).append(hexChars[i and 0x0F])
        }
        return result.toString()
    }

    private fun hexStringToByteArray(hex: String): ByteArray {
        val len = hex.length
        require(len % 2 == 0) { "Hex string must have an even number of characters" }
        return ByteArray(len / 2) { i ->
            ((Character.digit(hex[i * 2], 16) shl 4) + Character.digit(hex[i * 2 + 1], 16)).toByte()
        }
    }
}
