package io.github.octaviusframework.driver.converter

import io.github.octaviusframework.driver.exception.MappingExceptionMessage
import io.github.octaviusframework.driver.exception.MappingException

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

    override fun canConvert(sourceClass: KClass<*>, expectedOid: Int, context: SerializationContext): Boolean {
        return enumClass.java.isAssignableFrom(sourceClass.java)
    }

    override fun convert(source: T, expectedOid: Int, context: SerializationContext): Any {
        return enumToPg[source]!!
    }

    override fun getDefaultOid(context: SerializationContext): Int {
        return context.typeManager.resolveOid(qualifiedName.name, qualifiedName.schema)
    }
}

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
            ?: throw MappingException(MappingExceptionMessage.CONVERSION_ERROR, "Unknown enum value: $source for enum ${enumClass.simpleName}")
    }
}

