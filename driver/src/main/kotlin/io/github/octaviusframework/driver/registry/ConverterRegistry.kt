package io.github.octaviusframework.driver.registry

import io.github.octaviusframework.driver.util.reflection.ReflectionCache
import io.github.octaviusframework.driver.converter.parameter.array.CollectionArrayParameterConverter
import io.github.octaviusframework.driver.converter.parameter.array.PrimitiveArrayParameterConverter
import io.github.octaviusframework.driver.converter.parameter.composite.ReflectionCompositeParameterConverter
import io.github.octaviusframework.driver.converter.parameter.mapper.ParameterConverter
import io.github.octaviusframework.driver.converter.parameter.mapper.ParameterConverterRegistry
import io.github.octaviusframework.driver.converter.parameter.range.MultiRangeParameterConverter
import io.github.octaviusframework.driver.converter.parameter.range.RangeParameterConverter
import io.github.octaviusframework.driver.converter.parameter.standard.JsonElementParameterConverter
import io.github.octaviusframework.driver.converter.result.array.CollectionArrayConverter
import io.github.octaviusframework.driver.converter.result.array.PrimitiveArrayConverter
import io.github.octaviusframework.driver.converter.result.composite.MapCompositeConverter
import io.github.octaviusframework.driver.converter.result.composite.ReflectionCompositeConverter
import io.github.octaviusframework.driver.converter.result.mapper.ResultConverter
import io.github.octaviusframework.driver.converter.result.mapper.ResultConverterRegistry
import io.github.octaviusframework.driver.converter.result.range.MultiRangeResultConverter
import io.github.octaviusframework.driver.converter.result.range.RangeResultConverter
import io.github.octaviusframework.driver.converter.result.record.MapRecordConverter
import io.github.octaviusframework.driver.converter.result.row.MapRowConverter
import io.github.octaviusframework.driver.converter.result.row.ReflectionRowConverter
import io.github.octaviusframework.driver.converter.result.standard.JsonElementConverter
import io.github.octaviusframework.driver.exception.InvalidOperationException
import io.github.octaviusframework.driver.exception.InvalidOperationExceptionReason
import io.github.octaviusframework.driver.identifier.QualifiedName
import io.github.octaviusframework.identifier.CaseConvention
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.reflect.KClass

/**
 * A registry managing the converters used for mapping between Kotlin objects and PostgreSQL types.
 *
 * It holds both [ResultConverterRegistry] for handling incoming data from the database,
 * and [ParameterConverterRegistry] for handling outbound query parameters. 
 * Furthermore, it keeps track of registered composite type mappings.
 */
class ConverterRegistry internal constructor() {

    //------------------------------------------Registries--------------------------------------------------------------

    /**
     * The registry responsible for result conversion (PostgreSQL -> Kotlin).
     */
    val resultConverterRegistry = ResultConverterRegistry().apply {
        addConverter(MapCompositeConverter)
        addConverter(PrimitiveArrayConverter)
        addConverter(CollectionArrayConverter)
        addConverter(ReflectionCompositeConverter)
        addConverter(ReflectionRowConverter)
        addConverter(MapRowConverter)
        addConverter(MapRecordConverter)
        addConverter(JsonElementConverter)
        addConverter(RangeResultConverter)
        addConverter(MultiRangeResultConverter)
    }

    /**
     * The registry responsible for parameter conversion (Kotlin -> PostgreSQL).
     */
    val parameterConverterRegistry = ParameterConverterRegistry().apply {
        addConverter(PrimitiveArrayParameterConverter)
        addConverter(CollectionArrayParameterConverter)
        addConverter(ReflectionCompositeParameterConverter)
        addConverter(JsonElementParameterConverter)
        addConverter(RangeParameterConverter)
        addConverter(MultiRangeParameterConverter)
    }

    /**
     * Registers a custom [ResultConverter].
     *
     * @param converter the converter to register.
     */
    internal fun registerResultConverter(converter: ResultConverter<*, *>) {
        resultConverterRegistry.addConverter(converter)
    }

