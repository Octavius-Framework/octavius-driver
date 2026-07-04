package io.github.octaviusframework.driver.codec.dynamic

import io.github.octaviusframework.driver.codec.TypeCodec

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

    override val toBinary: (String) -> ByteArray = {
        it.toByteArray(Charsets.UTF_8)
    }
}
