package io.github.octaviusframework.driver.codec.standard

import io.github.octaviusframework.driver.codec.TypeCodec
import io.github.octaviusframework.driver.codec.PgByteWriter
import io.github.octaviusframework.driver.io.getIntBE
import io.github.octaviusframework.driver.io.getLongBE
import io.github.octaviusframework.driver.type.MAX
import io.github.octaviusframework.driver.type.DISTANT_FUTURE
import io.github.octaviusframework.driver.type.DISTANT_PAST
import io.github.octaviusframework.driver.exception.OctaviusTypeException
import io.github.octaviusframework.driver.exception.TypeExceptionMessage
import kotlinx.datetime.*
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
    try {
        val seconds = instant.epochSeconds
        val nanos = instant.nanosecondsOfSecond
        val totalMicros = Math.addExact(Math.multiplyExact(seconds, 1000000L), (nanos / 1000).toLong())
        val pgMicros = Math.subtractExact(totalMicros, PG_EPOCH_MICROS)
        if (pgMicros == Long.MAX_VALUE || pgMicros == Long.MIN_VALUE) {
            throw ArithmeticException("Instant value overlaps with PostgreSQL infinity representation")
        }
        return pgMicros
    } catch (e: ArithmeticException) {
        throw OctaviusTypeException(
            messageEnum = TypeExceptionMessage.VALUE_OUT_OF_RANGE,
            details = "Instant is out of range for PostgreSQL timestamp",
            cause = e
        )
    }
}

internal object TimestamptzCodec : TypeCodec<Instant> {
    override val pgTypeName = "timestamptz"
    override val pgSchema: String = "pg_catalog"
    override val oid: Int = 1184
    override val kotlinClass = Instant::class
    override val isDefaultForKotlinType = true

    override val fromBinary: (ByteArray, Int, Int) -> Instant = { data, offset, _ ->
        val micros = data.getLongBE(offset)
        when (micros) {
            Long.MAX_VALUE -> Instant.DISTANT_FUTURE
            Long.MIN_VALUE -> Instant.DISTANT_PAST
            else -> microsToInstant(micros)
        }
    }

    override val toBinary: (Instant, PgByteWriter) -> Unit = { value, writer ->
        when (value) {
            Instant.DISTANT_FUTURE -> writer.writeLong(Long.MAX_VALUE)
            Instant.DISTANT_PAST -> writer.writeLong(Long.MIN_VALUE)
            else -> writer.writeLong(instantToPgMicros(value))
        }
    }
}

internal object TimestampCodec : TypeCodec<LocalDateTime> {
    override val pgTypeName = "timestamp"
    override val pgSchema: String = "pg_catalog"
    override val oid: Int = 1114
    override val kotlinClass = LocalDateTime::class
    override val isDefaultForKotlinType = true

    override val fromBinary: (ByteArray, Int, Int) -> LocalDateTime = { data, offset, _ ->
        val micros = data.getLongBE(offset)
        when (micros) {
            Long.MAX_VALUE -> LocalDateTime.DISTANT_FUTURE
            Long.MIN_VALUE -> LocalDateTime.DISTANT_PAST
            else -> microsToInstant(micros).toLocalDateTime(TimeZone.UTC)
        }
    }

    override val toBinary: (LocalDateTime, PgByteWriter) -> Unit = { value, writer ->
        when (value) {
            LocalDateTime.DISTANT_FUTURE -> writer.writeLong(Long.MAX_VALUE)
            LocalDateTime.DISTANT_PAST -> writer.writeLong(Long.MIN_VALUE)
            else -> writer.writeLong(instantToPgMicros(value.toInstant(TimeZone.UTC)))
        }
    }
}

internal object DateCodec : TypeCodec<LocalDate> {
    override val pgTypeName = "date"
    override val pgSchema: String = "pg_catalog"
    override val oid: Int = 1082
    override val kotlinClass = LocalDate::class
    override val isDefaultForKotlinType = true

    override val fromBinary: (ByteArray, Int, Int) -> LocalDate = { data, offset, _ ->
        val days = data.getIntBE(offset)
        when (days) {
            Int.MAX_VALUE -> LocalDate.DISTANT_FUTURE
            Int.MIN_VALUE -> LocalDate.DISTANT_PAST
            else -> LocalDate.fromEpochDays(days + PG_EPOCH_DAYS)
        }
    }

    override val toBinary: (LocalDate, PgByteWriter) -> Unit = { value, writer ->
        when (value) {
            LocalDate.DISTANT_FUTURE -> writer.writeInt(Int.MAX_VALUE)
            LocalDate.DISTANT_PAST -> writer.writeInt(Int.MIN_VALUE)
            else -> {
                try {
                    val pgDays = Math.toIntExact(Math.subtractExact(value.toEpochDays(), PG_EPOCH_DAYS.toLong()))
                    if (pgDays == Int.MAX_VALUE || pgDays == Int.MIN_VALUE) {
                        throw ArithmeticException("LocalDate value overlaps with PostgreSQL infinity representation")
                    }
                    writer.writeInt(pgDays)
                } catch (e: ArithmeticException) {
                    throw OctaviusTypeException(
                        messageEnum = TypeExceptionMessage.VALUE_OUT_OF_RANGE,
                        details = "LocalDate is out of range for PostgreSQL date",
                        cause = e
                    )
                }
            }
        }
    }
}

internal object TimeCodec : TypeCodec<LocalTime> {
    override val pgTypeName = "time"
    override val pgSchema: String = "pg_catalog"
    override val oid: Int = 1083
    override val kotlinClass = LocalTime::class
    override val isDefaultForKotlinType = true

    private const val PG_TIME_24_HOURS_MICROS = 86400000000L

    override val fromBinary: (ByteArray, Int, Int) -> LocalTime = { data, offset, _ ->
        val micros = data.getLongBE(offset)
        if (micros == PG_TIME_24_HOURS_MICROS) {
            LocalTime.MAX
        } else {
            val secondsOfDay = (micros / 1000000).toInt()
            val nanosOfSecond = ((micros % 1000000) * 1000).toInt()
            val hours = secondsOfDay / 3600
            val minutes = (secondsOfDay % 3600) / 60
            val seconds = secondsOfDay % 60
            LocalTime(hours, minutes, seconds, nanosOfSecond)
        }
    }

    override val toBinary: (LocalTime, PgByteWriter) -> Unit = { value, writer ->
        if (value == LocalTime.MAX) {
            writer.writeLong(PG_TIME_24_HOURS_MICROS)
        } else {
            val micros = value.toSecondOfDay().toLong() * 1000000L + (value.nanosecond / 1000).toLong()
            writer.writeLong(micros)
        }
    }
}
