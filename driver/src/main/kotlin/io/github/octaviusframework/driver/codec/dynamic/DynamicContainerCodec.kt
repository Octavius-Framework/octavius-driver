package io.github.octaviusframework.driver.codec.dynamic

import io.github.octaviusframework.driver.codec.PgByteWriter
import io.github.octaviusframework.driver.codec.TypeCodec
import io.github.octaviusframework.driver.container.PgContainer
import io.github.octaviusframework.driver.registry.TypeRegistry
import kotlin.reflect.KClass

/**
 * Dynamic codec for PostgreSQL container types (Array, Composite, Range, Multirange, Record).
 * It delegates the actual parsing and serialization to the [ContainerCodec] object.
 */
internal class DynamicContainerCodec<T : PgContainer>(
    override val oid: Int,
    override val pgTypeName: String,
    override val pgSchema: String,
    override val kotlinClass: KClass<T>,
    private val typeRegistry: TypeRegistry
) : TypeCodec<T> {

    override val isDefaultForKotlinType = false

    @Suppress("UNCHECKED_CAST")
    override val fromBinary: (ByteArray, Int, Int) -> T = { data, offset, length ->
        ContainerCodec.parseContainer(data, offset, length, oid, typeRegistry) as T
    }

    override val toBinary: (T, PgByteWriter) -> Unit = { value, writer ->
        ContainerCodec.serializeContainer(value, writer, typeRegistry)
    }
}
