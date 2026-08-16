package io.github.octaviusframework.driver.type.range

import kotlin.reflect.KClass

/**
 * Represents a generic, user-friendly PostgreSQL range.
 * This class is designed to be used in application code, mapping directly to/from [io.github.octaviusframework.driver.container.PgRange].
 *
 * The defaults produce PostgreSQL's own `[)` convention: lower inclusive, upper exclusive, and a bound
 * left `null` treated as unbounded rather than as a `NULL` value.
 *
 * Note that ranges over a **discrete** subtype - `int4range`, `int8range`, `daterange` - are rewritten
 * by the server into the canonical `[)` form, so what comes back describes the same span with different
 * bounds than what went out: `(1,5]` is stored and read back as `[2,6)`. Comparing a returned range
 * field-by-field against the one you sent will not match. Continuous types (`numrange`, `tsrange`,
 * `tstzrange`) have no canonical form and are stored as written.
 *
 * @param T The type of the range bounds (e.g., Int, LocalDate, or a data class).
 * @property elementClass The class of the bounds, needed to resolve the PostgreSQL range type.
 * @property lowerBound The lower bound value, or `null` for none.
 * @property upperBound The upper bound value, or `null` for none.
 * @property isLowerInclusive Whether the lower bound is part of the range.
 * @property isUpperInclusive Whether the upper bound is part of the range.
 * @property isLowerInfinite Whether the range is unbounded below.
 * @property isUpperInfinite Whether the range is unbounded above.
 * @property isLowerNull Whether the lower bound is present but `NULL`, as distinct from unbounded.
 * @property isUpperNull Whether the upper bound is present but `NULL`, as distinct from unbounded.
 * @property isEmpty Whether the range contains nothing at all; use [Range.empty] to build one.
 */
data class Range<T : Any>(
    val elementClass: KClass<T>,
    val lowerBound: T? = null,
    val upperBound: T? = null,
    val isLowerInclusive: Boolean = true,
    val isUpperInclusive: Boolean = false,
    val isLowerInfinite: Boolean = lowerBound == null,
    val isUpperInfinite: Boolean = upperBound == null,
    val isLowerNull: Boolean = false,
    val isUpperNull: Boolean = false,
    val isEmpty: Boolean = false
) {
    companion object {
        /**
         * Creates an empty range.
         */
        inline fun <reified T : Any> empty(): Range<T> = Range(
            elementClass = T::class,
            lowerBound = null,
            upperBound = null,
            isLowerInclusive = false,
            isUpperInclusive = false,
            isLowerInfinite = false,
            isUpperInfinite = false,
            isLowerNull = false,
            isUpperNull = false,
            isEmpty = true
        )
    }
}

/**
 * Creates a range, inferring the PostgreSQL range type from [T].
 *
 * Resolution goes by subtype and a subtype maps to exactly one range type, so declaring a custom range
 * over a built-in subtype makes unqualified ranges of that element type ambiguous - the highest OID
 * wins, which is always the custom one. Pin the type with
 * [withPgType][io.github.octaviusframework.driver.type.withPgType] where that matters.
 *
 * @param T The type of the range bounds.
 * @param lowerBound The lower bound value, or `null` for none.
 * @param upperBound The upper bound value, or `null` for none.
 * @param isLowerInclusive Whether the lower bound is part of the range.
 * @param isUpperInclusive Whether the upper bound is part of the range.
 * @param isLowerInfinite Whether the range is unbounded below. Defaults to `lowerBound == null`.
 * @param isUpperInfinite Whether the range is unbounded above. Defaults to `upperBound == null`.
 * @param isLowerNull Whether the lower bound is present but `NULL`, as distinct from unbounded.
 * @param isUpperNull Whether the upper bound is present but `NULL`, as distinct from unbounded.
 * @return The constructed range.
 */
inline fun <reified T : Any> rangeOf(
    lowerBound: T? = null,
    upperBound: T? = null,
    isLowerInclusive: Boolean = true,
    isUpperInclusive: Boolean = false,
    isLowerInfinite: Boolean = lowerBound == null,
    isUpperInfinite: Boolean = upperBound == null,
    isLowerNull: Boolean = false,
    isUpperNull: Boolean = false
): Range<T> = Range(
    elementClass = T::class,
    lowerBound = lowerBound,
    upperBound = upperBound,
    isLowerInclusive = isLowerInclusive,
    isUpperInclusive = isUpperInclusive,
    isLowerInfinite = isLowerInfinite,
    isUpperInfinite = isUpperInfinite,
    isLowerNull = isLowerNull,
    isUpperNull = isUpperNull,
    isEmpty = false
)
