package io.github.octaviusframework.driver.codec.standard

import io.github.octaviusframework.driver.codec.TypeCodec
import io.github.octaviusframework.driver.exception.OctaviusTypeException
import io.github.octaviusframework.driver.exception.TypeExceptionMessage
import io.github.octaviusframework.driver.io.getLongBE
import kotlin.uuid.Uuid

internal object BooleanCodec : TypeCodec<Boolean> {
    override val pgTypeName = "bool"
    override val oid: Int = 16
    override val kotlinClass = Boolean::class
    override val isDefaultForKotlinType = true
    override val fromBinary: (ByteArray, Int, Int) -> Boolean = { data, offset, _ -> data[offset].toInt() != 0 }
    override val toBinary: (Boolean) -> ByteArray = { byteArrayOf(if (it) 1 else 0) }
}


internal object ByteArrayCodec : TypeCodec<ByteArray> {
    override val pgTypeName = "bytea"
    override val oid: Int = 17
    override val kotlinClass = ByteArray::class
    override val isDefaultForKotlinType = true

    override val fromBinary: (ByteArray, Int, Int) -> ByteArray = { data, offset, length -> data.copyOfRange(offset, offset + length) }
    override val toBinary: (ByteArray) -> ByteArray = { it }
}

internal object UnitCodec : TypeCodec<Unit> {
    override val pgTypeName = "void"
    override val oid: Int = 2278
    override val kotlinClass = Unit::class
    override val isDefaultForKotlinType = true

    override val fromBinary: (ByteArray, Int, Int) -> Unit = { _, _, _ -> }
    override val toBinary: (Unit) -> ByteArray =
        { throw OctaviusTypeException(TypeExceptionMessage.INVALID_PARAMETER_TYPE, typeName = "Unit", details = "Cannot send Unit/void as parameter") }
}

internal object UuidCodec : TypeCodec<Uuid> {
    override val pgTypeName = "uuid"
    override val oid: Int = 2950
    override val kotlinClass = Uuid::class
    override val isDefaultForKotlinType = true

    override val fromBinary: (ByteArray, Int, Int) -> Uuid = { data, offset, _ ->
        Uuid.fromLongs(data.getLongBE(offset), data.getLongBE(offset + 8))
    }

    override val toBinary: (Uuid) -> ByteArray = {
        it.toByteArray()
    }
}
