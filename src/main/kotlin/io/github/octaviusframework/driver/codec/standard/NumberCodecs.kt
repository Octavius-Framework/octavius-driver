package io.github.octaviusframework.driver.codec.standard

import io.github.octaviusframework.driver.codec.TypeCodec
import io.github.octaviusframework.driver.io.*
import java.math.BigDecimal
import java.math.BigInteger


internal object ShortCodec : TypeCodec<Short> {
    override val pgTypeName = "int2"
    override val oid: UInt = 21u
    override val kotlinClass = Short::class
    override val isDefaultForKotlinType = true
    override val fromBinary: (ByteArrayWindow) -> Short = { it.getShortBE() }
    override val toBinary: (Short) -> ByteArray = { it.toByteArrayBE() }
}

internal object IntCodec : TypeCodec<Int> {
    override val pgTypeName = "int4"
    override val oid: UInt = 23u
    override val kotlinClass = Int::class
    override val isDefaultForKotlinType = true
    override val fromBinary: (ByteArrayWindow) -> Int = { it.getIntBE() }
    override val toBinary: (Int) -> ByteArray = { it.toByteArrayBE() }
}

internal object LongCodec : TypeCodec<Long> {
    override val pgTypeName = "int8"
    override val oid: UInt = 20u
    override val kotlinClass = Long::class
    override val isDefaultForKotlinType = true
    override val fromBinary: (ByteArrayWindow) -> Long = { it.getLongBE() }
    override val toBinary: (Long) -> ByteArray = { it.toByteArrayBE() }
}

internal object FloatCodec : TypeCodec<Float> {
    override val pgTypeName = "float4"
    override val oid: UInt = 700u
    override val kotlinClass = Float::class
    override val isDefaultForKotlinType = true
    override val fromBinary: (ByteArrayWindow) -> Float = { it.getFloatBE() }
    override val toBinary: (Float) -> ByteArray = { it.toByteArrayBE() }
}

internal object DoubleCodec : TypeCodec<Double> {
    override val pgTypeName = "float8"
    override val oid: UInt = 701u
    override val kotlinClass = Double::class
    override val isDefaultForKotlinType = true
    override val fromBinary: (ByteArrayWindow) -> Double = { it.getDoubleBE() }
    override val toBinary: (Double) -> ByteArray = { it.toByteArrayBE() }
}

internal object NumericCodec : TypeCodec<BigDecimal> {
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

            // FAST PATH: If the number is max 4 "digits" base-10000 (up to 16 decimal digits),
            // it fits safely in primitive 64-bit Long type. Computations take a fraction of a second.
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
                // SLOW PATH: Extremely large numbers are processed as String. This avoids
                // O(N^2) BigInteger creation. We simply concatenate 4-digit groups.
                val sb = StringBuilder(ndigits * 4 + 1)
                if (sign == 0x4000) {
                    sb.append('-')
                }
                for (i in 0 until ndigits) {
                    val digit = it.getShortBE(8 + i * 2).toInt()
                    if (i == 0) {
                        sb.append(digit) // No leading zeros for the first group
                    } else {
                        // Fast padding
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

            // FAST PATH: If unscaledValue fits in Long type (bitLength <= 63),
            // we skip Strings and BigInteger math. Simple primitive modulo with Long.
            if (unscaled.bitLength() <= 63) {
                var v = unscaled.toLong()
                val temp = LongArray(16) // Max 5 dumps for Long
                var originalNdigits = 0
                while (v > 0L) {
                    temp[originalNdigits++] = v % 10000L
                    v /= 10000L
                }

                val weight = originalNdigits - 1 - (adjustedScale / 4)

                // Skip trailing zeros in Postgres representation
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
                // SLOW PATH: Extremely large BigIntegers become a GC nightmare due to `divideAndRemainder()`.
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
                        digit = digit * 10 + (str[j] - '0') // lightning fast Int parsing
                    }
                    bytes.setShortBE(offset, digit.toShort())
                    offset += 2
                }
                bytes
            }
        }
    }
}
