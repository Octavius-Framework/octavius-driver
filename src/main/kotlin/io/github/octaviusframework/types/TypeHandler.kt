package io.github.octaviusframework.types

import io.github.octaviusframework.io.*
import kotlin.reflect.KClass

interface TypeHandler<T : Any> {
    val pgTypeName: String
    val pgSchema: String get() = "pg_catalog"
    val oid: UInt? get() = null
    val kotlinClass: KClass<T>
    val isDefaultForKotlinType: Boolean get() = false
    
    val fromBinary: (ByteArrayWindow) -> T
    val toBinary: (T) -> ByteArray
}

object ShortHandler : TypeHandler<Short> {
    override val pgTypeName = "int2"
    override val oid: UInt = 21u
    override val kotlinClass = Short::class
    override val isDefaultForKotlinType = true
    override val fromBinary: (ByteArrayWindow) -> Short = { it.getShortBE() }
    override val toBinary: (Short) -> ByteArray = { it.toByteArrayBE() }
}

object IntHandler : TypeHandler<Int> {
    override val pgTypeName = "int4"
    override val oid: UInt = 23u
    override val kotlinClass = Int::class
    override val isDefaultForKotlinType = true
    override val fromBinary: (ByteArrayWindow) -> Int = { it.getIntBE() }
    override val toBinary: (Int) -> ByteArray = { it.toByteArrayBE() }
}

object LongHandler : TypeHandler<Long> {
    override val pgTypeName = "int8"
    override val oid: UInt = 20u
    override val kotlinClass = Long::class
    override val isDefaultForKotlinType = true
    override val fromBinary: (ByteArrayWindow) -> Long = { it.getLongBE() }
    override val toBinary: (Long) -> ByteArray = { it.toByteArrayBE() }
}

object FloatHandler : TypeHandler<Float> {
    override val pgTypeName = "float4"
    override val oid: UInt = 700u
    override val kotlinClass = Float::class
    override val isDefaultForKotlinType = true
    override val fromBinary: (ByteArrayWindow) -> Float = { it.getFloatBE() }
    override val toBinary: (Float) -> ByteArray = { it.toByteArrayBE() }
}

object DoubleHandler : TypeHandler<Double> {
    override val pgTypeName = "float8"
    override val oid: UInt = 701u
    override val kotlinClass = Double::class
    override val isDefaultForKotlinType = true
    override val fromBinary: (ByteArrayWindow) -> Double = { it.getDoubleBE() }
    override val toBinary: (Double) -> ByteArray = { it.toByteArrayBE() }
}

object BooleanHandler : TypeHandler<Boolean> {
    override val pgTypeName = "bool"
    override val oid: UInt = 16u
    override val kotlinClass = Boolean::class
    override val isDefaultForKotlinType = true
    override val fromBinary: (ByteArrayWindow) -> Boolean = { it[0].toInt() != 0 }
    override val toBinary: (Boolean) -> ByteArray = { byteArrayOf(if (it) 1 else 0) }
}

object StringHandler : TypeHandler<String> {
    override val pgTypeName = "text"
    override val oid: UInt = 25u
    override val kotlinClass = String::class
    override val isDefaultForKotlinType = true
    
    override val fromBinary: (ByteArrayWindow) -> String = { String(it.data, it.offset, it.length, Charsets.UTF_8) }
    override val toBinary: (String) -> ByteArray = { it.toByteArray(Charsets.UTF_8) }
}

object ByteArrayHandler : TypeHandler<ByteArray> {
    override val pgTypeName = "bytea"
    override val oid: UInt = 17u
    override val kotlinClass = ByteArray::class
    override val isDefaultForKotlinType = true
    
    override val fromBinary: (ByteArrayWindow) -> ByteArray = { it.toByteArray() }
    override val toBinary: (ByteArray) -> ByteArray = { it }
}
