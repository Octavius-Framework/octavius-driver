package io.github.octaviusframework.types

import io.github.octaviusframework.io.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

object JsonbElementHandler : TypeHandler<JsonElement> {
    override val pgTypeName = "jsonb"
    override val oid: UInt = 3802u
    override val kotlinClass = JsonElement::class
    override val isDefaultForKotlinType = true

    override val fromBinary: (ByteArrayWindow) -> JsonElement = {
        val version = it.data[it.offset]
        if (version == 1.toByte()) {
            val jsonString = String(it.data, it.offset + 1, it.length - 1, Charsets.UTF_8)
            Json.parseToJsonElement(jsonString)
        } else {
            error("Unsupported jsonb version byte: \$version")
        }
    }

    override val toBinary: (JsonElement) -> ByteArray = {
        val stringBytes = Json.encodeToString(JsonElement.serializer(), it).toByteArray(Charsets.UTF_8)
        val result = ByteArray(stringBytes.size + 1)
        result[0] = 1.toByte()
        stringBytes.copyInto(result, 1)
        result
    }
}

object JsonElementHandler : TypeHandler<JsonElement> {
    override val pgTypeName = "json"
    override val oid: UInt = 114u
    override val kotlinClass = JsonElement::class
    override val isDefaultForKotlinType = false

    override val fromBinary: (ByteArrayWindow) -> JsonElement = {
        val jsonString = String(it.data, it.offset, it.length, Charsets.UTF_8)
        Json.parseToJsonElement(jsonString)
    }

    override val toBinary: (JsonElement) -> ByteArray = {
        Json.encodeToString(JsonElement.serializer(), it).toByteArray(Charsets.UTF_8)
    }
}