    /**
     * Registers a custom [ParameterConverter].
     *
     * @param converter the converter to register.
     */
    internal fun registerParameterConverter(converter: ParameterConverter<*>) {
        parameterConverterRegistry.addConverter(converter)
    }

    //--------------------------------------------Composites------------------------------------------------------------
    private val lock = ReentrantLock()

    /**
     * A thread-safe map holding registration details for custom Kotlin composite data classes.
     */
    @Volatile
    var registeredComposites: Map<KClass<*>, QualifiedName> = emptyMap()
        private set

    /**
     * A thread-safe map mapping database composite names to their corresponding Kotlin classes.
     */
    @Volatile
    var compositeClassByName: Map<QualifiedName, KClass<*>> = emptyMap()
        private set

    /**
     * Registers a Kotlin data class to be automatically mapped to and from a PostgreSQL composite type.
     *
     * @param kClass the Kotlin data class to register.
     * @param name the name of the composite type in PostgreSQL.
     * @param schema the schema of the composite type (defaults to an empty string for the search path).
     * @throws InvalidOperationException if [kClass] is not a data class.
     */
    internal fun registerAutoCompositeType(
        kClass: KClass<*>,
        name: String,
        schema: String = ""
    ) {
        // Reflective mapping reads every primary constructor parameter back as a property, which is exactly what
        // a data class guarantees. Rejecting anything else here beats a null property lookup at query time.
        if (!kClass.isData) {
            throw InvalidOperationException(
                InvalidOperationExceptionReason.INVALID_ARGUMENT,
                "Class ${kClass.qualifiedName ?: kClass.simpleName} is not a data class and cannot be registered " +
                        "as composite type '$name'. Reflective mapping reads every primary constructor parameter " +
                        "back as a property, which only a data class guarantees. Write a ResultConverter and " +
                        "ParameterConverter pair for any other shape."
            )
        }

        // Warms the metadata cache before the registration is visible, so the first query does not pay for it.
        ReflectionCache.getOrCreateDataObjectMetadata(kClass)

        lock.withLock {
            val newMap = registeredComposites.toMutableMap()
            val qName = QualifiedName(schema, name)
            newMap[kClass] = qName
            registeredComposites = newMap

            val newNameMap = compositeClassByName.toMutableMap()
            newNameMap[qName] = kClass
            compositeClassByName = newNameMap
        }
    }

    //--------------------------------------------Enums----------------------------------------------------------------

    /**
     * A thread-safe map holding what every registered Kotlin enum was registered as.
     *
     * The converters carry the same facts already, but privately and one direction each. Written down here
     * they can be read by something that is not a conversion - which is the point: an enum means one thing in
     * a column of its own and another inside JSON, and only the layer holding the JSON can settle the second.
     * The driver states the mapping; it does not interpret it.
     */
    @Volatile
    var registeredEnums: Map<KClass<*>, PgEnumRegistration> = emptyMap()
        private set

    /**
     * Records that [kClass] is the PostgreSQL enum [qualifiedName], under the conventions the converters were
     * built with.
     */
    internal fun registerEnumType(kClass: KClass<*>, qualifiedName: QualifiedName, pgConvention: CaseConvention, kotlinConvention: CaseConvention) {
        lock.withLock {
            registeredEnums = registeredEnums +
                (kClass to PgEnumRegistration(qualifiedName, pgConvention, kotlinConvention))
        }
    }
}

/**
 * What an enum was registered as: the type it stands for, and the two conventions that map one side's names
 * onto the other's.
 *
 * @property qualifiedName The PostgreSQL enum type this class stands for.
 * @property pgConvention How the labels are written in PostgreSQL.
 * @property kotlinConvention How the constants are written in Kotlin.
 */
data class PgEnumRegistration(
    val qualifiedName: QualifiedName,
    val pgConvention: CaseConvention,
    val kotlinConvention: CaseConvention
)
