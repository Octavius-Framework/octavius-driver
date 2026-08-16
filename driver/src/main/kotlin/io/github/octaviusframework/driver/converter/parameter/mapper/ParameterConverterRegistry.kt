package io.github.octaviusframework.driver.converter.parameter.mapper

import io.github.octaviusframework.driver.container.PgContainer
import io.github.octaviusframework.driver.exception.MappingException
import io.github.octaviusframework.driver.exception.MappingExceptionReason
import io.github.octaviusframework.driver.type.PgTyped
import io.github.octaviusframework.driver.type.isKnownOid
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.reflect.KClass

/**
 * Holds [ParameterConverter]s and runs a value through the first one that claims it.
 *
 * Unlike the result side, converters are kept in one flat list rather than indexed by class, since a
 * parameter converter decides for itself what it accepts. The list is searched most-recent-first and
 * then handed on to the [parent] registry, so a registration overrides an earlier one without removing it.
 *
 * Registration is thread-safe and reads are lock-free: adding a converter replaces the list rather than
 * mutating it, so a lookup already in flight completes against the list it started with.
 *
 * @param parent The registry to fall back to when nothing here claims a value.
 */
class ParameterConverterRegistry(
    private val parent: ParameterConverterRegistry? = null
) {
    private val lock = ReentrantLock()

    @Volatile
    private var converters: List<ParameterConverter<*>> = emptyList()

    /**
     * Registers a converter ahead of everything already here.
     *
     * @param converter The converter to add.
     */
    fun addConverter(converter: ParameterConverter<*>) = lock.withLock {
        val newList = converters.toMutableList()
        newList.add(0, converter)
        converters = newList
    }

    /**
     * Runs a value through the first converter that claims it.
     *
     * Where the target OID was not known and the converter named a default type for the value, the
     * result is wrapped in [PgTyped] so the type reaches the server with it.
     *
     * A value nothing claims is returned untouched — correct for a scalar the codec already accepts.
     * Where the target OID is known and its codec cannot accept that class, nothing downstream can
     * either, so this fails here rather than one layer down as an encoding error: failing at this point
     * is what keeps the attribute name or element index in the exception's `path`, since by the time the
     * codec runs the structure the value sat in is gone.
     *
     * @param source The value being sent.
     * @param expectedOid The OID the server expects, or `0` when it is not known.
     * @param context Passed on to the converters.
     * @return A value a registered codec can encode.
     * @throws MappingException `NO_CONVERTER_FOUND` if nothing claims the value and the codec bound to
     *   [expectedOid] cannot accept its class.
     */
    fun convert(source: Any, expectedOid: Int, context: SerializationContext): Any {
        for (i in converters.indices) {
            val converter = converters[i]
            if (converter.canConvert(source::class, expectedOid, context)) {
                @Suppress("UNCHECKED_CAST")
                var result = (converter as ParameterConverter<Any>).convert(source, expectedOid, context)
                // A PgContainer already carries its own OID, so wrapping it would only force
                // the serializer to resolve a name it then discards in favour of containerOid.
                if (result !is PgTyped && result !is PgContainer && !expectedOid.isKnownOid) {
                    val defaultType = converter.getDefaultTypeName(source::class, context)
                    if (defaultType != null) {
                        result = PgTyped(result, defaultType)
                    }
                }
                return result
            }
        }

        val parentRegistry = parent
        if (parentRegistry != null) return parentRegistry.convert(source, expectedOid, context)

        // End of the chain: nothing claimed the value. Passing it through untouched is right for a scalar the
        // codec takes as it stands, but where the target OID is known and its codec cannot accept this class,
        // nothing downstream can either. Failing here is what keeps the attribute name in the path - by the time
        // the codec runs, the structure the value sat in is gone. The read direction reports the same mistake
        // the same way, as MappingException(NO_CONVERTER_FOUND).
        rejectIfCodecCannotAccept(source, expectedOid, context)
        return source
    }

    private fun rejectIfCodecCannotAccept(source: Any, expectedOid: Int, context: SerializationContext) {
        if (!expectedOid.isKnownOid) return
        val codec = context.typeManager.codecDictionary.getCodecByOid<Any>(expectedOid) ?: return
        if (codec.kotlinClass.isInstance(source)) return

        throw MappingException(
            MappingExceptionReason.NO_CONVERTER_FOUND,
            "No converter found for source class ${source::class.qualifiedName ?: source::class} and expected " +
                    "type ${codec.pgTypeName} (OID $expectedOid), which encodes " +
                    "${codec.kotlinClass.qualifiedName ?: codec.kotlinClass}"
        )
    }

    /**
     * Finds the converter that would claim a value, without converting anything.
     *
     * @param source The value being sent.
     * @param expectedOid The OID the server expects, or `0` when it is not known.
     * @param context Passed on to each candidate's `canConvert`.
     * @return The first converter to claim the value, or `null` if none does.
     */
    fun findConverter(source: Any, expectedOid: Int, context: SerializationContext): ParameterConverter<Any>? {
        return findConverterByClass(source::class, expectedOid, context)
    }

    /**
     * Same as [findConverter], keyed on a class rather than an instance.
     *
     * @param sourceClass The class of the value being sent.
     * @param expectedOid The OID the server expects, or `0` when it is not known.
     * @param context Passed on to each candidate's `canConvert`.
     * @return The first converter to claim the class, or `null` if none does.
     */
    fun findConverterByClass(sourceClass: KClass<*>, expectedOid: Int, context: SerializationContext): ParameterConverter<Any>? {
        for (i in converters.indices) {
            val converter = converters[i]
            if (converter.canConvert(sourceClass, expectedOid, context)) {
                @Suppress("UNCHECKED_CAST")
                return converter as ParameterConverter<Any>
            }
        }
        return parent?.findConverterByClass(sourceClass, expectedOid, context)
    }
}