package io.github.octaviusframework.serializer

import io.github.octaviusframework.identifier.CaseConvention
import io.github.octaviusframework.identifier.CaseConverter
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlin.enums.EnumEntries

/**
 * Writes an enum constant into JSON under the label PostgreSQL knows it by, rather than under its Kotlin name.
 *
 * `registerEnum` teaches the driver that `Praetor` is `PRAETOR` in an enum **column**. A `jsonb` payload never
 * reaches that: kotlinx.serialization writes the constant's own name, so the same value is `PRAETOR` in one
 * place and `Praetor` in the other, and a query filtering on `payload->>'office'` finds neither reliably.
 *
 * On the JVM this is already answered without writing anything: the client reads the labels off the driver's
 * registry, so a registered enum - named at `registerEnum`, or found by a scan through `@PgEnumType` - is
 * written correctly wherever `@Contextual` asks for it. **This is for the side that has no registry to read**:
 * a frontend sharing the class, or an enum that only ever appears inside JSON. The conventions default to
 * what `registerEnum` defaults to, so an enum that took the defaults there takes them here too, and
 * `@Serializable(with = …)` binds tighter than a contextual module - so naming this on the class makes both
 * ends agree by construction rather than by each deriving its own.
 *
 * ```kotlin
 * @Serializable(with = MagistratureSerializer::class)
 * @PgEnumType(pgConvention = CaseConvention.SNAKE_CASE_UPPER)
 * enum class Magistrature { Quaestor, Aedile, Praetor, Consul }
 *
 * object MagistratureSerializer : EnumWithCaseConventionSerializer<Magistrature>(
 *     enumName = "Magistrature",
 *     entries = Magistrature.entries
 * )
 * ```
 *
 * @param E The enum being written.
 * @param enumName What to call it in the descriptor - the class's own name is the obvious choice, and it has
 * to be distinct from every other serializer's in the same format.
 * @param entries Its constants, which is `YourEnum.entries`.
 * @param pgConvention How the labels are written in PostgreSQL.
 * @param kotlinConvention How the constants are written in Kotlin.
 */
open class EnumWithCaseConventionSerializer<E : Enum<E>>(
    enumName: String,
    private val entries: EnumEntries<E>,
    private val pgConvention: CaseConvention = CaseConvention.SNAKE_CASE_UPPER,
    private val kotlinConvention: CaseConvention = CaseConvention.PASCAL_CASE
) : KSerializer<E> {

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("io.github.octaviusframework.serializer.$enumName", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: E) {
        encoder.encodeString(CaseConverter.convert(value.name, kotlinConvention, pgConvention))
    }

    override fun deserialize(decoder: Decoder): E {
        val string = decoder.decodeString()
        val kotlinName = CaseConverter.convert(string, pgConvention, kotlinConvention)

        return entries.firstOrNull { it.name == kotlinName }
            ?: throw SerializationException(
                "'$string' is not a label of ${descriptor.serialName}; under $kotlinConvention that reads as " +
                    "'$kotlinName', and no constant is called that."
            )
    }
}
