package io.github.octaviusframework.driver.mapping

import io.github.octaviusframework.driver.mapping.parameter.ParameterConverter
import io.github.octaviusframework.driver.mapping.parameter.SerializationContext
import io.github.octaviusframework.driver.mapping.result.DeserializationContext
import io.github.octaviusframework.driver.mapping.result.ResultConverter
import io.github.octaviusframework.driver.type.*
import kotlin.reflect.KClass
import kotlin.reflect.KType

class EnumParameterConverter<T : Enum<T>>(
    private val enumClass: KClass<T>,
    private val pgConvention: CaseConvention,
    private val kotlinConvention: CaseConvention
) : ParameterConverter<T> {

    private val enumToPg = enumClass.java.enumConstants.associateWith {
        CaseConverter.convert(it.name, kotlinConvention, pgConvention)
    }

    override fun canConvert(source: Any, expectedOid: UInt?, typeManager: TypeManager): Boolean {
        return source::class == enumClass
    }

    override fun convert(source: Any, expectedOid: UInt?, context: SerializationContext, typeManager: TypeManager): Any? {
        return enumToPg[source]
    }
}

class EnumResultConverter<T : Enum<T>>(
    private val enumClass: KClass<T>,
    private val qualifiedName: QualifiedName,
    private val pgConvention: CaseConvention,
    private val kotlinConvention: CaseConvention
) : ResultConverter<T> {

    private val pgToEnum = enumClass.java.enumConstants.associateBy {
        CaseConverter.convert(it.name, kotlinConvention, pgConvention)
    }

    override fun canConvert(source: Any, expectedType: KType, sourceType: PgType): Boolean {
        return sourceType is PgType.Enum && sourceType.name == qualifiedName.name //TODO TypeRegistry and resolveOid
    }

    override fun convert(source: Any, expectedType: KType, context: DeserializationContext, sourceType: PgType): T {
        val strSource = source.toString()
        return pgToEnum[strSource]
            ?: throw IllegalArgumentException("Unknown enum value: $strSource for enum ${enumClass.simpleName}")
    }
}
