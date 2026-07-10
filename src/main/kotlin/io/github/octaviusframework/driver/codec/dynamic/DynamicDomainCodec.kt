package io.github.octaviusframework.driver.codec.dynamic

import io.github.octaviusframework.driver.codec.TypeCodec
import io.github.octaviusframework.driver.codec.PgByteWriter
import io.github.octaviusframework.driver.exception.OctaviusTypeException
import io.github.octaviusframework.driver.exception.TypeExceptionMessage
import io.github.octaviusframework.driver.registry.TypeRegistry
import kotlin.reflect.KClass

internal class DynamicDomainCodec<T : Any>(
    override val oid: Int,
    override val pgTypeName: String,
    override val pgSchema: String,
    private val baseTypeOid: Int,
    private val typeRegistry: TypeRegistry
) : TypeCodec<T> {

    @Suppress("UNCHECKED_CAST")
    private val delegate: TypeCodec<T>
        get() = typeRegistry.getCodecByOid(baseTypeOid)
            ?: throw OctaviusTypeException(TypeExceptionMessage.MISSING_CODEC, oid = baseTypeOid, details = "Nie znaleziono serializatora dla bazowego typu domeny o OID $baseTypeOid")

    override val kotlinClass: KClass<T>
        get() = delegate.kotlinClass

    override val isDefaultForKotlinType = false

    override val fromBinary: (ByteArray, Int, Int) -> T
        get() = delegate.fromBinary

    override val toBinary: (T, PgByteWriter) -> Unit
        get() = delegate.toBinary
}

