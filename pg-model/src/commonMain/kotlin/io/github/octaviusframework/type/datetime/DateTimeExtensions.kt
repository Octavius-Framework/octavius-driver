package io.github.octaviusframework.type.datetime

import kotlinx.datetime.DateTimePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime

/**
 * Extension properties for kotlinx.datetime types to support PostgreSQL infinity values.
 *
 * PostgreSQL's DATE, TIMESTAMP, and TIMESTAMPTZ types support special values 'infinity' and '-infinity'
 * to represent unbounded dates. These extensions provide corresponding constants for Kotlin types.
 *
 * ## Notes
 *
 * - [kotlin.time.Instant.DISTANT_PAST] and [kotlin.time.Instant.DISTANT_FUTURE] are provided
 *   by the Kotlin standard library and map to PostgreSQL TIMESTAMPTZ infinity values.
 * - kotlinx.datetime keeps the same bounds on every platform it supports, and they are `internal` on all of
 *   them, which is why these are written out rather than delegated to.
 */

/** The first year kotlinx.datetime represents, on every platform. */
private const val YEAR_MIN = -999_999_999

/** The last year kotlinx.datetime represents, on every platform. */
private const val YEAR_MAX = 999_999_999

private const val NANOS_PER_SECOND = 1_000_000_000

/**
 * The minimum LocalDate value, maps to PostgreSQL '-infinity' for DATE type.
 */
val LocalDate.Companion.DISTANT_PAST: LocalDate
    get() = LocalDate(YEAR_MIN, 1, 1)

/**
 * The maximum LocalDate value, maps to PostgreSQL 'infinity' for DATE type.
 */
val LocalDate.Companion.DISTANT_FUTURE: LocalDate
    get() = LocalDate(YEAR_MAX, 12, 31)

/**
 * The minimum LocalDateTime value, maps to PostgreSQL '-infinity' for TIMESTAMP type.
 */
val LocalDateTime.Companion.DISTANT_PAST: LocalDateTime
    get() = LocalDateTime(LocalDate.DISTANT_PAST, LocalTime.MIN)

/**
 * The maximum LocalDateTime value, maps to PostgreSQL 'infinity' for TIMESTAMP type.
 */
val LocalDateTime.Companion.DISTANT_FUTURE: LocalDateTime
    get() = LocalDateTime(LocalDate.DISTANT_FUTURE, LocalTime.MAX)

/**
 * The minimum LocalTime value, midnight.
 */
val LocalTime.Companion.MIN: LocalTime
    get() = LocalTime(0, 0, 0, 0)

/**
 * The maximum LocalTime value, one nanosecond before midnight.
 */
val LocalTime.Companion.MAX: LocalTime
    get() = LocalTime(23, 59, 59, NANOS_PER_SECOND - 1)

// Kotlinx.Datetime throw exception if years + months overflows Int
//    require(it / 12 in Int.MIN_VALUE..Int.MAX_VALUE) {
//        "The total number of years in $years years and $months months overflows an Int"
// Or nanoseconds overflow Long
//       } catch (_: ArithmeticException) {
//        throw IllegalArgumentException("The total number of nanoseconds in $hours hours, $minutes minutes, $seconds seconds, and $nanoseconds nanoseconds overflows a Long")
//    }
//

/**
 * The 'infinity' representation for DateTimePeriod.
 *
 * **WARNING:** This is strictly a marker value used for mapping PostgreSQL 'infinity' interval.
 * Do NOT use it for actual date arithmetic (e.g., `date + DateTimePeriod.INFINITY`),
 * as it will cause an overflow in kotlinx-datetime.
 */
val DateTimePeriod.Companion.INFINITY: DateTimePeriod
    get() = DateTimePeriod(
        years = Int.MAX_VALUE,
        days = Int.MAX_VALUE,
        nanoseconds = Long.MAX_VALUE
    )

/**
 * The '-infinity' representation for DateTimePeriod.
 *
 * **WARNING:** This is strictly a marker value used for mapping PostgreSQL '-infinity' interval.
 * Do NOT use it for actual date arithmetic.
 */
val DateTimePeriod.Companion.MINUS_INFINITY: DateTimePeriod
    get() = DateTimePeriod(
        years = Int.MIN_VALUE,
        days = Int.MIN_VALUE,
        nanoseconds = Long.MIN_VALUE
    )
