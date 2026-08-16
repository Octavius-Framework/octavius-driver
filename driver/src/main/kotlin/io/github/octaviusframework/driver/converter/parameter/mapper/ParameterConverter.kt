package io.github.octaviusframework.driver.converter.parameter.mapper

import io.github.octaviusframework.driver.identifier.QualifiedName
import kotlin.reflect.KClass

/**
 * Turns a Kotlin value into something a codec can encode for the wire.
 *
 * This is the write half of the conversion SPI, and the mirror of
 * [ResultConverter][io.github.octaviusframework.driver.converter.result.mapper.ResultConverter]. A
 * converter does not produce bytes: it produces a value some registered codec knows how to encode — a
 * `String` for an enum label, a `PgComposite` for a data class — and the codec does the rest. Register
 * one on a session through
 * [TypeManager.registerParameterConverter][io.github.octaviusframework.driver.registry.TypeManager.registerParameterConverter],
 * or on a single query through
 * [registerParameterConverter][io.github.octaviusframework.driver.query.OctaviusQuery.registerParameterConverter].
 *
 * Converters are consulted in reverse registration order, so a later one wins over an earlier one, and
 * the first whose [canConvert] answers `true` takes the value. A value nothing claims is passed through
 * untouched, which is right for a scalar the codec already accepts; where the target OID is known and
 * its codec cannot accept that class, the driver raises `MappingException` (`NO_CONVERTER_FOUND`)
 * naming both sides instead of letting it fail one layer down as an encoding error.
 *
 * @param T The Kotlin type this converter accepts.
 */
interface ParameterConverter<T : Any> {
    /**
     * The Kotlin class this converter handles. The default [canConvert] also accepts its subtypes.
     */
    val supportedClass: KClass<T>

    /**
     * Decides whether this converter handles the value at hand.
     *
     * The default accepts [supportedClass] and anything assignable to it, ignoring the target type.
     * Override to narrow that — to decline a class that has not been registered, or to claim a value
     * only for a particular [expectedOid].
     *
     * @param sourceClass The class of the value being sent.
     * @param expectedOid The OID the server expects, or `0` when it is not yet known.
     * @param context Access to the type manager and to nested conversion.
     * @return `true` to claim the value.
     */
    fun canConvert(sourceClass: KClass<*>, expectedOid: Int, context: SerializationContext): Boolean {
        return supportedClass == sourceClass || supportedClass.java.isAssignableFrom(sourceClass.java)
    }

    /**
     * Converts a value this converter has claimed into something a codec can encode.
     *
     * Nested values should go through [SerializationContext.convert] rather than being converted by
     * hand, so that a failure deeper in the structure arrives with the path to it.
     *
     * @param source The value being sent.
     * @param expectedOid The OID the server expects, or `0` when it is not yet known.
     * @param context Access to the type manager and to nested conversion.
     * @return A value some registered codec can encode.
     */
    fun convert(source: T, expectedOid: Int, context: SerializationContext): Any

    /**
     * Names the PostgreSQL type to declare for the converted value, where nothing else has named one.
     *
     * Consulted only when [expectedOid] was unresolved and [convert] returned something that does not
     * already carry its own type. Returning `null`, the default, means the declared type is worked out
     * from the **converted value's Kotlin class** instead, through the codec registered for it — which
     * is why an unclaimed `String` always goes out as `text`.
     *
     * That default is exactly what a converter producing a stand-in type has to override. An enum
     * converter returns a `String`, and left alone it would be declared `text`, which PostgreSQL will
     * not accept where an enum type is wanted; naming the enum type here is what gets the value
     * declared as `mood` rather than `text`.
     *
     * @param sourceClass The class of the value being sent, before conversion.
     * @param context Access to the type manager.
     * @return The type to declare, or `null` to let the converted value's own codec decide.
     */
    fun getDefaultTypeName(sourceClass: KClass<*>, context: SerializationContext): QualifiedName? = null
}

