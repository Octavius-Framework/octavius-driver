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
 * Writes a [LocalDateTime] the way a `timestamp` column holds it, so that `(payload->>'taken')::timestamp`
 * reads back what was put in.
 *
 * [LocalDateTime.Companion.DISTANT_FUTURE] and [LocalDateTime.Companion.DISTANT_PAST] mean `infinity` and
 * `-infinity` in a column, and the default serializer writes them out in full at a year a `timestamp` cannot
 * hold, that type reaching 294276 AD. Outside the markers, every year outside `0001`..`9999` is spelled in a
 * way PostgreSQL will not read - see [PgDateText].
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
            else -> encoder.encodeString(PgDateText.fromIso(value.toString()))
        }
    }

    override fun deserialize(decoder: Decoder): LocalDateTime {
        return when (val string = decoder.decodeString()) {
            "infinity" -> LocalDateTime.DISTANT_FUTURE
            "-infinity" -> LocalDateTime.DISTANT_PAST
            else -> LocalDateTime.parse(PgDateText.toIso(string))
        }
    }
}
