package io.github.octaviusframework.client.dynamic

import io.github.octaviusframework.driver.identifier.QualifiedName
import io.github.octaviusframework.driver.registry.ConverterRegistry
import io.github.octaviusframework.driver.registry.PgEnumRegistration
import io.github.octaviusframework.identifier.CaseConverter
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.overwriteWith
import kotlin.reflect.KClass

/**
 * Writes a registered enum into JSON under the label PostgreSQL holds, and reads it back.
 *
 * The driver's enum converters answer for an enum **column**; nothing about them reaches a value that travels
 * as JSON instead, where kotlinx.serialization writes the Kotlin constant's own name. That leaves one value
 * spelled two ways depending on where it is stored, and a query filtering on `payload ->> 'office'` matching
 * neither reliably.
 *
 * It is built from the registration and not from an annotation, so the enum named at `registerEnum` and the
 * one a classpath scan found by `@PgEnumType` are covered alike: both arrive in the driver's registry the
 * same way, carrying the conventions they were registered under.
 *
 * @property enumClass The Kotlin enum this serializes.
 * @param registration What it was registered as, which is where the two conventions come from.
 */
internal class PgEnumSerializer(
    private val enumClass: KClass<*>,
    registration: PgEnumRegistration
) : KSerializer<Any> {

    private val enumToPg: Map<Any, String> = enumClass.java.enumConstants.associate { constant ->
        constant as Any to CaseConverter.convert(
            (constant as Enum<*>).name,
            registration.kotlinConvention,
            registration.pgConvention
        )
    }

    private val pgToEnum: Map<String, Any> = enumToPg.entries.associate { (constant, label) -> label to constant }

    private val typeName: QualifiedName = registration.qualifiedName

    // The class's own name, which is what keeps two enums' descriptors from colliding in one format.
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor(
        enumClass.qualifiedName ?: typeName.toString(),
        PrimitiveKind.STRING
    )

    override fun serialize(encoder: Encoder, value: Any) {
        val label = enumToPg[value] ?: throw SerializationException(
            "$value is not a constant of ${enumClass.simpleName}, which is what this serializer writes."
        )
        encoder.encodeString(label)
    }

    override fun deserialize(decoder: Decoder): Any {
        val label = decoder.decodeString()
        return pgToEnum[label] ?: throw SerializationException(
            "'$label' is not a label of ${enumClass.simpleName}, which is registered as $typeName with " +
                "labels ${enumToPg.values.sorted().joinToString(", ")}."
        )
    }
}

/**
 * Builds the contextual module for a set of enum registrations, and hands back the same one until that set
 * changes.
 *
 * The driver replaces [ConverterRegistry.registeredEnums] wholesale on each registration, so the map's
 * identity *is* the version: a reader that sees the map it saw last time gets the module it built last time,
 * for the price of a volatile read and a reference comparison.
 */
internal class PgEnumSerializersModule {

    @Volatile
    private var cached: Pair<Map<KClass<*>, PgEnumRegistration>, SerializersModule>? = null

    fun resolve(registry: ConverterRegistry): SerializersModule {
        val source = registry.registeredEnums
        cached?.let { (from, module) -> if (from === source) return module }

        @Suppress("UNCHECKED_CAST")
        val module = SerializersModule {
            source.forEach { (kClass, registration) ->
                contextual(kClass as KClass<Any>, PgEnumSerializer(kClass, registration) as KSerializer<Any>)
            }
        }
        cached = source to module
        return module
    }
}

/**
 * A [Json] with the registered enums' serializers folded into it, rebuilt only when that set changes.
 *
 * The two cannot simply be composed once: a client is built before anything is registered on it, and the
 * enums arrive afterwards - named one by one, or all at once by a classpath scan. So the base is kept as it
 * was given and the derived form is resolved per conversion, off the same identity check
 * [PgEnumSerializersModule] uses.
 *
 * The base's own registrations win over the generated ones, so an application that wrote a serializer for one
 * of its enums keeps it and the rest are filled in.
 *
 * @property base The [Json] as it was given, which is what is used where no enum is registered at all.
 */
internal class EnumAwareJson(private val base: Json) {

    private val enumModule = PgEnumSerializersModule()

    @Volatile
    private var cached: Pair<SerializersModule, Json>? = null

    fun resolve(registry: ConverterRegistry): Json {
        if (registry.registeredEnums.isEmpty()) return base

        val module = enumModule.resolve(registry)
        cached?.let { (from, json) -> if (from === module) return json }

        val derived = Json(base) { serializersModule = module.overwriteWith(base.serializersModule) }
        cached = module to derived
        return derived
    }
}
