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
 * Writes a [LocalDate] the way a `date` column holds it, so that `(payload->>'until')::date` reads back
 * what was put in.
 *
 * Two things stand between the default serializer and that. [LocalDate.Companion.DISTANT_FUTURE] and
 * [LocalDate.Companion.DISTANT_PAST] mean PostgreSQL's `infinity` and `-infinity` in a column and are written
 * out as year ±999999999 in JSON - a year no `date` holds, and not `infinity` in any case, so the same Kotlin
 * value stops meaning the same thing depending on where it is stored. And every year outside `0001`..`9999`
 * is spelled in a way PostgreSQL will not read at all; see [PgDateText] for that half, which is a plain
 * `LocalDate(10000, …)` and not a marker.
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
            else -> encoder.encodeString(PgDateText.fromIso(value.toString()))
        }
    }

    override fun deserialize(decoder: Decoder): LocalDate {
        return when (val string = decoder.decodeString()) {
            "infinity" -> LocalDate.DISTANT_FUTURE
            "-infinity" -> LocalDate.DISTANT_PAST
            else -> LocalDate.parse(PgDateText.toIso(string))
        }
    }
}
