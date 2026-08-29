package io.github.octaviusframework.driver.type.datetime


import io.github.octaviusframework.driver.type.datetime.PgInterval.*
import io.github.octaviusframework.type.datetime.INFINITY
import io.github.octaviusframework.type.datetime.MINUS_INFINITY
import kotlinx.datetime.DateTimePeriod
import kotlin.time.Duration
import kotlin.time.Duration.Companion.microseconds

/**
 * Represents a PostgreSQL interval type, which can be a finite period of time or infinite.
 */
sealed interface PgInterval {
    /**
     * Represents a finite PostgreSQL interval.
     *
     * @property time The time part of the interval in microseconds.
     * @property days The number of days in the interval.
     * @property months The number of months in the interval.
     */
    data class Finite(
        val time: Long,
        val days: Int,
        val months: Int
    ) : PgInterval

    /**
     * Represents a PostgreSQL positive infinity interval.
     */
    data object Infinity : PgInterval

    /**
     * Represents a PostgreSQL negative infinity interval.
     */
    data object MinusInfinity : PgInterval
}

/**
 * Converts this [PgInterval] to a Kotlinx [DateTimePeriod].
 *
 * @return The equivalent [DateTimePeriod].
 */
fun PgInterval.toDateTimePeriod(): DateTimePeriod = when (this) {
    Infinity -> DateTimePeriod.INFINITY
    MinusInfinity -> DateTimePeriod.MINUS_INFINITY
    is Finite -> {
        val years = months / 12
        val remainingMonths = months % 12

        val hours = (time / 3_600_000_000L).toInt()
        val remainderAfterHours = time % 3_600_000_000L

        val minutes = (remainderAfterHours / 60_000_000L).toInt()
        val remainderAfterMinutes = remainderAfterHours % 60_000_000L

        val seconds = (remainderAfterMinutes / 1_000_000L).toInt()
        val microseconds = remainderAfterMinutes % 1_000_000L

        DateTimePeriod(
            years = years,
            months = remainingMonths,
            days = days,
            hours = hours,
            minutes = minutes,
            seconds = seconds,
            nanoseconds = microseconds * 1000
        )
    }
}

/**
 * Converts a Kotlinx [DateTimePeriod] to a [PgInterval].
 *
 * @return The equivalent [PgInterval].
 * @throws IllegalArgumentException if the calendar fields or time units overflow during conversion.
 */
fun DateTimePeriod.toPgInterval(): PgInterval = when (this) {
    DateTimePeriod.INFINITY -> Infinity
    DateTimePeriod.MINUS_INFINITY -> MinusInfinity
    else -> {
        val pgMonths = try {
            Math.addExact(Math.multiplyExact(years, 12), months)
        } catch (e: ArithmeticException) {
            throw IllegalArgumentException("Months overflow in DateTimePeriod to PgInterval conversion", e)
        }

        val pgTime = try {
            var t = Math.multiplyExact(hours.toLong(), 3_600_000_000L)
            t = Math.addExact(t, Math.multiplyExact(minutes.toLong(), 60_000_000L))
            t = Math.addExact(t, Math.multiplyExact(seconds.toLong(), 1_000_000L))
            t = Math.addExact(t, nanoseconds / 1000L)
            t
        } catch (e: ArithmeticException) {
            throw IllegalArgumentException("Time overflow in DateTimePeriod to PgInterval conversion", e)
        }

        Finite(
            time = pgTime,
            days = days,
            months = pgMonths
        )
    }
}

// ==========================================
// 3. PG_INTERVAL <-> DURATION (STRICT)
// ==========================================

/**
 * Converts strictly. The interval cannot have days and months set,
 * because they have variable length in the calendar.
 */
fun PgInterval.toDurationExact(): Duration = when (this) {
    Infinity -> Duration.INFINITE
    MinusInfinity -> -Duration.INFINITE
    is Finite -> {
        if (days != 0 || months != 0) {
            throw IllegalArgumentException(
                "Cannot convert PgInterval to exact Duration because it contains variable-length calendar units (days: $days, months: $months)."
            )
        }
        time.microseconds
    }
}

/**
 * Converts this [Duration] strictly to a [PgInterval].
 * The resulting interval will solely have the [PgInterval.Finite.time] component set,
 * with [PgInterval.Finite.days] and [PgInterval.Finite.months] set to zero.
 */
fun Duration.toPgIntervalExact(): PgInterval = when {
    this == Duration.INFINITE -> Infinity
    this == -Duration.INFINITE -> MinusInfinity
    else -> Finite(
        time = this.inWholeMicroseconds,
        days = 0,
        months = 0
    )
}

// ==========================================
// 4. PG_INTERVAL <-> DURATION (APPROXIMATE / JUSTIFIED)
// ==========================================

private const val MICROS_PER_DAY = 86_400_000_000L
private const val MICROS_PER_MONTH = 30 * MICROS_PER_DAY // 2,592,000,000,000L

/**
 * Converts by flattening everything to microseconds, assuming (like PostgreSQL `justify_interval`):
 * 1 month = 30 days, 1 day = 24 hours.
 */
fun PgInterval.toDurationApproximate(): Duration = when (this) {
    Infinity -> Duration.INFINITE
    MinusInfinity -> -Duration.INFINITE
    is Finite -> {
        val totalMicros = try {
            var m = Math.multiplyExact(months.toLong(), MICROS_PER_MONTH)
            m = Math.addExact(m, Math.multiplyExact(days.toLong(), MICROS_PER_DAY))
            m = Math.addExact(m, time)
            m
        } catch (e: ArithmeticException) {
            throw IllegalArgumentException("Microseconds overflow in PgInterval to Duration conversion", e)
        }
        totalMicros.microseconds
    }
}

/**
 * Splits absolute time into months (30 days), days (24h) and the rest of the time,
 * according to the PostgreSQL approximation standard.
 */
fun Duration.toPgIntervalApproximate(): PgInterval = when {
    this == Duration.INFINITE -> Infinity
    this == -Duration.INFINITE -> MinusInfinity
    else -> {
        val totalMicros = this.inWholeMicroseconds
        val months = (totalMicros / MICROS_PER_MONTH).toInt()
        val remAfterMonths = totalMicros % MICROS_PER_MONTH

        val days = (remAfterMonths / MICROS_PER_DAY).toInt()
        val time = remAfterMonths % MICROS_PER_DAY

        Finite(
            time = time,
            days = days,
            months = months
        )
    }
}