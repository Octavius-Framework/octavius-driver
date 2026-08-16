package io.github.octaviusframework.driver.converter



import io.github.octaviusframework.driver.converter.parameter.mapper.ParameterConverter
import io.github.octaviusframework.driver.converter.parameter.mapper.SerializationContext
import io.github.octaviusframework.driver.converter.result.mapper.DeserializationContext
import io.github.octaviusframework.driver.converter.result.mapper.ResultConverter
import io.github.octaviusframework.driver.identifier.CaseConvention
import io.github.octaviusframework.driver.identifier.CaseConverter
import io.github.octaviusframework.driver.identifier.QualifiedName
import io.github.octaviusframework.driver.type.PgType
import kotlin.reflect.KClass
import kotlin.reflect.KType

/**
 * Converts a Kotlin Enum instance to its PostgreSQL String representation during query parameter serialization.
 *
 * The two conventions describe the same constant on either side of the wire, and the mapping is applied
 * name by name: a constant is read under [kotlinConvention] and written under [pgConvention].
 * Registering through [registerEnum][io.github.octaviusframework.driver.registry.TypeManager.registerEnum]
 * supplies [CaseConvention.SNAKE_CASE_UPPER] and [CaseConvention.PASCAL_CASE] respectively.
 *
 * @param T The type of the Kotlin enum.
 * @property enumClass The Kotlin KClass of the enum.
 * @property qualifiedName The qualified name of the corresponding PostgreSQL ENUM type in the database.
 * @property pgConvention The naming convention the labels are declared with in PostgreSQL.
 * @property kotlinConvention The naming convention the constants are declared with in Kotlin.
 */
class EnumParameterConverter<T : Enum<T>>(
    private val enumClass: KClass<T>,
    private val qualifiedName: QualifiedName,
    private val pgConvention: CaseConvention,
    private val kotlinConvention: CaseConvention
) : ParameterConverter<T> {

    private val enumToPg = enumClass.java.enumConstants.associateWith {
        CaseConverter.convert(it.name, kotlinConvention, pgConvention)
    }

    override val supportedClass: KClass<T> = enumClass

    override fun convert(source: T, expectedOid: Int, context: SerializationContext): Any {
        return enumToPg[source]!!
    }

    override fun getDefaultTypeName(sourceClass: KClass<*>, context: SerializationContext): QualifiedName {
        return qualifiedName
    }
}

/**
 * Converts a PostgreSQL String representation back into a Kotlin Enum instance during result set deserialization.
 *
 * Claims a value only when the column's own type is the PostgreSQL enum named by [qualifiedName], so an
 * unrelated `text` column carrying the same label is left to another converter.
 *
 * @param T The type of the Kotlin enum.
 * @property enumClass The Kotlin KClass of the enum.
 * @property qualifiedName The qualified name of the corresponding PostgreSQL ENUM type in the database.
 * @property pgConvention The naming convention the labels are declared with in PostgreSQL.
 * @property kotlinConvention The naming convention the constants are declared with in Kotlin.
 */
class EnumResultConverter<T : Enum<T>>(
    private val enumClass: KClass<T>,
    private val qualifiedName: QualifiedName,
    private val pgConvention: CaseConvention,
    private val kotlinConvention: CaseConvention
) : ResultConverter<String, T> {

    private val pgToEnum = enumClass.java.enumConstants.associateBy {
        CaseConverter.convert(it.name, kotlinConvention, pgConvention)
    }

    override val supportedSourceClass = String::class

    override fun canConvert(sourceClass: KClass<*>, expectedType: KType, sourceType: PgType, context: DeserializationContext): Boolean {
        if (sourceType !is PgType.Enum) return false
        val expectedClass = expectedType.classifier as? KClass<*> ?: return false
        if (expectedClass != enumClass && expectedClass != Any::class) return false

         return sourceType.name == qualifiedName.name && (qualifiedName.schema.isEmpty() || sourceType.schema == qualifiedName.schema)
    }

    override fun convert(source: String, expectedType: KType, sourceType: PgType, context: DeserializationContext): T {
        return pgToEnum[source]
            ?: throw IllegalArgumentException("Unknown enum value: $source for enum ${enumClass.simpleName}")
    }
}

