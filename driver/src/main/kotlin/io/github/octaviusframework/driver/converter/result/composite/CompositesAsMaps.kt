package io.github.octaviusframework.driver.converter.result.composite

import io.github.octaviusframework.driver.container.PgComposite
import io.github.octaviusframework.driver.converter.result.mapper.DeserializationContext
import io.github.octaviusframework.driver.converter.result.mapper.ResultConverter
import io.github.octaviusframework.driver.identifier.QualifiedName
import io.github.octaviusframework.driver.type.PgType
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.typeOf

/**
 * A converter that reads every composite as a `Map<String, Any?>`, whatever it is registered as.
 *
 * Registration is global to the database - it is what a composite *is* to every session pointing there - so
 * it is not where one report saying "give me the shape, not my classes" can be answered. This is: register it
 * on the query and it applies to that query and is discarded with it.
 *
 * ```kotlin
 * session.createNativeQuery("SELECT * FROM veterans")
 *     .registerResultConverter(compositesAsMaps())
 *     .fetchObjects<Map<String, Any?>>()
 * ```
 *
 * It collapses the **whole subtree**, which it gets for free rather than by walking one: attributes are
 * converted as `Any?`, a nested composite asked for as `Any?` reaches this same converter again, and an array
 * of composites hands its elements down the same way. An anonymous `ROW(...)` already answers to a map on its
 * own. A composite registered nowhere collapses along with the rest, which is what stops one turning up as a
 * raw `PgComposite` three levels down - the one place asking for `Map<String, Any?>` does not reach today.
 *
 * **Naming a class still gets you the class.** This claims a value only where the caller asked for `Any` or
 * `Map` - which is what "no preference" and "a dictionary" look like - so `row.get<Address>("residence")` in
 * the same query is untouched. What changes is the default view of a composite, not what an explicit ask
 * means.
 *
 * @return A converter to hand to `registerResultConverter` on a query.
 * @see compositesAsMaps for the one-type form.
 */
fun compositesAsMaps(): ResultConverter<PgComposite, Map<String, Any?>> = CompositesAsMapsConverter(null)

/**
 * The same, for one composite type: values of that type come back as maps and every other composite still
 * maps onto whatever class it is registered as.
 *
 * ```kotlin
 * session.createNativeQuery("SELECT assessment FROM censuses")
 *     .registerResultConverter(compositesAsMaps("tribute"))
 *     .fetchField<Map<String, Any?>>()      // {label=census, payload={amount=40, currency=denarius}}
 * ```
 *
 * The type is matched where it is met, so a `tribute` nested inside an `assessment` collapses as well and the
 * `assessment` around it does not. For several types, name them all in one of these rather than registering
 * one apiece - see the [Collection][compositesAsMaps] overload.
 *
 * @param name The composite type's name in PostgreSQL.
 * @param schema Its schema. Left empty, the name matches whichever schema the value came from, which is the
 *   right reading when the type is reached through the search path rather than written out.
 * @return A converter to hand to `registerResultConverter` on a query.
 */
fun compositesAsMaps(name: String, schema: String = ""): ResultConverter<PgComposite, Map<String, Any?>> =
    CompositesAsMapsConverter(arrayOf(QualifiedName(schema, name)))

/**
 * The same, for several named types at once.
 *
 * Prefer this to registering one converter per type: every converter on the query is asked about every
 * composite it decodes, and this is one of them however many names it holds. At a handful of types the
 * difference is not measurable against what decoding a composite costs anyway; by fifty it is about a third
 * of the lookup, the array scan here being cheaper than fifty calls through a registry.
 *
 * @param types The composite types to collapse. A [QualifiedName] with an empty schema matches the name
 *   wherever the value came from.
 * @return A converter to hand to `registerResultConverter` on a query.
 */
fun compositesAsMaps(types: Collection<QualifiedName>): ResultConverter<PgComposite, Map<String, Any?>> =
    CompositesAsMapsConverter(types.toTypedArray())

/**
 * @param only The types to collapse, or `null` to collapse every composite.
 */
private class CompositesAsMapsConverter(
    private val only: Array<QualifiedName>?
) : ResultConverter<PgComposite, Map<String, Any?>> {

    override val supportedSourceClass = PgComposite::class

    override fun canConvert(sourceClass: KClass<*>, expectedType: KType, sourceType: PgType, context: DeserializationContext): Boolean {
        // The name goes first because it is the cheap half and the one that usually says no: a converter
        // named for one type is asked about every composite in the query and declines almost all of them,
        // and a field comparison is what that decline should cost. Reading `expectedType.classifier` is a
        // lazy reflective property, which is enough dearer to show up once several of these are registered.
        val targets = only
        if (targets != null && !names(targets, sourceType)) return false

        // Anything narrower was asked for by name and is left alone: this changes what a composite is by
        // default, not what naming a class means.
        val kClass = expectedType.classifier as? KClass<*> ?: return false
        return kClass == Any::class || kClass == Map::class
    }

    /** Whether any of [targets] is [sourceType], an empty schema on a target matching whichever it came from. */
    private fun names(targets: Array<QualifiedName>, sourceType: PgType): Boolean {
        for (i in targets.indices) {
            val target = targets[i]
            if (sourceType.name != target.name) continue
            if (target.schema.isEmpty() || sourceType.schema == target.schema) return true
        }
        return false
    }

    override fun convert(source: PgComposite, expectedType: KType, sourceType: PgType, context: DeserializationContext): Map<String, Any?> {
        // A caller who wrote the value type out meant it - `Map<String, Address>` is an ask, not a default -
        // so only the `Any` side recurses through this converter.
        val valueType = if (expectedType.classifier == Map::class) {
            expectedType.arguments.getOrNull(1)?.type ?: typeOf<Any?>()
        } else {
            typeOf<Any?>()
        }

        return source.attributesAsMap(valueType, context)
    }
}
