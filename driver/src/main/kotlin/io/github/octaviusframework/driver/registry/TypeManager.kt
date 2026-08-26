package io.github.octaviusframework.driver.registry

import io.github.octaviusframework.driver.codec.TypeCodec
import io.github.octaviusframework.driver.converter.EnumParameterConverter
import io.github.octaviusframework.driver.converter.EnumResultConverter
import io.github.octaviusframework.driver.converter.parameter.mapper.ParameterConverter
import io.github.octaviusframework.driver.converter.result.mapper.ResultConverter
import io.github.octaviusframework.identifier.CaseConvention
import io.github.octaviusframework.driver.identifier.CaseConverter
import io.github.octaviusframework.driver.identifier.QualifiedName
import io.github.octaviusframework.driver.registry.TypeRegistry
import kotlin.reflect.KClass

/**
 * Manages the registration and resolution of PostgreSQL types, codecs, and converters.
 *
 * This class provides a high-level API over [TypeRegistry], making it easier to
 * register custom mappings between Kotlin types and PostgreSQL database types.
 *
 * @property registry The underlying [TypeRegistry] used for storing type information.
 */
class TypeManager internal constructor(
    private val registry: TypeRegistry,
    private val searchPathProvider: () -> List<String> = { emptyList() }
) {
    /**
     * The registry handling the conversion of parameters and results.
     */
    val converterRegistry get() = registry.converterRegistry
    
    /**
     * The dictionary mapping PostgreSQL type names to their OIDs and vice versa.
     */
    val typeDictionary get() = registry.dictionary

    /**
     * The dictionary maintaining [TypeCodec] implementations.
     */
    val codecDictionary get() = registry.codecs

    /**
     * Factory for creating container types (like composites).
     */
    val containers = ContainerFactory(this)

    /**
     * Resolves an OID for a given type name, considering the current search path.
     */
    fun resolveOid(
        typeName: String,
        schema: String = "",
        isArray: Boolean = false
    ): Int {
        return registry.dictionary.resolveOid(typeName, schema, isArray, searchPathProvider())
    }

    /**
     * Registers a custom [ResultConverter] for mapping PostgreSQL database types to Kotlin types.
     *
     * @param converter The converter instance to register.
     */
    fun registerResultConverter(converter: ResultConverter<*, *>) = converterRegistry.registerResultConverter(converter)

    /**
     * Registers a custom [ParameterConverter] for mapping Kotlin types to PostgreSQL database types.
     *
     * @param converter The converter instance to register.
     */
    fun registerParameterConverter(converter: ParameterConverter<*>) = converterRegistry.registerParameterConverter(converter)

    /**
     * Registers a custom [TypeCodec] for encoding and decoding
     * database types at the lowest level.
     *
     * @param codec The codec instance to register.
     */
    fun registerCodec(codec: TypeCodec<*>) = registry.registerCodec(codec)

    /**
     * Registers a composite type mapped reflectively onto the data class [T].
     *
     * Property names are matched to attribute names by converting `camelCase` to `snake_case`;
     * [PgName][io.github.octaviusframework.annotation.PgName] overrides that per property.
     *
     * @param T The Kotlin data class representing the composite type.
     * @param typeName Optional custom type name in the database. If empty, the name is derived from the class name
     *   by converting `PascalCase` to `snake_case`.
     * @param schema Optional schema where the type is defined. If empty, the type is resolved through the search path.
     * @throws io.github.octaviusframework.driver.exception.InvalidOperationException if [T] is not a data class.
     */
    inline fun <reified T : Any> registerAutoComposite(
        typeName: String = "",
        schema: String = ""
    ) {
        registerAutoComposite(T::class, typeName, schema)
    }

    /**
     * Registers a composite type mapped reflectively onto the data class [kClass].
     *
     * @param kClass The Kotlin data class representing the composite type.
     * @param typeName Optional custom type name in the database. If empty, the name is derived from the class name
     *   by converting `PascalCase` to `snake_case`.
     * @param schema Optional schema where the type is defined. If empty, the type is resolved through the search path.
     * @throws io.github.octaviusframework.driver.exception.InvalidOperationException if [kClass] is not a data class.
     */
    fun registerAutoComposite(
        kClass: KClass<*>,
        typeName: String = "",
        schema: String = ""
    ) {
        val qName = typeName.takeIf { it.isNotEmpty() } ?: CaseConverter.convert(
            kClass.simpleName!!,
            CaseConvention.PASCAL_CASE,
            CaseConvention.SNAKE_CASE_LOWER
        )
        converterRegistry.registerAutoCompositeType(kClass, qName, schema)
    }

    /**
     * Registers an enum type, creating both parameter and result converters.
     *
     * @param T The Kotlin enum class.
     * @param typeName Optional custom type name in the database.
     * @param schema Optional schema where the enum is defined.
     * @param pgConvention The naming convention used for enum values in PostgreSQL.
     * @param kotlinConvention The naming convention used for enum values in Kotlin.
     */
    inline fun <reified T : Enum<T>> registerEnum(
        typeName: String = "",
        schema: String = "",
        pgConvention: CaseConvention = CaseConvention.SNAKE_CASE_UPPER,
        kotlinConvention: CaseConvention = CaseConvention.PASCAL_CASE
    ) {
        registerEnum(T::class, typeName, schema, pgConvention, kotlinConvention)
    }

    /**
     * Registers an enum type, creating both parameter and result converters.
     *
     * @param enumClass The Kotlin enum class.
     * @param typeName Optional custom type name in the database.
     * @param schema Optional schema where the enum is defined.
     * @param pgConvention The naming convention used for enum values in PostgreSQL.
     * @param kotlinConvention The naming convention used for enum values in Kotlin.
     */
    fun <T : Enum<T>> registerEnum(
        enumClass: KClass<T>,
        typeName: String = "",
        schema: String = "",
        pgConvention: CaseConvention = CaseConvention.SNAKE_CASE_UPPER,
        kotlinConvention: CaseConvention = CaseConvention.PASCAL_CASE
    ) {
        val actualTypeName = typeName.takeIf { it.isNotEmpty() } ?: CaseConverter.convert(
            enumClass.simpleName!!, CaseConvention.PASCAL_CASE, CaseConvention.SNAKE_CASE_LOWER
        )

        val actualSchema = schema.takeIf { it.isNotEmpty() } ?: ""
        val qualifiedName = QualifiedName(actualSchema, actualTypeName)
        registerParameterConverter(EnumParameterConverter(enumClass, qualifiedName, pgConvention, kotlinConvention))
        registerResultConverter(EnumResultConverter(enumClass, qualifiedName, pgConvention, kotlinConvention))
    }
}
