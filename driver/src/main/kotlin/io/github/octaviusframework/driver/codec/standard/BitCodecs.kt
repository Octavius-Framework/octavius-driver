package io.github.octaviusframework.driver.codec.standard

import io.github.octaviusframework.driver.codec.TypeCodec
import io.github.octaviusframework.driver.io.PgByteWriter
import io.github.octaviusframework.driver.io.getIntBE
import java.util.BitSet

internal object BitCodec : TypeCodec<BitSet> {
    override val pgTypeName = "bit"
    override val pgSchema: String = "pg_catalog"
    override val oid: Int = 1560
    override val kotlinClass = BitSet::class
    override val isDefaultForKotlinType = true

    override val fromBinary: (ByteArray, Int, Int) -> BitSet = { data, offset, _ ->
        val bitLen = data.getIntBE(offset)
        val bitSet = BitSet(bitLen)
        for (i in 0 until bitLen) {
            val byteIndex = offset + 4 + (i / 8)
            val bitIndex = 7 - (i % 8)
            if ((data[byteIndex].toInt() and (1 shl bitIndex)) != 0) {
                bitSet.set(i)
            }
        }
        bitSet
    }

    override val toBinary: (BitSet, PgByteWriter) -> Unit = { value, writer ->
        val bitLen = value.length()
        writer.writeInt(bitLen)
        val byteLen = (bitLen + 7) / 8
        val bytes = ByteArray(byteLen)
        for (i in 0 until bitLen) {
            if (value[i]) {
                val byteIndex = i / 8
                val bitIndex = 7 - (i % 8)
                bytes[byteIndex] = (bytes[byteIndex].toInt() or (1 shl bitIndex)).toByte()
            }
        }
        writer.writeBytes(bytes)
    }
}

internal object VarbitCodec : TypeCodec<BitSet> {
    override val pgTypeName = "varbit"
    override val pgSchema: String = "pg_catalog"
    override val oid: Int = 1562
    override val kotlinClass = BitSet::class

    override val fromBinary = BitCodec.fromBinary
    override val toBinary = BitCodec.toBinary
}
