package io.github.octaviusframework.driver.codec.dynamic

import io.github.octaviusframework.driver.codec.TypeCodec
import io.github.octaviusframework.driver.codec.PgByteWriter

/**
 * Codec for PostgreSQL enum types.
 * It maps enum values to Kotlin strings during serialization and deserialization.
 *
 * @property oid The OID of the enum type.
 * @property pgTypeName The name of the enum type in PostgreSQL.
 * @property pgSchema The schema where the enum type is defined.
 */
internal class DynamicEnumCodec(
    override val oid: Int,
    override val pgTypeName: String,
    override val pgSchema: String
) : TypeCodec<String> {
    override val kotlinClass = String::class
    override val isDefaultForKotlinType = false

    override val fromBinary: (ByteArray, Int, Int) -> String = { data, offset, length ->
        String(data, offset, length, Charsets.UTF_8)
    }

    override val toBinary: (String, PgByteWriter) -> Unit = { value, writer -> writer.writeBytes(value.toByteArray(Charsets.UTF_8)) }
}
