package io.github.octaviusframework.serializer

import io.github.octaviusframework.type.BigDecimal
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Writes a [BigDecimal] into JSON as a bare number, keeping every digit it had.
 *
 * There is no serializer for `BigDecimal` in the box, and the two obvious ways of writing one both lose
 * something a `numeric` column was chosen to keep. Encoding it as a `Double` rounds it - which is the whole
 * reason the column is `numeric` and not `float8`. Encoding it as a string keeps the digits but makes the
 * JSON say text: `jsonb_typeof` answers `string`, `(payload->>'amount')::numeric` needs a cast written by
 * hand, and arithmetic or an index on the value inside `jsonb` is not available at all.
 *
 * So it writes an unquoted literal - `12345.6789012345`, not `"12345.6789012345"` - which JSON permits at any
 * length and PostgreSQL's `jsonb` stores as a `numeric`, exactly. Reading goes back through the raw token
 * rather than through a `Double`, so a value too long for one survives the round trip.
 *
 * This works against JSON only: it reaches for the encoder's JSON element to bypass the number type its
 * primitives offer. Another format throws.
 *
 * Registered contextually by [octaviusSerializersModule], so `@Contextual` is normally all a property needs.
 * Naming it outright is for the class that does not read that module's [kotlinx.serialization.json.Json]:
 *
 * ```kotlin
 * @Serializable
 * @DynamicallyMappable("tribute_assessment")
 * data class TributeAssessment(
 *     val province: String,
 *     @Serializable(with = BigDecimalAsNumberSerializer::class)
 *     val denarii: BigDecimal
 * )
 * ```
 */
object BigDecimalAsNumberSerializer : KSerializer<BigDecimal> {

    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor(
        "io.github.octaviusframework.serializer.BigDecimalAsNumberSerializer",
        PrimitiveKind.STRING
    )

    override fun serialize(encoder: Encoder, value: BigDecimal) {
        encodeBigDecimalNative(encoder, value)
    }

    override fun deserialize(decoder: Decoder): BigDecimal {
        return decodeBigDecimalNative(decoder)
    }
}

/** The literal to write, which is where the platforms differ: one holds digits, the other holds a number. */
internal expect fun encodeBigDecimalNative(encoder: Encoder, value: BigDecimal)

/** The token as it stood in the text, read before anything could narrow it to a `Double`. */
internal expect fun decodeBigDecimalNative(decoder: Decoder): BigDecimal
