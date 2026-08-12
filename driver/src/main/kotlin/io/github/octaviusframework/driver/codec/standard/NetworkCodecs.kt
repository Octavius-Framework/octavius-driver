package io.github.octaviusframework.driver.codec.standard

import io.github.octaviusframework.driver.io.PgByteWriter
import io.github.octaviusframework.driver.codec.TypeCodec
import java.net.InetAddress

private fun formatMac(data: ByteArray, offset: Int, length: Int): String {
    val hex = "0123456789abcdef"
    val sb = StringBuilder(length * 3 - 1)
    for (i in 0 until length) {
        val b = data[offset + i].toInt() and 0xFF
        sb.append(hex[b shr 4])
        sb.append(hex[b and 0x0F])
        if (i < length - 1) sb.append(':')
    }
    return sb.toString()
}

private fun parseMac(value: String, length: Int, writer: PgByteWriter) {
    val cleanStr = value.replace(":", "").replace("-", "").replace(".", "")
    require(cleanStr.length == length * 2) { "Invalid MAC address format: $value" }
    val bytes = ByteArray(length)
    for (i in 0 until length) {
        val hexIndex = i * 2
        val b = cleanStr.substring(hexIndex, hexIndex + 2).toInt(16)
        bytes[i] = b.toByte()
    }
    writer.writeBytes(bytes)
}

internal object MacAddrCodec : TypeCodec<String> {
    override val pgTypeName = "macaddr"
    override val pgSchema: String = "pg_catalog"
    override val oid: Int = 829
    override val kotlinClass = String::class
    override val isDefaultForKotlinType = false

    override val fromBinary: (ByteArray, Int, Int) -> String = { data, offset, _ -> formatMac(data, offset, 6) }
    override val toBinary: (String, PgByteWriter) -> Unit = { value, writer -> parseMac(value, 6, writer) }
}

internal object MacAddr8Codec : TypeCodec<String> {
    override val pgTypeName = "macaddr8"
    override val pgSchema: String = "pg_catalog"
    override val oid: Int = 774
    override val kotlinClass = String::class
    override val isDefaultForKotlinType = false

    override val fromBinary: (ByteArray, Int, Int) -> String = { data, offset, _ -> formatMac(data, offset, 8) }
    override val toBinary: (String, PgByteWriter) -> Unit = { value, writer -> parseMac(value, 8, writer) }
}

internal abstract class NetworkCodec(
    override val pgTypeName: String,
    override val oid: Int,
    private val isCidr: Boolean
) : TypeCodec<String> {
    override val pgSchema: String = "pg_catalog"
    override val kotlinClass = String::class
    override val isDefaultForKotlinType = false

    override val fromBinary: (ByteArray, Int, Int) -> String = { data, offset, _ ->
        val family = data[offset].toInt()
        val bits = data[offset + 1].toInt() and 0xFF
        val isCidrFlag = data[offset + 2].toInt()
        val nb = data[offset + 3].toInt() and 0xFF

        val addressBytes = data.copyOfRange(offset + 4, offset + 4 + nb)
        val address = InetAddress.getByAddress(addressBytes).hostAddress

        val maxBits = if (nb == 4) 32 else 128
        if (bits == maxBits && !isCidr) {
            address
        } else {
            "$address/$bits"
        }
    }

    override val toBinary: (String, PgByteWriter) -> Unit = { value, writer ->
        val parts = value.split("/")
        val address = InetAddress.getByName(parts[0]).address
        
        val maxBits = if (address.size == 4) 32 else 128
        val bits = if (parts.size > 1) parts[1].toInt() else maxBits
        
        val family = if (address.size == 4) 2 else 3

        writer.writeByte(family.toByte())
        writer.writeByte(bits.toByte())
        writer.writeByte(if (isCidr) 1.toByte() else 0.toByte())
        writer.writeByte(address.size.toByte())
        writer.writeBytes(address)
    }
}

internal object InetCodec : NetworkCodec("inet", 869, false)
internal object CidrCodec : NetworkCodec("cidr", 650, true)
