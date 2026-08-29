package io.github.octaviusframework.serializer

import io.github.octaviusframework.type.BigDecimal
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonUnquotedLiteral
import kotlinx.serialization.json.jsonPrimitive

/** The JS [BigDecimal] already holds the text, so there is nothing to format on the way out. */
internal actual fun encodeBigDecimalNative(encoder: Encoder, value: BigDecimal) {
    (encoder as JsonEncoder).encodeJsonElement(JsonUnquotedLiteral(value.toString()))
}

internal actual fun decodeBigDecimalNative(decoder: Decoder): BigDecimal {
    val element = (decoder as JsonDecoder).decodeJsonElement()
    if (element is JsonNull) {
        throw SerializationException("Unexpected null value for non-nullable BigDecimal")
    }
    // The token's own text, which is the whole point: routing it through a JS `Number` is what would round it.
    return BigDecimal(element.jsonPrimitive.content)
}
