package io.github.octaviusframework.driver.container

import io.github.octaviusframework.driver.exception.OctaviusTypeException
import io.github.octaviusframework.driver.exception.TypeExceptionMessage
import io.github.octaviusframework.driver.registry.TypeRegistry

/**
 * Represents a PostgreSQL range type (e.g., int4range, tsrange).
 *
 * @property rangeOid OID of the range type.
 * @property elementOid OID of the underlying element type of the range bounds.
 * @property flags Bitmask flags representing the range properties (e.g., empty, inclusive/exclusive bounds).
 * @property lowerBound The lower bound value of the range, or null if infinite/unbounded.
 * @property upperBound The upper bound value of the range, or null if infinite/unbounded.
 * @property typeRegistry Registry used for resolving types.
 */
class PgRange internal constructor(
    val rangeOid: Int,
    val elementOid: Int,
    val flags: Byte,
    val lowerBound: Any?,
    val upperBound: Any?,
    @PublishedApi internal val typeRegistry: TypeRegistry
) : PgContainer {
    val isEmpty: Boolean get() = (flags.toInt() and 0x01) != 0
    val isLowerInclusive: Boolean get() = (flags.toInt() and 0x02) != 0
    val isUpperInclusive: Boolean get() = (flags.toInt() and 0x04) != 0
    val isLowerInfinite: Boolean get() = (flags.toInt() and 0x08) != 0
    val isUpperInfinite: Boolean get() = (flags.toInt() and 0x10) != 0
    val isLowerNull: Boolean get() = (flags.toInt() and 0x20) != 0
    val isUpperNull: Boolean get() = (flags.toInt() and 0x40) != 0

    inline fun <reified T> lowerBound(): T {
        if (isEmpty || isLowerInfinite || isLowerNull) {
            if (null is T) return null as T
            throw OctaviusTypeException(
                TypeExceptionMessage.CASTING_ERROR,
                typeName = T::class.simpleName,
                details = "Lower bound is null or infinite (missing) but requested type is non-nullable"
            )
        }
        val value = lowerBound
        if (value is T) return value
        throw OctaviusTypeException(
            TypeExceptionMessage.CASTING_ERROR,
            typeName = T::class.simpleName,
            details = "Expected ${T::class.simpleName}, got ${if (value != null) value::class.simpleName else "null"}"
        )
    }

    inline fun <reified T> upperBound(): T {
        if (isEmpty || isUpperInfinite || isUpperNull) {
            if (null is T) return null as T
            throw OctaviusTypeException(
                TypeExceptionMessage.CASTING_ERROR,
                typeName = T::class.simpleName,
                details = "Upper bound is null or infinite (missing) but requested type is non-nullable"
            )
        }
        val value = upperBound
        if (value is T) return value
        throw OctaviusTypeException(
            TypeExceptionMessage.CASTING_ERROR,
            typeName = T::class.simpleName,
            details = "Expected ${T::class.simpleName}, got ${if (value != null) value::class.simpleName else "null"}"
        )
    }

    companion object {
        fun empty(rangeOid: Int, elementOid: Int, typeRegistry: TypeRegistry): PgRange {
            return PgRange(
                rangeOid = rangeOid,
                elementOid = elementOid,
                flags = 0x01,
                lowerBound = null,
                upperBound = null,
                typeRegistry = typeRegistry
            )
        }

        fun create(
            rangeOid: Int,
            elementOid: Int,
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

            if (isLowerInfinite) {
                flags = flags or 0x08
            } else if (isLowerNull || lowerBound == null) {
                flags = flags or 0x20
            }

            if (isUpperInfinite) {
                flags = flags or 0x10
            } else if (isUpperNull || upperBound == null) {
                flags = flags or 0x40
            }

            return PgRange(rangeOid, elementOid, flags.toByte(), lowerBound, upperBound, typeRegistry)
        }
    }
}

