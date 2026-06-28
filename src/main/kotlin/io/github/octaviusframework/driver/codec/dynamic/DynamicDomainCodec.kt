package io.github.octaviusframework.driver.codec.dynamic

import io.github.octaviusframework.driver.codec.TypeCodec
import io.github.octaviusframework.driver.exception.OctaviusTypeException
import io.github.octaviusframework.driver.exception.TypeExceptionMessage
import io.github.octaviusframework.driver.io.ByteArrayWindow
import io.github.octaviusframework.driver.type.TypeRegistry
import kotlin.reflect.KClass

internal class DynamicDomainCodec<T : Any>(
    override val oid: UInt,
    override val pgTypeName: String,
    override val pgSchema: String,
    private val baseTypeOid: UInt,
    private val typeRegistry: TypeRegistry
) : TypeCodec<T> {

    @Suppress("UNCHECKED_CAST")
    private val delegate: TypeCodec<T>
        get() = typeRegistry.getCodecByOid<T>(baseTypeOid)
            ?: throw OctaviusTypeException(TypeExceptionMessage.MISSING_SERIALIZER, oid = baseTypeOid, details = "Nie znaleziono serializatora dla bazowego typu domeny o OID $baseTypeOid")

    override val kotlinClass: KClass<T>
        get() = delegate.kotlinClass

    override val isDefaultForKotlinType = false

    override val fromBinary: (ByteArrayWindow) -> T
        get() = delegate.fromBinary

    override val toBinary: (T) -> ByteArray
        get() = delegate.toBinary
}
