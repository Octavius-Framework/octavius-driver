package io.github.octaviusframework.driver.type.containter

import io.github.octaviusframework.driver.exception.OctaviusTypeException
import io.github.octaviusframework.driver.exception.TypeExceptionMessage
import io.github.octaviusframework.driver.type.TypeRegistry

/**
 * Reprezentuje zakres w bazie PostgreSQL (np. int4range, tsrange).
 * Boundary values are stored natively, parsing is delegated lazily.
 */
class PgRange internal constructor(
    val rangeOid: UInt,
    val elementOid: UInt,
    val flags: Byte,
    val lowerBoundField: ContainerField?,
    val upperBoundField: ContainerField?,
    @PublishedApi internal val typeRegistry: TypeRegistry
) : PgContainer {
    override fun detach() {
        lowerBoundField?.detach()
        upperBoundField?.detach()
    }

    val isEmpty: Boolean get() = (flags.toInt() and 0x01) != 0
    val isLowerInclusive: Boolean get() = (flags.toInt() and 0x02) != 0
    val isUpperInclusive: Boolean get() = (flags.toInt() and 0x04) != 0
    val isLowerInfinite: Boolean get() = (flags.toInt() and 0x08) != 0
    val isUpperInfinite: Boolean get() = (flags.toInt() and 0x10) != 0
    val isLowerNull: Boolean get() = (flags.toInt() and 0x20) != 0
    val isUpperNull: Boolean get() = (flags.toInt() and 0x40) != 0

    /**
     * Lazily casts and returns the lower bound of the range.
     * Returns null if boundary is missing (infinity), explicitly null, or set is empty.
     */
    inline fun <reified T> lowerBound(): T? {
        if (isEmpty || isLowerInfinite || isLowerNull) return null
        return parseBound(lowerBoundField)
    }

    /**
     * Lazily casts and returns the upper bound of the range.
     * Returns null if boundary is missing (infinity), explicitly null, or set is empty.
     */
    inline fun <reified T> upperBound(): T? {
        if (isEmpty || isUpperInfinite || isUpperNull) return null
        return parseBound(upperBoundField)
    }

    @PublishedApi
    internal inline fun <reified T> parseBound(field: ContainerField?): T? {
        if (field == null) return null
        if (field.value != null && field.value is T) return field.value as T
        if (field.container != null && field.container is T) return field.container as T

        val window = field.rawValue ?: return null

        val serializer = typeRegistry.getCodecByOid<Any>(elementOid)
            ?: throw OctaviusTypeException(
                TypeExceptionMessage.MISSING_SERIALIZER,
                oid = elementOid,
                details = "Getting range bound"
            )

        val parsed = serializer.fromBinary(window)
        if (parsed is T) {
            return parsed
        } else {
            throw OctaviusTypeException(
                TypeExceptionMessage.CASTING_ERROR,
                typeName = T::class.simpleName,
                details = "Otrzymano ${parsed::class.simpleName}"
            )
        }
    }

    companion object {
        fun empty(rangeOid: UInt, elementOid: UInt, typeRegistry: TypeRegistry): PgRange {
            return PgRange(
                rangeOid = rangeOid,
                elementOid = elementOid,
                flags = 0x01,
                lowerBoundField = null,
                upperBoundField = null,
                typeRegistry = typeRegistry
            )
        }

        fun create(
            rangeOid: UInt,
            elementOid: UInt,
            lowerBound: Any? = null,
            upperBound: Any? = null,
            isLowerInclusive: Boolean = true,
            isUpperInclusive: Boolean = false,
            isLowerInfinite: Boolean = (lowerBound == null),
            isUpperInfinite: Boolean = (upperBound == null),
            isLowerNull: Boolean = false,
            isUpperNull: Boolean = false,
            typeRegistry: TypeRegistry
        ): PgRange {
            var flags = 0

            if (isLowerInclusive) flags = flags or 0x02
            if (isUpperInclusive) flags = flags or 0x04

            var lowerField: ContainerField? = null
            if (isLowerInfinite) {
                flags = flags or 0x08
            } else if (isLowerNull || lowerBound == null) {
                flags = flags or 0x20
            } else {
                lowerField = if (lowerBound is PgContainer) ContainerField(null, container = lowerBound) else ContainerField(null, value = lowerBound)
            }

            var upperField: ContainerField? = null
            if (isUpperInfinite) {
                flags = flags or 0x10
            } else if (isUpperNull || upperBound == null) {
                flags = flags or 0x40
            } else {
                upperField = if (upperBound is PgContainer) ContainerField(null, container = upperBound) else ContainerField(null, value = upperBound)
            }

            return PgRange(rangeOid, elementOid, flags.toByte(), lowerField, upperField, typeRegistry)
        }
    }
}
