package io.github.octaviusframework.serializer

import kotlinx.serialization.SerializationException
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonUnquotedLiteral
import kotlinx.serialization.json.jsonPrimitive
import java.math.BigDecimal

/**
 * `toPlainString` and not `toString`: the latter switches to scientific notation once the exponent is far
 * enough from the scale, and `1.0E+10` is not a JSON number.
 */
internal actual fun encodeBigDecimalNative(encoder: Encoder, value: BigDecimal) {
    (encoder as JsonEncoder).encodeJsonElement(JsonUnquotedLiteral(value.toPlainString()))
}

internal actual fun decodeBigDecimalNative(decoder: Decoder): BigDecimal {
    val element = (decoder as JsonDecoder).decodeJsonElement()
    if (element is JsonNull) {
        throw SerializationException("Unexpected null value for non-nullable BigDecimal")
    }
    // The token's own text, whether it was written as a number or - by something that is not this serializer
    // - as a string. Either parses; going through the element's number accessors would not.
    val content = element.jsonPrimitive.content
    return try {
        BigDecimal(content)
    } catch (e: NumberFormatException) {
        throw SerializationException("Invalid BigDecimal format: $content", e)
    }
}
