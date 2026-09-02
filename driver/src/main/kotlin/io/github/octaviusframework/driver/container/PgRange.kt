package io.github.octaviusframework.driver.container

import io.github.octaviusframework.driver.exception.MappingException

/**
 * Represents a PostgreSQL range type (e.g., int4range, tsrange).
 *
 * @property rangeOid OID of the range type.
 * @property elementOid OID of the underlying element type of the range bounds.
 * @property flags Bitmask flags representing the range properties (e.g., empty, inclusive/exclusive bounds).
 * @property lowerBound The lower bound value of the range, or null if infinite/unbounded.
 * @property upperBound The upper bound value of the range, or null if infinite/unbounded.
 */
class PgRange internal constructor(
    val rangeOid: Int,
    val elementOid: Int,
    val flags: Byte,
    val lowerBound: Any?,
    val upperBound: Any?
) : PgContainer {
    override val containerOid: Int get() = rangeOid

    /** The range contains nothing at all. All other flags are meaningless when this is set. */
    val isEmpty: Boolean get() = (flags.toInt() and 0x01) != 0

    /** The lower bound is part of the range - `[` rather than `(` in PostgreSQL's own notation. */
    val isLowerInclusive: Boolean get() = (flags.toInt() and 0x02) != 0

    /** The upper bound is part of the range - `]` rather than `)`. */
    val isUpperInclusive: Boolean get() = (flags.toInt() and 0x04) != 0

    /** The range extends without limit downwards, written `(,` in PostgreSQL. */
    val isLowerInfinite: Boolean get() = (flags.toInt() and 0x08) != 0

    /** The range extends without limit upwards, written `,)`. */
    val isUpperInfinite: Boolean get() = (flags.toInt() and 0x10) != 0

    /** The lower bound is present but `NULL`, which is distinct from being unbounded. */
    val isLowerNull: Boolean get() = (flags.toInt() and 0x20) != 0

    /** The upper bound is present but `NULL`, which is distinct from being unbounded. */
    val isUpperNull: Boolean get() = (flags.toInt() and 0x40) != 0

    /**
     * Returns the lower bound, cast to [T].
     *
     * Empty, unbounded and `NULL` all mean there is no value to give back, and they are not
     * distinguished here - declare [T] nullable to receive `null` for any of the three, or interrogate
     * [isEmpty], [isLowerInfinite] and [isLowerNull] to tell them apart.
     *
     * @param T The expected bound type.
     * @return The lower bound.
     * @throws MappingException `CONVERSION_ERROR` if there is no bound and [T] is not nullable, or the
     *   bound is not a [T].
     */
    inline fun <reified T> lowerBound(): T {
        if (isEmpty || isLowerInfinite || isLowerNull) {
            if (null is T) return null as T
            throw conversionErrorAt(
                "lower",
                "Lower bound is null or infinite (missing) but requested type is non-nullable"
            )
        }
        val value = lowerBound
        if (value is T) return value
        throw conversionErrorAt(
            "lower",
            "Expected ${T::class.simpleName}, got ${if (value != null) value::class.simpleName else "null"}"
        )
    }

    /**
     * Returns the upper bound, cast to [T].
     *
     * Empty, unbounded and `NULL` all mean there is no value to give back, and they are not
     * distinguished here - declare [T] nullable to receive `null` for any of the three, or interrogate
     * [isEmpty], [isUpperInfinite] and [isUpperNull] to tell them apart.
     *
     * @param T The expected bound type.
     * @return The upper bound.
     * @throws MappingException `CONVERSION_ERROR` if there is no bound and [T] is not nullable, or the
     *   bound is not a [T].
     */
    inline fun <reified T> upperBound(): T {
        if (isEmpty || isUpperInfinite || isUpperNull) {
            if (null is T) return null as T
            throw conversionErrorAt(
                "upper",
                "Upper bound is null or infinite (missing) but requested type is non-nullable"
            )
        }
        val value = upperBound
        if (value is T) return value
        throw conversionErrorAt(
            "upper",
            "Expected ${T::class.simpleName}, got ${if (value != null) value::class.simpleName else "null"}"
        )
    }

    companion object {
        /**
         * Creates the empty range of a given range type - the one that contains nothing.
         *
         * @param rangeOid OID of the range type.
         * @param elementOid OID of its bound type.
         * @return An empty [PgRange].
         */
        fun empty(rangeOid: Int, elementOid: Int): PgRange {
            return PgRange(
                rangeOid = rangeOid,
                elementOid = elementOid,
                flags = 0x01,
                lowerBound = null,
                upperBound = null
            )
        }

        /**
         * Creates a range, packing the bound descriptions into the protocol's flag byte.
         *
         * The defaults produce PostgreSQL's own `[)` convention: lower inclusive, upper exclusive, and a
         * bound left `null` treated as unbounded rather than as a `NULL` value. Pass `isLowerNull` or
         * `isUpperNull` to mean the other thing. `Infinite` wins over `Null` where both are asked for.
         *
         * @param rangeOid OID of the range type.
         * @param elementOid OID of its bound type.
         * @param lowerBound The lower bound value, or `null` for none.
         * @param upperBound The upper bound value, or `null` for none.
         * @param isLowerInclusive Whether the lower bound is part of the range.
         * @param isUpperInclusive Whether the upper bound is part of the range.
         * @param isLowerInfinite Whether the range is unbounded below. Defaults to `lowerBound == null`.
         * @param isUpperInfinite Whether the range is unbounded above. Defaults to `upperBound == null`.
         * @param isLowerNull Whether the lower bound is present but `NULL`.
         * @param isUpperNull Whether the upper bound is present but `NULL`.
         * @return The constructed [PgRange].
         */
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
            isUpperNull: Boolean = false
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

            return PgRange(rangeOid, elementOid, flags.toByte(), lowerBound, upperBound)
        }
    }
}

