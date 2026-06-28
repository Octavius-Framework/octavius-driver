package io.github.octaviusframework.driver.codec.standard

import io.github.octaviusframework.driver.codec.TypeCodec
import io.github.octaviusframework.driver.exception.OctaviusTypeException
import io.github.octaviusframework.driver.exception.TypeExceptionMessage
import io.github.octaviusframework.driver.io.ByteArrayWindow
import io.github.octaviusframework.driver.io.get
import io.github.octaviusframework.driver.io.getLongBE
import io.github.octaviusframework.driver.io.toByteArray
import kotlin.uuid.Uuid

internal object BooleanCodec : TypeCodec<Boolean> {
    override val pgTypeName = "bool"
    override val oid: UInt = 16u
    override val kotlinClass = Boolean::class
    override val isDefaultForKotlinType = true
    override val fromBinary: (ByteArrayWindow) -> Boolean = { it[0].toInt() != 0 }
    override val toBinary: (Boolean) -> ByteArray = { byteArrayOf(if (it) 1 else 0) }
}


internal object ByteArrayCodec : TypeCodec<ByteArray> {
    override val pgTypeName = "bytea"
    override val oid: UInt = 17u
    override val kotlinClass = ByteArray::class
    override val isDefaultForKotlinType = true

    override val fromBinary: (ByteArrayWindow) -> ByteArray = { it.toByteArray() }
    override val toBinary: (ByteArray) -> ByteArray = { it }
}

internal object UnitCodec : TypeCodec<Unit> {
    override val pgTypeName = "void"
    override val oid: UInt = 2278u
    override val kotlinClass = Unit::class
    override val isDefaultForKotlinType = true

    override val fromBinary: (ByteArrayWindow) -> Unit = { }
    override val toBinary: (Unit) -> ByteArray =
        { throw OctaviusTypeException(TypeExceptionMessage.INVALID_PARAMETER_TYPE, typeName = "Unit", details = "Cannot send Unit/void as parameter") }
}

internal object UuidCodec : TypeCodec<Uuid> {
    override val pgTypeName = "uuid"
    override val oid: UInt = 2950u
    override val kotlinClass = Uuid::class
    override val isDefaultForKotlinType = true

    override val fromBinary: (ByteArrayWindow) -> Uuid = {
        Uuid.fromLongs(it.getLongBE(0), it.getLongBE(8))
    }

    override val toBinary: (Uuid) -> ByteArray = {
        it.toByteArray()
    }
}