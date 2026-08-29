package io.github.octaviusframework.serializer

import io.github.octaviusframework.type.BigDecimal
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlin.time.Instant

/**
 * The contextual serializers for the four types whose JSON form does not match their column form.
 *
 * Every one of them is a type the driver already maps correctly in a column of its own, and gets wrong the
 * moment the same value goes through JSON instead - a `numeric` rounded to a `Double`, an unbounded date
 * written as the year 999999999. A `dynamic_dto` payload and a `jsonb` column are both JSON, so both are
 * where that happens.
 *
 * - [BigDecimal] through [BigDecimalAsNumberSerializer], keeping the digits and the JSON number type.
 * - [LocalDate], [LocalDateTime] and [Instant] through the `WithInfinity` serializers, keeping PostgreSQL's
 *   `infinity` and `-infinity`.
 *
 * Contextual, so it changes nothing until a property asks for it:
 *
 * ```kotlin
 * @Serializable
 * @DynamicallyMappable("land_grant")
 * data class LandGrant(
 *     val province: String,
 *     @Contextual val iugera: BigDecimal,
 *     @Contextual val until: LocalDate
 * )
 * ```
 *
 * The client's own `dynamicJson` already carries it, so a payload written through Octavius needs nothing
 * added. This is for the [Json] built elsewhere - the frontend that reads the same class, or an HTTP layer
 * that has one of its own:
 *
 * ```kotlin
 * val json = Json {
 *     serializersModule = octaviusSerializersModule + myAppModule
 *     ignoreUnknownKeys = true
 * }
 * ```
 *
 * @see octaviusJson for the case where the module is the only thing being configured.
 */
val octaviusSerializersModule: SerializersModule = SerializersModule {
    contextual(BigDecimal::class, BigDecimalAsNumberSerializer)
    contextual(LocalDate::class, LocalDateWithInfinitySerializer)
    contextual(LocalDateTime::class, LocalDateTimeWithInfinitySerializer)
    contextual(Instant::class, InstantWithInfinitySerializer)
}

/**
 * A [Json] carrying [octaviusSerializersModule] and otherwise left alone.
 *
 * Strict, as `Json` is: a payload carrying a field the class does not declare is an error rather than
 * something dropped. That is the client's default for `dynamic_dto` too, and this is the same instance to
 * reach for on the other side of the wire - a JS frontend decoding a class the backend wrote.
 *
 * Anything further - `ignoreUnknownKeys`, a naming strategy, a module of your own - is a `Json { }` of your
 * own with [octaviusSerializersModule] in it.
 */
val octaviusJson: Json = Json {
    serializersModule = octaviusSerializersModule
}
