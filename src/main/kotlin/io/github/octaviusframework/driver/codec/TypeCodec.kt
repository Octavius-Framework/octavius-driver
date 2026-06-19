package io.github.octaviusframework.driver.codec

import io.github.octaviusframework.driver.io.ByteArrayWindow
import io.github.octaviusframework.driver.io.get
import io.github.octaviusframework.driver.io.getDoubleBE
import io.github.octaviusframework.driver.io.getFloatBE
import io.github.octaviusframework.driver.io.getIntBE
import io.github.octaviusframework.driver.io.getLongBE
import io.github.octaviusframework.driver.io.getShortBE
import io.github.octaviusframework.driver.io.setShortBE
import io.github.octaviusframework.driver.io.toByteArray
import io.github.octaviusframework.driver.io.toByteArrayBE
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import java.math.BigDecimal
import java.math.BigInteger
import kotlin.reflect.KClass
import kotlin.time.Instant
import kotlin.uuid.Uuid

interface TypeCodec<T : Any> {
    val pgTypeName: String
    val pgSchema: String get() = "pg_catalog"
    val oid: UInt? get() = null
    val kotlinClass: KClass<T>
    val isDefaultForKotlinType: Boolean get() = false

    val fromBinary: (ByteArrayWindow) -> T
    val toBinary: (T) -> ByteArray
}

object ShortCodec : TypeCodec<Short> {
    override val pgTypeName = "int2"
    override val oid: UInt = 21u
    override val kotlinClass = Short::class
    override val isDefaultForKotlinType = true
    override val fromBinary: (ByteArrayWindow) -> Short = { it.getShortBE() }
    override val toBinary: (Short) -> ByteArray = { it.toByteArrayBE() }
}

object IntCodec : TypeCodec<Int> {
    override val pgTypeName = "int4"
    override val oid: UInt = 23u
    override val kotlinClass = Int::class
    override val isDefaultForKotlinType = true
    override val fromBinary: (ByteArrayWindow) -> Int = { it.getIntBE() }
    override val toBinary: (Int) -> ByteArray = { it.toByteArrayBE() }
}

object LongCodec : TypeCodec<Long> {
    override val pgTypeName = "int8"
    override val oid: UInt = 20u
    override val kotlinClass = Long::class
    override val isDefaultForKotlinType = true
    override val fromBinary: (ByteArrayWindow) -> Long = { it.getLongBE() }
    override val toBinary: (Long) -> ByteArray = { it.toByteArrayBE() }
}

object FloatCodec : TypeCodec<Float> {
    override val pgTypeName = "float4"
    override val oid: UInt = 700u
    override val kotlinClass = Float::class
    override val isDefaultForKotlinType = true
    override val fromBinary: (ByteArrayWindow) -> Float = { it.getFloatBE() }
    override val toBinary: (Float) -> ByteArray = { it.toByteArrayBE() }
}

object DoubleCodec : TypeCodec<Double> {
    override val pgTypeName = "float8"
    override val oid: UInt = 701u
    override val kotlinClass = Double::class
    override val isDefaultForKotlinType = true
    override val fromBinary: (ByteArrayWindow) -> Double = { it.getDoubleBE() }
    override val toBinary: (Double) -> ByteArray = { it.toByteArrayBE() }
}

object BooleanCodec : TypeCodec<Boolean> {
    override val pgTypeName = "bool"
    override val oid: UInt = 16u
    override val kotlinClass = Boolean::class
    override val isDefaultForKotlinType = true
    override val fromBinary: (ByteArrayWindow) -> Boolean = { it[0].toInt() != 0 }
    override val toBinary: (Boolean) -> ByteArray = { byteArrayOf(if (it) 1 else 0) }
}

object StringCodec : TypeCodec<String> {
    override val pgTypeName = "text"
    override val oid: UInt = 25u
    override val kotlinClass = String::class
    override val isDefaultForKotlinType = true

    override val fromBinary: (ByteArrayWindow) -> String = { String(it.data, it.offset, it.length, Charsets.UTF_8) }
    override val toBinary: (String) -> ByteArray = { it.toByteArray(Charsets.UTF_8) }
}

object VarcharCodec : TypeCodec<String> {
    override val pgTypeName = "varchar"
    override val oid: UInt = 1043u
    override val kotlinClass = String::class
    override val isDefaultForKotlinType = false
    override val fromBinary = StringCodec.fromBinary
    override val toBinary = StringCodec.toBinary
}

object BpcharCodec : TypeCodec<String> {
    override val pgTypeName = "bpchar"
    override val oid: UInt = 1042u
    override val kotlinClass = String::class
    override val isDefaultForKotlinType = false
    override val fromBinary = StringCodec.fromBinary
    override val toBinary = StringCodec.toBinary
}


