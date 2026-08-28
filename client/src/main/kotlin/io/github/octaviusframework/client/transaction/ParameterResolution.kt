package io.github.octaviusframework.client.transaction

import io.github.octaviusframework.driver.exception.InvalidOperationException
import io.github.octaviusframework.driver.exception.InvalidOperationExceptionReason
import io.github.octaviusframework.driver.exception.MappingException
import io.github.octaviusframework.driver.exception.MappingExceptionReason
import io.github.octaviusframework.driver.exception.OctaviusException

/**
 * Replaces the unresolved values in a step's parameters with what the earlier steps produced.
 *
 * A [SpreadParameters] contributes its entries under their own names instead of taking the name it was filed
 * under, so the parameter map that comes out can be longer than the one that went in.
 */
internal fun resolveParams(
    raw: Map<String, Any?>,
    results: Map<StepHandle<*>, Any?>
): Map<String, Any?> {
    if (raw.none { it.value is TransactionValue<*> || it.value is SpreadParameters }) return raw

    val resolved = LinkedHashMap<String, Any?>(raw.size)
    for ((name, value) in raw) {
        when (value) {
            is SpreadParameters -> resolved.putAll(spreadOf(value, results, name))
            is TransactionValue<*> -> resolved[name] = resolveOne(value, results, name)
            else -> resolved[name] = value
        }
    }
    return resolved
}

private fun resolveOne(
    value: TransactionValue<*>,
    results: Map<StepHandle<*>, Any?>,
    paramName: String
): Any? = when (value) {
    is TransactionValue.Value -> value.value

    is TransactionValue.FromStep -> resultOf(value.handle, results)

    is TransactionValue.Transformed<*, *> -> {
        val input = resolveOne(value.source, results, paramName)
        @Suppress("UNCHECKED_CAST")
        val transform = value.transform as (Any?) -> Any?
        // The one place a plan runs code the caller wrote, and so the only one that can raise something from
        // outside the driver's hierarchy. Left alone, an IndexOutOfBoundsException out of `map { xs[it] }`
        // would travel as itself - past dbResult and transactionResult, which catch OctaviusException and
        // nothing else, so the result style would not see it at all.
        try {
            transform(input)
        } catch (e: OctaviusException) {
            // Already in the hierarchy and already more specific than anything this could say about it.
            throw e
        } catch (e: Exception) {
            throw MappingException(
                MappingExceptionReason.CONVERSION_ERROR,
                details = "The transformation on parameter '$paramName' threw " +
                    "${e::class.simpleName}: ${e.message}",
                cause = e
            )
        }
    }
}

/** The entries a [SpreadParameters] contributes, which stand in for the name it was filed under. */
@Suppress("UNCHECKED_CAST")
private fun spreadOf(
    spread: SpreadParameters,
    results: Map<StepHandle<*>, Any?>,
    paramName: String
): Map<String, Any?> = when (val resolved = resolveOne(spread.source, results, paramName)) {
    // Checked as far as erasure allows: the keys are String by the type spread() takes.
    is Map<*, *> -> resolved as Map<String, Any?>

    // Out of reach from Kotlin, spread() taking a non-null map: only erasure or a Java caller gets here.
    else -> throw InvalidOperationException(
        InvalidOperationExceptionReason.INVALID_ARGUMENT,
        details = "'$paramName' spreads ${resolved?.let { it::class.simpleName } ?: "null"}, " +
            "which is not a Map."
    )
}

private fun resultOf(handle: StepHandle<*>, results: Map<StepHandle<*>, Any?>): Any? {
    if (!results.containsKey(handle)) {
        throw InvalidOperationException(
            InvalidOperationExceptionReason.INVALID_ARGUMENT,
            details = "$handle is referred to by a step that runs before it, or comes from another plan. " +
                "A step can only use results from steps added before it."
        )
    }
    return results[handle]
}
