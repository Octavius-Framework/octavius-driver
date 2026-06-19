package io.github.octaviusframework.driver.codec.standard

import io.github.octaviusframework.driver.codec.TypeCodec
import io.github.octaviusframework.driver.io.ByteArrayWindow
import io.github.octaviusframework.driver.io.getIntBE
import io.github.octaviusframework.driver.io.getLongBE
import io.github.octaviusframework.driver.io.toByteArrayBE
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

private const val PG_EPOCH_MICROS = 946684800000000L
private const val PG_EPOCH_DAYS = 10957

private fun microsToInstant(pgMicros: Long): Instant {
    val totalMicros = pgMicros + PG_EPOCH_MICROS
    var seconds = totalMicros / 1000000
    var micros = totalMicros % 1000000
    if (micros < 0) {
        seconds -= 1
        micros += 1000000
    }
    return Instant.fromEpochSeconds(seconds, micros * 1000)
}

private fun instantToPgMicros(instant: Instant): Long {
    val seconds = instant.epochSeconds
    val nanos = instant.nanosecondsOfSecond
    val totalMicros = seconds * 1000000 + nanos / 1000
    return totalMicros - PG_EPOCH_MICROS
}

object InstantCodec : TypeCodec<Instant> {
    override val pgTypeName = "timestamptz"
    override val oid: UInt = 1184u
    override val kotlinClass = Instant::class
    override val isDefaultForKotlinType = true

    override val fromBinary: (ByteArrayWindow) -> Instant = {
        microsToInstant(it.getLongBE())
    }

    override val toBinary: (Instant) -> ByteArray = {
        instantToPgMicros(it).toByteArrayBE()
    }
}

object LocalDateTimeCodec : TypeCodec<LocalDateTime> {
    override val pgTypeName = "timestamp"
    override val oid: UInt = 1114u
    override val kotlinClass = LocalDateTime::class
    override val isDefaultForKotlinType = true

    override val fromBinary: (ByteArrayWindow) -> LocalDateTime = {
        microsToInstant(it.getLongBE()).toLocalDateTime(TimeZone.UTC)
    }

    override val toBinary: (LocalDateTime) -> ByteArray = {
        instantToPgMicros(it.toInstant(TimeZone.UTC)).toByteArrayBE()
    }
}

object LocalDateCodec : TypeCodec<LocalDate> {
    override val pgTypeName = "date"
    override val oid: UInt = 1082u
    override val kotlinClass = LocalDate::class
    override val isDefaultForKotlinType = true

    override val fromBinary: (ByteArrayWindow) -> LocalDate = {
        LocalDate.fromEpochDays(it.getIntBE() + PG_EPOCH_DAYS)
    }

    override val toBinary: (LocalDate) -> ByteArray = {
        (it.toEpochDays() - PG_EPOCH_DAYS).toByteArrayBE()
    }
}

object LocalTimeCodec : TypeCodec<LocalTime> {
    override val pgTypeName = "time"
    override val oid: UInt = 1083u
    override val kotlinClass = LocalTime::class
    override val isDefaultForKotlinType = true

    override val fromBinary: (ByteArrayWindow) -> LocalTime = {
        val micros = it.getLongBE()
        val secondsOfDay = (micros / 1000000).toInt()
        val nanosOfSecond = ((micros % 1000000) * 1000).toInt()
        val hours = secondsOfDay / 3600
        val minutes = (secondsOfDay % 3600) / 60
        val seconds = secondsOfDay % 60
        LocalTime(hours, minutes, seconds, nanosOfSecond)
    }

    override val toBinary: (LocalTime) -> ByteArray = {
        val micros = it.toSecondOfDay().toLong() * 1000000L + (it.nanosecond / 1000).toLong()
        micros.toByteArrayBE()
    }
}