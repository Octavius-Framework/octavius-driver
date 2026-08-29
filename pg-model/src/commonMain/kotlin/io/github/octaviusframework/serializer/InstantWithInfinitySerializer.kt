package io.github.octaviusframework.serializer

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlin.time.Instant

/**
 * Writes an [Instant] the way a `timestamptz` column holds it, so that `(payload->>'issued')::timestamptz`
 * reads back what was put in.
 *
 * The driver already maps [Instant.DISTANT_FUTURE] and [Instant.DISTANT_PAST] onto PostgreSQL's infinities in
 * a `timestamptz` column. A `jsonb` payload is not that column: the default serializer writes
 * `+100000-01-01T00:00:00Z` there, a timestamp far away rather than an unbounded one, so the two forms of
 * "no end date" stop comparing equal in SQL. Year 100000 is itself inside what a `timestamptz` holds, unlike
 * the `LocalDate` and `LocalDateTime` markers - which is a good illustration of the second half, handled by
 * [PgDateText]: the spelling is refused before the range is ever tested.
 *
 * Registered contextually by [octaviusSerializersModule]; `@Contextual` on the property is what selects it.
 */
object InstantWithInfinitySerializer : KSerializer<Instant> {

    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor(
        "io.github.octaviusframework.serializer.InstantWithInfinitySerializer",
        PrimitiveKind.STRING
    )

    override fun serialize(encoder: Encoder, value: Instant) {
        when (value) {
            Instant.DISTANT_FUTURE -> encoder.encodeString("infinity")
            Instant.DISTANT_PAST -> encoder.encodeString("-infinity")
            else -> encoder.encodeString(PgDateText.fromIso(value.toString()))
        }
    }

    override fun deserialize(decoder: Decoder): Instant {
        return when (val string = decoder.decodeString()) {
            "infinity" -> Instant.DISTANT_FUTURE
            "-infinity" -> Instant.DISTANT_PAST
            else -> Instant.parse(PgDateText.toIso(string))
        }
    }
}
