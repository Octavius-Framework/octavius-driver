package io.github.octaviusframework.serializer

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlin.time.Instant

/**
 * Writes [Instant.DISTANT_FUTURE] and [Instant.DISTANT_PAST] as `infinity` and `-infinity`, the way a
 * `timestamptz` column stores them, and everything else as ISO-8601.
 *
 * The driver already maps those two constants onto PostgreSQL's infinities in a `timestamptz` column. A
 * `jsonb` payload is not that column: the value goes through JSON, where the default serializer writes
 * `+100000-01-01T00:00:00Z`, which is a timestamp far away rather than an unbounded one. Year 100000 is
 * inside what a `timestamptz` holds - unlike the `LocalDate` and `LocalDateTime` markers, which are past
 * their columns' ceilings outright - so this one fails on the sign alone: `(payload->>'issued')::timestamptz`
 * refuses the text because PostgreSQL reads that leading `+` as the start of a timezone offset. Either way
 * the two forms of "no end date" stop comparing equal, in SQL, in whichever query first read one.
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
            else -> encoder.encodeString(value.toString())
        }
    }

    override fun deserialize(decoder: Decoder): Instant {
        return when (val string = decoder.decodeString()) {
            "infinity" -> Instant.DISTANT_FUTURE
            "-infinity" -> Instant.DISTANT_PAST
            else -> Instant.parse(string)
        }
    }
}
