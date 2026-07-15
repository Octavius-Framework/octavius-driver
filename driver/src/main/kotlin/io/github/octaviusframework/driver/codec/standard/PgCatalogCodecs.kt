package io.github.octaviusframework.driver.codec.standard

import io.github.octaviusframework.driver.codec.PgByteWriter
import io.github.octaviusframework.driver.codec.TypeCodec
import io.github.octaviusframework.driver.io.getIntBE

internal object OidCodec : TypeCodec<Int> {
    override val pgTypeName = "oid"
    override val pgSchema: String = "pg_catalog"
    override val oid: Int = 26
    override val kotlinClass = Int::class
    override val fromBinary: (ByteArray, Int, Int) -> Int = { data, offset, _ -> data.getIntBE(offset) }
    override val toBinary: (Int, PgByteWriter) -> Unit = { value, writer ->
        writer.writeInt(value)
    }
}

internal object NameCodec : TypeCodec<String> {
    override val pgTypeName = "name"
    override val pgSchema: String = "pg_catalog"
    override val oid: Int = 19
    override val kotlinClass = String::class
    override val fromBinary = TextCodec.fromBinary
    override val toBinary = TextCodec.toBinary
}

internal object CharCodec : TypeCodec<String> {
    override val pgTypeName = "char"
    override val pgSchema: String = "pg_catalog"
    override val oid: Int = 18
    override val kotlinClass = String::class
    override val fromBinary = TextCodec.fromBinary
    override val toBinary = TextCodec.toBinary
}