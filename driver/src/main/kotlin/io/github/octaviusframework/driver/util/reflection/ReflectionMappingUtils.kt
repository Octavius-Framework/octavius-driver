package io.github.octaviusframework.driver.util.reflection

import io.github.octaviusframework.driver.exception.MappingException
import io.github.octaviusframework.driver.exception.MappingExceptionReason
import kotlin.reflect.KClass
import kotlin.reflect.KParameter
import kotlin.reflect.KType

/**
 * Token used to represent a missing value during instantiation,
 * avoiding the need for allocating result wrappers like Result or Optional.
 */
object MissingToken

/**
 * Instantiates a data class based on its metadata.
 */
fun <T : Any> instantiateDataObject(
    kClass: KClass<T>,
    metadata: DataObjectClassMetadata<T>,
    resolveValue: (ConstructorParamMetadata<T>) -> Any?
): T {
    val constructorArgs = mutableMapOf<KParameter, Any?>()

    for (meta in metadata.constructorProperties) {
        val param = meta.parameter

        when (val result = resolveValue(meta)) {
            // Nothing in the result set under this name: the default is what covers it.
            MissingToken -> {
                if (!param.isOptional) {
                    if (!meta.type.isMarkedNullable) {
                        throw MappingException(
                            MappingExceptionReason.REQUIRED_ATTRIBUTE_MISSING,
                            "Missing non-nullable attribute '${meta.keyName}' for $kClass",
                            path = mutableListOf(meta.keyName)
                        )
                    }
                    constructorArgs[param] = null
                }
            }
            // Present and NULL: a value
            null -> {
                if (!meta.type.isMarkedNullable) {
                    throw MappingException(
                        MappingExceptionReason.REQUIRED_ATTRIBUTE_MISSING,
                        "Null value for non-nullable attribute '${meta.keyName}' for $kClass",
                        path = mutableListOf(meta.keyName)
                    )
                }
                constructorArgs[param] = null
            }
            else -> constructorArgs[param] = result
        }
    }

    return metadata.constructor.callBy(constructorArgs)
}

/**
 * Converts a map to a data class instance.
 * Keys in the map will be matched to data class properties according to the configured case conventions.
 */
inline fun <reified T : Any> Map<String, Any?>.toDataObject(): T {
    return toDataObject(T::class)
}

/**
 * Converts a map to a data class instance.
 * Keys in the map will be matched to data class properties according to the configured case conventions.
 */
fun <T : Any> Map<String, Any?>.toDataObject(
    kClass: KClass<T>
): T {
    val metadata = ReflectionCache.getOrCreateDataObjectMetadata(kClass)
    
    return instantiateDataObject(kClass, metadata) { meta ->
        if (this.containsKey(meta.keyName)) {
            val value = this[meta.keyName]
            try {
                validateValue(value, meta.type)
            } catch (e: MappingException) {
                e.path.add(meta.keyName)
                throw e
            }
        } else {
            MissingToken
        }
    }
}

/**
 * Converts a data class object to a map, where keys are mapped property names
 * and values are the property values.
 *
 * @param excludeKeys Keys to exclude from the resulting map.
 */
fun <T : Any> T.toDataMap(
    vararg excludeKeys: String
): Map<String, Any?> {
    @Suppress("UNCHECKED_CAST")
    val kClass = this::class as KClass<T>
    val metadata = ReflectionCache.getOrCreateDataObjectMetadata(kClass)
    
    val exclusionSet = if (excludeKeys.isNotEmpty()) excludeKeys.toSet() else emptySet()

    return metadata.constructorProperties.mapNotNull { meta ->
        val keyName = meta.keyName
        if (keyName in exclusionSet) {
            return@mapNotNull null
        }
        val value = meta.property.get(this)
        
        keyName to value
    }.associate { it }
}

/**
 * Validates whether a runtime value matches the expected Kotlin type.
 *
 * This is an internal framework function used during object mapping to ensure type safety.
 * For collections (List, Map), validates the type of the first non-null element.
 *
 * @param value The value to validate (can be null).
 * @param targetType The expected Kotlin type (KType) including generic parameters.
 * @return The original value if validation passes.
 * @throws MappingException if the value's type doesn't match the target type.
 */
fun validateValue(value: Any?, targetType: KType): Any? {
    if (value == null) {
        if (targetType.isMarkedNullable) {
            return null
        } else {
            throw MappingException(
                reason = MappingExceptionReason.REQUIRED_ATTRIBUTE_MISSING,
                details = "Null value for non-nullable expected type $targetType"
            )
        }
    }

    val classifier = targetType.classifier
    if (classifier is kotlin.reflect.KTypeParameter) {
        throw MappingException(
            reason = MappingExceptionReason.CONVERSION_ERROR,
            details = "Unsupported generic type parameter in data class: $targetType"
        )
    }

    val targetClass = classifier as? KClass<*> ?: return value

    // --- Validation 1: Check main type ---
    if (!targetClass.isInstance(value)) {
        throw MappingException(
            reason = MappingExceptionReason.CONVERSION_ERROR,
            details = "Incompatible type. Expected $targetType but got ${value::class}"
        )
    }

    // --- Validation 2: Check element type in collection ---
    when (value) {
        is List<*> -> validateList(value, targetType)
        is Map<*, *> -> validateMap(value, targetType)
    }

    return value
}

private fun validateList(value: List<*>, targetType: KType) {
    val firstNonNullElement = value.firstOrNull { it != null }

    if (firstNonNullElement != null) {
        val listElementType = targetType.arguments.firstOrNull()?.type ?: return
        val listElementClass = listElementType.classifier as? KClass<*> ?: return

        if (!listElementClass.isInstance(firstNonNullElement)) {
            throw MappingException(
                reason = MappingExceptionReason.CONVERSION_ERROR,
                details = "Incompatible collection element type. Expected $listElementType but got ${firstNonNullElement::class}"
            )
        }
    }
}

private fun validateMap(value: Map<*, *>, targetType: KType) {
    val firstNonNullEntry = value.entries.firstOrNull { it.key != null && it.value != null }

    if (firstNonNullEntry != null && targetType.arguments.size == 2) {
        val keyType = targetType.arguments[0].type ?: return
        val valueType = targetType.arguments[1].type ?: return

        val keyClass = keyType.classifier as? KClass<*> ?: return
        val valueClass = valueType.classifier as? KClass<*> ?: return

        if (!keyClass.isInstance(firstNonNullEntry.key) || !valueClass.isInstance(firstNonNullEntry.value)) {
            throw MappingException(
                reason = MappingExceptionReason.CONVERSION_ERROR,
                details = "Incompatible map entry types for map $targetType. Key: ${firstNonNullEntry.key!!::class}, Value: ${firstNonNullEntry.value!!::class}"
            )
        }
    }
}
