package io.github.octaviusframework.driver.type

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.toKotlinLocalDate
import kotlinx.datetime.toKotlinLocalDateTime
import kotlinx.datetime.toKotlinLocalTime

/**
 * Extension properties for kotlinx.datetime types to support PostgreSQL infinity values.
 *
 * PostgreSQL's DATE, TIMESTAMP, and TIMESTAMPTZ types support special values 'infinity' and '-infinity'
 * to represent unbounded dates. These extensions provide corresponding constants for Kotlin types.
 *
 * ## Usage with PostgreSQL
 *
 * ```kotlin
 * // Insert a contract with no end date
 * dataAccess.insertInto("contracts")
 *     .values(listOf("end_date"))
 *     .execute("end_date" to LocalDate.DISTANT_FUTURE)  // Stored as 'infinity'
 *
 * // Query returns LocalDate.DISTANT_FUTURE for infinity values
 * val contract = dataAccess.select("end_date")
 *     .from("contracts")
 *     .toSingleOf<Contract>()
 *     .getOrThrow()!!
 * ```
 *
 * ## Notes
 *
 * - [kotlinx.datetime.Instant.DISTANT_PAST] and [kotlinx.datetime.Instant.DISTANT_FUTURE] are provided
 *   by the Kotlin standard library and map to PostgreSQL TIMESTAMPTZ infinity values.
 */

/**
 * The minimum LocalDate value, maps to PostgreSQL '-infinity' for DATE type.
 *
 * @see java.time.LocalDate.MIN
 */
val LocalDate.Companion.DISTANT_PAST: LocalDate
    get() = java.time.LocalDate.MIN.toKotlinLocalDate()

/**
 * The maximum LocalDate value, maps to PostgreSQL 'infinity' for DATE type.
 *
 * @see java.time.LocalDate.MAX
 */
val LocalDate.Companion.DISTANT_FUTURE: LocalDate
    get() = java.time.LocalDate.MAX.toKotlinLocalDate()

/**
 * The minimum LocalDateTime value, maps to PostgreSQL '-infinity' for TIMESTAMP type.
 *
 * @see java.time.LocalDateTime.MIN
 */
val LocalDateTime.Companion.DISTANT_PAST: LocalDateTime
    get() = java.time.LocalDateTime.MIN.toKotlinLocalDateTime()

/**
 * The maximum LocalDateTime value, maps to PostgreSQL 'infinity' for TIMESTAMP type.
 *
 * @see java.time.LocalDateTime.MAX
 */
val LocalDateTime.Companion.DISTANT_FUTURE: LocalDateTime
    get() = java.time.LocalDateTime.MAX.toKotlinLocalDateTime()

/**
 * The minimum LocalTime value.
 *
 * @see java.time.LocalTime.MIN
 */
val LocalTime.Companion.MIN: LocalTime
    get() = java.time.LocalTime.MIN.toKotlinLocalTime()

/**
 * The maximum LocalTime value.
 *
 * @see java.time.LocalTime.MAX
 */
val LocalTime.Companion.MAX: LocalTime
    get() = java.time.LocalTime.MAX.toKotlinLocalTime()
