package io.github.octaviusframework.types

import java.nio.ByteBuffer
import kotlin.reflect.KClass

interface TypeHandler<T : Any> {
    val pgTypeName: String
    val pgSchema: String get() = "pg_catalog"
    val oid: UInt? get() = null
    val kotlinClass: KClass<T>
    val isDefaultForKotlinType: Boolean get() = false
    
    val fromBinary: (ByteArray) -> T
    val toBinary: (T) -> ByteArray
}

object ShortHandler : TypeHandler<Short> {
    override val pgTypeName = "int2"
    override val oid: UInt = 21u
    override val kotlinClass = Short::class
    override val isDefaultForKotlinType = true
    override val fromBinary: (ByteArray) -> Short = { ByteBuffer.wrap(it).short }
    override val toBinary: (Short) -> ByteArray = { ByteBuffer.allocate(2).putShort(it).array() }
}

object IntHandler : TypeHandler<Int> {
    override val pgTypeName = "int4"
    override val oid: UInt = 23u
    override val kotlinClass = Int::class
    override val isDefaultForKotlinType = true
    override val fromBinary: (ByteArray) -> Int = { ByteBuffer.wrap(it).int }
    override val toBinary: (Int) -> ByteArray = { ByteBuffer.allocate(4).putInt(it).array() }
}

object LongHandler : TypeHandler<Long> {
    override val pgTypeName = "int8"
    override val oid: UInt = 20u
    override val kotlinClass = Long::class
    override val isDefaultForKotlinType = true
    override val fromBinary: (ByteArray) -> Long = { ByteBuffer.wrap(it).long }
    override val toBinary: (Long) -> ByteArray = { ByteBuffer.allocate(8).putLong(it).array() }
}

object FloatHandler : TypeHandler<Float> {
    override val pgTypeName = "float4"
    override val oid: UInt = 700u
    override val kotlinClass = Float::class
    override val isDefaultForKotlinType = true
    override val fromBinary: (ByteArray) -> Float = { ByteBuffer.wrap(it).float }
    override val toBinary: (Float) -> ByteArray = { ByteBuffer.allocate(4).putFloat(it).array() }
}

object DoubleHandler : TypeHandler<Double> {
    override val pgTypeName = "float8"
    override val oid: UInt = 701u
    override val kotlinClass = Double::class
    override val isDefaultForKotlinType = true
    override val fromBinary: (ByteArray) -> Double = { ByteBuffer.wrap(it).double }
    override val toBinary: (Double) -> ByteArray = { ByteBuffer.allocate(8).putDouble(it).array() }
}

object BooleanHandler : TypeHandler<Boolean> {
    override val pgTypeName = "bool"
    override val oid: UInt = 16u
    override val kotlinClass = Boolean::class
    override val isDefaultForKotlinType = true
    override val fromBinary: (ByteArray) -> Boolean = { it[0].toInt() != 0 }
    override val toBinary: (Boolean) -> ByteArray = { byteArrayOf(if (it) 1 else 0) }
}

object StringHandler : TypeHandler<String> {
    override val pgTypeName = "text"
    override val oid: UInt = 25u
    override val kotlinClass = String::class
    override val isDefaultForKotlinType = true
    
    override val fromBinary: (ByteArray) -> String = { String(it, Charsets.UTF_8) }
    override val toBinary: (String) -> ByteArray = { it.toByteArray(Charsets.UTF_8) }
}

object ByteArrayHandler : TypeHandler<ByteArray> {
    override val pgTypeName = "bytea"
    override val oid: UInt = 17u
    override val kotlinClass = ByteArray::class
    override val isDefaultForKotlinType = true
    
    override val fromBinary: (ByteArray) -> ByteArray = { it }
    override val toBinary: (ByteArray) -> ByteArray = { it }
}
