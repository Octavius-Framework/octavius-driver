package io.github.octaviusframework.serializer

import io.github.octaviusframework.type.datetime.DISTANT_FUTURE
import io.github.octaviusframework.type.datetime.DISTANT_PAST
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Writes [LocalDateTime.Companion.DISTANT_FUTURE] and [LocalDateTime.Companion.DISTANT_PAST] as `infinity`
 * and `-infinity`, the way a `timestamp` column stores them, and everything else as ISO-8601.
 *
 * The default serializer writes those two out in full - `+999999999-12-31T23:59:59.999999999` and its
 * counterpart - and `(payload->>'taken')::timestamp` refuses the text, so the unbounded value a `timestamp`
 * column round-trips does not survive being put in a `jsonb` payload.
 *
 * Registered contextually by [octaviusSerializersModule]; `@Contextual` on the property is what selects it.
 */
object LocalDateTimeWithInfinitySerializer : KSerializer<LocalDateTime> {

    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor(
        "io.github.octaviusframework.serializer.LocalDateTimeWithInfinitySerializer",
        PrimitiveKind.STRING
    )

    override fun serialize(encoder: Encoder, value: LocalDateTime) {
        when (value) {
            LocalDateTime.DISTANT_FUTURE -> encoder.encodeString("infinity")
            LocalDateTime.DISTANT_PAST -> encoder.encodeString("-infinity")
            else -> encoder.encodeString(value.toString())
        }
    }

    override fun deserialize(decoder: Decoder): LocalDateTime {
        return when (val string = decoder.decodeString()) {
            "infinity" -> LocalDateTime.DISTANT_FUTURE
            "-infinity" -> LocalDateTime.DISTANT_PAST
            else -> LocalDateTime.parse(string)
        }
    }
}
