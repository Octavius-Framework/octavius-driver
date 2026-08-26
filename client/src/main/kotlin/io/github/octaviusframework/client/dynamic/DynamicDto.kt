package io.github.octaviusframework.client.dynamic

/**
 * The Kotlin side of the `dynamic_dto` composite: a discriminator and a JSON payload.
 *
 * A `COMPOSITE` type says at schema level what shape a column holds, which is right where the shape is fixed
 * and wrong where it is not. `dynamic_dto` is the other case - one column holding whichever of several
 * unrelated shapes this row happens to carry, with `type_name` saying which.
 *
 * Neither direction needs this type to be named. A `dynamic_dto` column comes back as the registered class
 * itself, and a column holding several of them comes back as their common supertype; going the other way, an
 * instance of a registered class is written as one on the terms [DynamicTypes.strategy] sets. What this type
 * is for is the two places inference does not reach: a value whose destination is ambiguous because its class
 * is registered as a composite as well, and a caller who wants the payload as it is stored rather than as a
 * class.
 *
 * [DynamicTypes.toDynamicDto] is the only way to make one, which is what keeps the discriminator honest. A
 * name is checked against the registry there and nowhere else - the column is a plain `(text, jsonb)` and the
 * server has never heard of the registry, so a value built with a name nothing is registered under would
 * store cleanly and fail on whichever read reached it first, in some other process, much later.
 *
 * @property typeName The discriminator, as it was registered and as the database stores it.
 * @property dataPayload The object's state as JSON text.
 *
 * Text rather than a `JsonElement`, because that is what the column holds and what the wire carries either
 * way: `jsonb`'s codec encodes and decodes a `String`, so this is the form both directions already pass
 * through and a tree would be one materialization each way that nothing asked for. A tree is one call off -
 * `Json.parseToJsonElement`, on [DynamicTypes.json] to read it the way the payload was written. What this
 * does not do is check that the text is JSON; PostgreSQL does, when the value reaches it.
 */
@ConsistentCopyVisibility
data class DynamicDto internal constructor(
    val typeName: String,
    val dataPayload: String
)
