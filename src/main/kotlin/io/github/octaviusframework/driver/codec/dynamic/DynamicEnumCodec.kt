package io.github.octaviusframework.driver.codec.dynamic

import io.github.octaviusframework.driver.codec.TypeCodec
import io.github.octaviusframework.driver.io.ByteArrayWindow

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