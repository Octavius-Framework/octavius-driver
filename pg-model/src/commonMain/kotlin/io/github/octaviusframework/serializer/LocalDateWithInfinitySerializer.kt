package io.github.octaviusframework.serializer

import io.github.octaviusframework.type.datetime.DISTANT_FUTURE
import io.github.octaviusframework.type.datetime.DISTANT_PAST
import kotlinx.datetime.LocalDate
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Writes [LocalDate.Companion.DISTANT_FUTURE] and [LocalDate.Companion.DISTANT_PAST] as `infinity` and
 * `-infinity`, the way a `date` column stores them, and everything else as ISO-8601.
 *
 * The default serializer writes those two as `+999999999-12-31` and `-999999999-01-01`, which are not
 * `infinity` and are not storable either: a `date` reaches 5874897 AD and 4713 BC, so the marker year is past
 * the column's ceiling by three orders of magnitude. `(payload->>'until')::date` refuses the text earlier
 * still - PostgreSQL reads the leading sign as the start of a timezone offset, which is why even
 * `+2024-01-01` is refused. So a grant with no end date, written into a `jsonb` payload, stops being the
 * value it was in a `date` column and stops being readable as a date at all.
 *
 * Registered contextually by [octaviusSerializersModule]; `@Contextual` on the property is what selects it.
 */
object LocalDateWithInfinitySerializer : KSerializer<LocalDate> {

    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor(
        "io.github.octaviusframework.serializer.LocalDateWithInfinitySerializer",
        PrimitiveKind.STRING
    )

    override fun serialize(encoder: Encoder, value: LocalDate) {
        when (value) {
            LocalDate.DISTANT_FUTURE -> encoder.encodeString("infinity")
            LocalDate.DISTANT_PAST -> encoder.encodeString("-infinity")
            else -> encoder.encodeString(value.toString())
        }
    }

    override fun deserialize(decoder: Decoder): LocalDate {
        return when (val string = decoder.decodeString()) {
            "infinity" -> LocalDate.DISTANT_FUTURE
            "-infinity" -> LocalDate.DISTANT_PAST
            else -> LocalDate.parse(string)
        }
    }
}
