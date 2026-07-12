package io.github.octaviusframework.driver.type

import io.github.octaviusframework.driver.codec.TypeCodec
import io.github.octaviusframework.driver.container.ArrayDimension
import io.github.octaviusframework.driver.container.PgArray
import io.github.octaviusframework.driver.container.PgComposite
import io.github.octaviusframework.driver.container.PgMultirange
import io.github.octaviusframework.driver.container.PgRange
import io.github.octaviusframework.driver.converter.EnumParameterConverter
import io.github.octaviusframework.driver.converter.EnumResultConverter
import io.github.octaviusframework.driver.converter.parameter.mapper.ParameterConverter
import io.github.octaviusframework.driver.converter.result.mapper.ResultConverter
import io.github.octaviusframework.driver.exception.OctaviusTypeException
import io.github.octaviusframework.driver.exception.TypeExceptionMessage
import io.github.octaviusframework.driver.identifier.CaseConvention
import io.github.octaviusframework.driver.identifier.CaseConverter
import io.github.octaviusframework.driver.identifier.QualifiedName
import io.github.octaviusframework.driver.registry.TypeRegistry

class TypeManager(
    val registry: TypeRegistry,
    private val searchPathProvider: () -> List<String> = { emptyList() }
) {
    /**
     * The underlying type registry associated with this TypeManager.
     */

    /**
     * Resolves an OID for a given type name, considering the current search path.
     */
    fun resolveOid(
        typeName: String,
        schema: String = "",
        isArray: Boolean = false
    ): Int {
        return registry.resolveOid(typeName, schema, isArray, searchPathProvider())
    }

    /**
     * Registers a custom [ResultConverter] for mapping PostgreSQL database types to Kotlin types.
     *
     * @param converter The converter instance to register.
     */
    fun registerResultConverter(converter: ResultConverter<*, *>) = registry.registerResultConverter(converter)

    /**
     * Registers a custom [ParameterConverter] for mapping Kotlin types to PostgreSQL database types.
     *
     * @param converter The converter instance to register.
     */
    fun registerParameterConverter(converter: ParameterConverter<*>) = registry.registerParameterConverter(converter)

    /**
     * Registers a custom [TypeCodec] for encoding and decoding
     * database types at the lowest level.
     *
     * @param codec The codec instance to register.
     */
    fun registerCodec(codec: TypeCodec<*>) = registry.registerCodec(codec, searchPathProvider())

    /**
     * Registers a composite type with the given configuration using reflection.
     *
     * @param T The Kotlin data class representing the composite type.
     * @param typeName Optional custom type name in the database. If empty, the name is derived from the class name.
     * @param schema Optional schema where the type is defined.
     * @param pgConvention Naming convention in the database.
     * @param kotlinConvention Naming convention in Kotlin.
     */
    inline fun <reified T : Any> registerAutoComposite(
        typeName: String = "",
        schema: String = "",
        pgConvention: CaseConvention = CaseConvention.SNAKE_CASE_LOWER,
        kotlinConvention: CaseConvention = CaseConvention.CAMEL_CASE
    ) {
        val qName = typeName.takeIf { it.isNotEmpty() } 
            ?: CaseConverter.convert(T::class.simpleName!!, CaseConvention.PASCAL_CASE, CaseConvention.SNAKE_CASE_LOWER)
        registry.registerAutoCompositeType<T>(qName, schema, pgConvention, kotlinConvention)
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
        val enumClass = T::class

        val actualTypeName = typeName.takeIf { it.isNotEmpty() } ?: CaseConverter.convert(
            enumClass.simpleName!!, CaseConvention.PASCAL_CASE, CaseConvention.SNAKE_CASE_LOWER
        )

        val actualSchema = schema.takeIf { it.isNotEmpty() } ?: ""
        val qualifiedName = QualifiedName(actualSchema, actualTypeName)
        registry.registerParameterConverter(EnumParameterConverter(enumClass, pgConvention, kotlinConvention))
        registry.registerResultConverter(EnumResultConverter(enumClass, qualifiedName, pgConvention, kotlinConvention, this))
    }

    /**
     * Creates a new instance of a PostgreSQL composite type using its name and schema.
     *
     * @param typeName The name of the composite type.
     * @param schema The schema where the composite type is defined.
     * @return A new [io.github.octaviusframework.driver.container.PgComposite] instance with empty fields.
     */
    fun createComposite(typeName: String, schema: String = ""): PgComposite {
        val resolvedOid = registry.resolveOid(typeName, schema, searchPath = searchPathProvider())
        return createComposite(resolvedOid)
    }

    /**
     * Creates a new instance of a PostgreSQL composite type using its Object ID (OID).
     *
     * @param oid The OID of the composite type.
     * @return A new [PgComposite] instance with empty fields.
     */
    fun createComposite(oid: Int): PgComposite {
        val pgType = registry.types[oid] as? PgType.Composite
            ?: throw OctaviusTypeException(TypeExceptionMessage.NOT_A_CONTAINER, oid = oid, details = "Type is not a composite or does not exist in TypeRegistry")
        val fields = Array<Any?>(pgType.attributes.size) { null }
        return PgComposite(pgType, fields, registry)
    }

    /**
     * Creates a new instance of a PostgreSQL array type using its name and schema.
     *
     * @param typeName The name of the array type.
     * @param schema The schema where the array type is defined.
     * @param dimensionSizes Sizes of the array dimensions.
     * @return A new [io.github.octaviusframework.driver.container.PgArray] instance initialized with nulls.
     */
    fun createArray(typeName: String, schema: String = "", vararg dimensionSizes: Int): PgArray {
        val resolvedOid = registry.resolveOid(typeName, schema, searchPath = searchPathProvider())
        return createArray(resolvedOid, *dimensionSizes)
    }

    /**
     * Creates a new instance of a PostgreSQL array type using its Object ID (OID).
     *
     * @param oid The OID of the array type.
     * @param dimensionSizes Sizes of the array dimensions.
     * @return A new [PgArray] instance initialized with nulls.
     */
    fun createArray(oid: Int, vararg dimensionSizes: Int): PgArray {
        require(dimensionSizes.isNotEmpty()) { "Array must have at least 1 dimension" }
        val arrayType = registry.types[oid] as? PgType.Array
            ?: throw OctaviusTypeException(TypeExceptionMessage.NOT_A_CONTAINER, oid = oid, details = "Type is not an array or does not exist in TypeRegistry")
            
        val dimensions = dimensionSizes.map { ArrayDimension(it, 1) }
        val totalSize = dimensionSizes.fold(1) { acc, size -> acc * size }
        val elements = MutableList<Any?>(totalSize) { null }
        return PgArray(arrayType.oid, arrayType.elementOid, dimensions, elements, registry)
    }

    /**
     * Creates a new instance of a PostgreSQL range type using its name and schema.
     *
     * @param typeName The name of the range type.
     * @param schema The schema where the range type is defined.
     * @param lower The lower bound value.
     * @param upper The upper bound value.
     * @param flags Range flags (e.g., inclusive/exclusive bounds).
     * @return A new [io.github.octaviusframework.driver.container.PgRange] instance.
     */
    fun createRange(
        typeName: String,
        schema: String = "",
        lower: Any? = null,
        upper: Any? = null,
        isLowerInclusive: Boolean = true,
        isUpperInclusive: Boolean = false,
        isLowerInfinite: Boolean = (lower == null),
        isUpperInfinite: Boolean = (upper == null),
        isLowerNull: Boolean = false,
        isUpperNull: Boolean = false
    ): PgRange {
        val resolvedOid = registry.resolveOid(typeName, schema, searchPath = searchPathProvider())
        return createRange(
            oid = resolvedOid,
            lower = lower,
            upper = upper,
            isLowerInclusive = isLowerInclusive,
            isUpperInclusive = isUpperInclusive,
            isLowerInfinite = isLowerInfinite,
            isUpperInfinite = isUpperInfinite,
            isLowerNull = isLowerNull,
            isUpperNull = isUpperNull
        )
    }

    /**
     * Creates a new instance of a PostgreSQL range type using its Object ID (OID).
     */
    fun createRange(
        oid: Int,
        lower: Any? = null,
        upper: Any? = null,
        isLowerInclusive: Boolean = true,
        isUpperInclusive: Boolean = false,
        isLowerInfinite: Boolean = (lower == null),
        isUpperInfinite: Boolean = (upper == null),
        isLowerNull: Boolean = false,
        isUpperNull: Boolean = false
    ): PgRange {
        val rangeType = registry.types[oid] as? PgType.Range
            ?: throw OctaviusTypeException(TypeExceptionMessage.NOT_A_CONTAINER, oid = oid, details = "Type is not a range or does not exist in TypeRegistry")
            
        return PgRange.create(
            rangeOid = rangeType.oid,
            elementOid = rangeType.subtypeOid,
            lowerBound = lower,
            upperBound = upper,
            isLowerInclusive = isLowerInclusive,
            isUpperInclusive = isUpperInclusive,
            isLowerInfinite = isLowerInfinite,
            isUpperInfinite = isUpperInfinite,
            isLowerNull = isLowerNull,
            isUpperNull = isUpperNull,
            typeRegistry = registry
        )
    }

    /**
     * Creates an empty PostgreSQL range type using its name and schema.
     */
    fun createEmptyRange(typeName: String, schema: String = ""): PgRange {
        val resolvedOid = registry.resolveOid(typeName, schema, searchPath = searchPathProvider())
        return createEmptyRange(resolvedOid)
    }

    /**
     * Creates an empty PostgreSQL range type using its Object ID (OID).
     */
    fun createEmptyRange(oid: Int): PgRange {
        val rangeType = registry.types[oid] as? PgType.Range
            ?: throw OctaviusTypeException(TypeExceptionMessage.NOT_A_CONTAINER, oid = oid, details = "Type is not a range or does not exist in TypeRegistry")
        return PgRange.empty(rangeType.oid, rangeType.subtypeOid, registry)
    }

    /**
     * Creates a new instance of a PostgreSQL multirange type using its name and schema.
     *
     * @param typeName The name of the multirange type.
     * @param schema The schema where the multirange type is defined.
     * @param ranges The ranges included in the multirange.
     * @return A new [io.github.octaviusframework.driver.container.PgMultirange] instance.
     */
    fun createMultirange(typeName: String, schema: String = "", vararg ranges: PgRange): PgMultirange {
        val resolvedOid = registry.resolveOid(typeName, schema, searchPath = searchPathProvider())
        return createMultirange(resolvedOid, *ranges)
    }

    /**
     * Creates a new instance of a PostgreSQL multirange type using its Object ID (OID).
     *
     * @param oid The OID of the multirange type.
     * @param ranges The ranges included in the multirange.
     * @return A new [PgMultirange] instance.
     */
    fun createMultirange(oid: Int, vararg ranges: PgRange): PgMultirange {
        val multirangeType = registry.types[oid] as? PgType.Multirange
            ?: throw OctaviusTypeException(TypeExceptionMessage.NOT_A_CONTAINER, oid = oid, details = "Type is not a multirange or does not exist in TypeRegistry")
        return PgMultirange(multirangeType.oid, multirangeType.rangeOid, ranges.toList())
    }
}

