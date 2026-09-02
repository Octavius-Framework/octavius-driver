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
 *
 * Everything that can fail here says which step it was resolving. A plan built in a loop has the same SQL and
 * the same parameter names in every one of its steps, so the parameter alone does not identify anything and
 * the query context on the exception cannot separate the third iteration from the seventeenth.
 *
 * @param raw The step's parameters as they were written.
 * @param results What the steps before this one produced.
 * @param stepIndex Where this step sits in the plan being run.
 * @param stepIndices Where every step sits, for naming the step a value was taken from.
 */
internal fun resolveParams(
    raw: Map<String, Any?>,
    results: Map<StepHandle<*>, Any?>,
    stepIndex: Int,
    stepIndices: Map<StepHandle<*>, Int>
): Map<String, Any?> {
    if (raw.none { it.value is TransactionValue<*> || it.value is SpreadParameters }) return raw

    val resolved = LinkedHashMap<String, Any?>(raw.size)
    for ((name, value) in raw) {
        when (value) {
            is SpreadParameters -> resolved.putAll(spreadOf(value, results, name, stepIndex, stepIndices))
            is TransactionValue<*> -> resolved[name] = resolveOne(value, results, name, stepIndex, stepIndices)
            else -> resolved[name] = value
        }
    }
    return resolved
}

private fun resolveOne(
    value: TransactionValue<*>,
    results: Map<StepHandle<*>, Any?>,
    paramName: String,
    stepIndex: Int,
    stepIndices: Map<StepHandle<*>, Int>
): Any? = when (value) {
    is TransactionValue.Value -> value.value

    is TransactionValue.FromStep -> resultOf(value.handle, results, paramName, stepIndex)

    is TransactionValue.Transformed<*, *> -> {
        val input = resolveOne(value.source, results, paramName, stepIndex, stepIndices)
        @Suppress("UNCHECKED_CAST")
        val transform = value.transform as (Any?) -> Any?
        // The one place a plan runs code the caller wrote, and so the only one that can raise something from
        // outside the driver's hierarchy. Left alone, an IndexOutOfBoundsException out of `map { xs[it] }`
        // would travel as itself - past dbResult and transactionResult, which catch OctaviusException and
        // nothing else, so the result style would not see it at all.
        try {
            transform(input)
        } catch (e: OctaviusException) {
            // Already in the hierarchy and already more specific than anything this could say about it, but
            // carrying a path every layer writes to as it unwinds - the one thing that can be added without
            // replacing the exception. Replacing it would cost the type the caller catches on, so a failure
            // from a query run inside the lambda would stop being the one a retry matches. `row.get` for a
            // column that is not there therefore reports the parameter it was reached from as well as the
            // column, and the exception is still the one that was thrown. The step is added by the executor,
            // which puts it on everything a step raises rather than on this alone.
            e.path.add("map #${value.position}")
            e.path.add("parameter '$paramName'")
            throw e
        } catch (e: Exception) {
            throw MappingException(
                MappingExceptionReason.CONVERSION_ERROR,
                details = "Step $stepIndex of the plan, parameter '$paramName': map #${value.position} over " +
                    "${describeValue(value.source, stepIndices)} threw ${e::class.simpleName}: ${e.message}",
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
    paramName: String,
    stepIndex: Int,
    stepIndices: Map<StepHandle<*>, Int>
): Map<String, Any?> =
    when (val resolved = resolveOne(spread.source, results, paramName, stepIndex, stepIndices)) {
        // Checked as far as erasure allows: the keys are String by the type spread() takes.
        is Map<*, *> -> resolved as Map<String, Any?>

        // Out of reach from Kotlin, spread() taking a non-null map: only erasure or a Java caller gets here.
        else -> throw InvalidOperationException(
            InvalidOperationExceptionReason.INVALID_ARGUMENT,
            details = "Step $stepIndex of the plan spreads ${resolved?.let { it::class.simpleName } ?: "null"} " +
                "under '$paramName', which is not a Map."
        )
    }

private fun resultOf(
    handle: StepHandle<*>,
    results: Map<StepHandle<*>, Any?>,
    paramName: String,
    stepIndex: Int
): Any? {
    if (!results.containsKey(handle)) {
        throw InvalidOperationException(
            InvalidOperationExceptionReason.INVALID_ARGUMENT,
            details = "Step $stepIndex of the plan binds '$paramName' to $handle, which is a step that runs " +
                "after it or comes from another plan. A step can only use results from steps added before it."
        )
    }
    return results[handle]
}