object ByteArrayCodec : TypeCodec<ByteArray> {
    override val pgTypeName = "bytea"
    override val oid: UInt = 17u
    override val kotlinClass = ByteArray::class
    override val isDefaultForKotlinType = true

    override val fromBinary: (ByteArrayWindow) -> ByteArray = { it.toByteArray() }
    override val toBinary: (ByteArray) -> ByteArray = { it }
}

object UnitCodec : TypeCodec<Unit> {
    override val pgTypeName = "void"
    override val oid: UInt = 2278u
    override val kotlinClass = Unit::class
    override val isDefaultForKotlinType = true

    override val fromBinary: (ByteArrayWindow) -> Unit = { }
    override val toBinary: (Unit) -> ByteArray =
        { throw UnsupportedOperationException("Cannot send Unit/void as parameter") }
}

object UuidCodec : TypeCodec<Uuid> {
    override val pgTypeName = "uuid"
    override val oid: UInt = 2950u
    override val kotlinClass = Uuid::class
    override val isDefaultForKotlinType = true

    override val fromBinary: (ByteArrayWindow) -> Uuid = {
        Uuid.fromLongs(it.getLongBE(0), it.getLongBE(8))
    }

    override val toBinary: (Uuid) -> ByteArray = {
        it.toByteArray()
    }
}

object NumericCodec : TypeCodec<BigDecimal> {
    override val pgTypeName = "numeric"
    override val oid: UInt = 1700u
    override val kotlinClass = BigDecimal::class
    override val isDefaultForKotlinType = true

    override val fromBinary: (ByteArrayWindow) -> BigDecimal = {
        val ndigits = it.getShortBE(0).toInt()
        val weight = it.getShortBE(2).toInt()
        val sign = it.getShortBE(4).toInt()
        val dscale = it.getShortBE(6).toInt()

        if (sign == 0xC000) {
            error("NaN is not supported by java.math.BigDecimal") // :<
        }

        if (ndigits == 0) {
            BigDecimal.ZERO.setScale(dscale)
        } else {
            val parsedScale = -4 * (weight - ndigits + 1)

            // FAST PATH: Jeśli liczba to max 4 "cyfry" base-10000 (czyli do 16 cyfr dziesiętnych),
            // mieści się bezpiecznie w prymitywnym 64-bitowym typie Long. Obliczenia są ułamek sekundy.
            if (ndigits <= 4) {
                var unscaled = 0L
                for (i in 0 until ndigits) {
                    val digit = it.getShortBE(8 + i * 2).toLong()
                    unscaled = unscaled * 10000L + digit
                }
                if (sign == 0x4000) {
                    unscaled = -unscaled
                }
                var result = BigDecimal.valueOf(unscaled, parsedScale)
                if (result.scale() != dscale) {
                    result = result.setScale(dscale)
                }
                result
            } else {
                // SLOW PATH: Ekstremalnie duże liczby przetwarzamy jako String. Unikamy w ten sposób
                // $O(N^2)$ tworzenia BigIntegerów. Po prostu sklejamy ze sobą grupy 4-cyfrowe.
                val sb = StringBuilder(ndigits * 4 + 1)
                if (sign == 0x4000) {
                    sb.append('-')
                }
                for (i in 0 until ndigits) {
                    val digit = it.getShortBE(8 + i * 2).toInt()
                    if (i == 0) {
                        sb.append(digit) // Bez zer wiodących dla pierwszej grupy
                    } else {
                        // Szybki padding
                        if (digit < 10) sb.append("000")
                        else if (digit < 100) sb.append("00")
                        else if (digit < 1000) sb.append('0')
                        sb.append(digit)
                    }
                }

                var result = BigDecimal(BigInteger(sb.toString()), parsedScale)
                if (result.scale() != dscale) {
                    result = result.setScale(dscale)
                }
                result
            }
        }
    }

