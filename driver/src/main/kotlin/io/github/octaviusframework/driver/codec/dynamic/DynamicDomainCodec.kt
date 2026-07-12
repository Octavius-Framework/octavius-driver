package io.github.octaviusframework.driver.codec.dynamic

import io.github.octaviusframework.driver.codec.TypeCodec
import io.github.octaviusframework.driver.codec.PgByteWriter
import io.github.octaviusframework.driver.exception.OctaviusTypeException
import io.github.octaviusframework.driver.exception.TypeExceptionMessage
import io.github.octaviusframework.driver.registry.TypeRegistry
import kotlin.reflect.KClass

/**
 * Codec for PostgreSQL domain types, which are essentially custom types based on underlying base types.
 * It delegates serialization and deserialization to the codec of the base type.
 *
 * @param T The Kotlin type of the underlying base type.
 * @property oid The OID of the domain type.
 * @property pgTypeName The name of the domain type in PostgreSQL.
 * @property pgSchema The schema where the domain type is defined.
 * @property baseTypeOid The OID of the underlying base type.
 * @property typeRegistry Registry to look up the base type codec.
 */
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
            ?: throw OctaviusTypeException(TypeExceptionMessage.MISSING_CODEC, oid = baseTypeOid, details = "Serializer not found for base domain type with OID $baseTypeOid")

    override val kotlinClass: KClass<T>
        get() = delegate.kotlinClass

    override val isDefaultForKotlinType = false

    override val fromBinary: (ByteArray, Int, Int) -> T
        get() = delegate.fromBinary

    override val toBinary: (T, PgByteWriter) -> Unit
        get() = delegate.toBinary
}