    override val toBinary: (BigDecimal) -> ByteArray = {
        val sign = if (it.signum() == -1) 0x4000 else 0x0000
        val dscale = it.scale().coerceAtLeast(0)

        if (it.compareTo(BigDecimal.ZERO) == 0) {
            val bytes = ByteArray(8)
            bytes.setShortBE(0, 0) // ndigits
            bytes.setShortBE(2, 0) // weight
            bytes.setShortBE(4, sign.toShort()) // sign
            bytes.setShortBE(6, dscale.toShort()) // dscale
            bytes
        } else {
            var adjusted = it.abs()
            var adjustedScale = adjusted.scale()
            if (adjustedScale < 0) {
                adjusted = adjusted.setScale(0)
                adjustedScale = 0
            }
            val remainder = adjustedScale % 4
            if (remainder != 0) {
                adjustedScale += (4 - remainder)
                adjusted = adjusted.setScale(adjustedScale)
            }

            val unscaled = adjusted.unscaledValue()

            // FAST PATH: Jeżeli unscaledValue zmieści się w typie Long (bitLength <= 63),
            // pomijamy Stringi oraz matematykę BigInteger. Zwykłe prymitywne modulo z Longa.
            if (unscaled.bitLength() <= 63) {
                var v = unscaled.toLong()
                val temp = LongArray(16) // Max 5 zrzutów dla Long
                var originalNdigits = 0
                while (v > 0L) {
                    temp[originalNdigits++] = v % 10000L
                    v /= 10000L
                }

                val weight = originalNdigits - 1 - (adjustedScale / 4)

                // Pominięcie końcowych zer w reprezentacji Postgresa
                var startIdx = 0
                while (startIdx < originalNdigits && temp[startIdx] == 0L) {
                    startIdx++
                }
                val ndigits = originalNdigits - startIdx

                val bytes = ByteArray(8 + ndigits * 2)
                bytes.setShortBE(0, ndigits.toShort())
                bytes.setShortBE(2, weight.toShort())
                bytes.setShortBE(4, sign.toShort())
                bytes.setShortBE(6, dscale.toShort())

                var offset = 8
                for (i in (originalNdigits - 1) downTo startIdx) {
                    bytes.setShortBE(offset, temp[i].toShort())
                    offset += 2
                }
                bytes
            } else {
                // SLOW PATH: Ekstremalnie duże BigIntegery zamienia się w koszmar dla GC przez `divideAndRemainder()`.
                val str = unscaled.toString()
                val len = str.length
                val originalNdigits = (len + 3) / 4
                val weight = originalNdigits - 1 - (adjustedScale / 4)

                var trailingZeroChunks = 0
                for (i in 0 until originalNdigits) {
                    val end = len - i * 4
                    val start = maxOf(0, end - 4)
                    var isZero = true
                    for (j in start until end) {
                        if (str[j] != '0') {
                            isZero = false
                            break
                        }
                    }
                    if (isZero) trailingZeroChunks++ else break
                }

                val ndigits = originalNdigits - trailingZeroChunks

                val bytes = ByteArray(8 + ndigits * 2)
                bytes.setShortBE(0, ndigits.toShort())
                bytes.setShortBE(2, weight.toShort())
                bytes.setShortBE(4, sign.toShort())
                bytes.setShortBE(6, dscale.toShort())

                var offset = 8
                for (chunkIdx in (originalNdigits - 1) downTo trailingZeroChunks) {
                    val end = len - chunkIdx * 4
                    val start = maxOf(0, end - 4)
                    var digit = 0
                    for (j in start until end) {
                        digit = digit * 10 + (str[j] - '0') // błyskawiczne parsowanie Int'a
                    }
                    bytes.setShortBE(offset, digit.toShort())
                    offset += 2
                }
                bytes
            }
        }
    }
}

private const val PG_EPOCH_MICROS = 946684800000000L
private const val PG_EPOCH_DAYS = 10957

private fun microsToInstant(pgMicros: Long): Instant {
    var totalMicros = pgMicros + PG_EPOCH_MICROS
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

object JsonbCodec : TypeCodec<String> {
    override val pgTypeName = "jsonb"
    override val oid: UInt = 3802u
    override val kotlinClass = String::class
    override val isDefaultForKotlinType = false

    override val fromBinary: (ByteArrayWindow) -> String = {
        val version = it.data[it.offset]
        if (version == 1.toByte()) {
            String(it.data, it.offset + 1, it.length - 1, Charsets.UTF_8)
        } else {
            error("Unsupported jsonb version byte: $version")
        }
    }

    override val toBinary: (String) -> ByteArray = {
        val stringBytes = it.toByteArray(Charsets.UTF_8)
        val result = ByteArray(stringBytes.size + 1)
        result[0] = 1.toByte()
        stringBytes.copyInto(result, 1)
        result
    }
}

object JsonCodec : TypeCodec<String> {
    override val pgTypeName = "json"
    override val oid: UInt = 114u
    override val kotlinClass = String::class
    override val isDefaultForKotlinType = false

    override val fromBinary: (ByteArrayWindow) -> String = {
        String(it.data, it.offset, it.length, Charsets.UTF_8)
    }

    override val toBinary: (String) -> ByteArray = {
        it.toByteArray(Charsets.UTF_8)
    }
}

class DynamicEnumCodec(
    override val oid: UInt,
    override val pgTypeName: String,
    override val pgSchema: String
) : TypeCodec<String> {
    override val kotlinClass = String::class
    override val isDefaultForKotlinType = false

    override val fromBinary: (ByteArrayWindow) -> String = {
        String(it.data, it.offset, it.length, Charsets.UTF_8)
    }

    override val toBinary: (String) -> ByteArray = {
        it.toByteArray(Charsets.UTF_8)
    }
}
