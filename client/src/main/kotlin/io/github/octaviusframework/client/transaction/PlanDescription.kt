package io.github.octaviusframework.client.transaction

import io.github.octaviusframework.client.query.RunnableQuery
import io.github.octaviusframework.driver.exception.InvalidOperationException

/**
 * Renders a value as the chain that will produce it.
 *
 * Used by a plan's description, by a failure that has to say which of several `map`s on one parameter threw,
 * and by [TransactionValue.Transformed.toString].
 *
 * A handle is rendered as its position in [stepIndices] wherever that is known, because a handle's own
 * `toString` carries the index it was *created* at - which after [TransactionPlan.addPlan] is no longer where
 * that step sits in the plan being run. Where it is not known the handle speaks for itself, and naming the
 * plan it was created in is exactly what is wanted at that spot.
 *
 * What a literal *is* is deliberately not rendered. The wiring is the part that is invisible in the code that
 * assembled the plan; a bound value is not, and putting one here would put a `bytea` parameter or a column of
 * personal data into whatever the description was written to.
 *
 * @param value The value to render.
 * @param stepIndices Where each handle's step sits in the plan in hand, where there is one.
 * @return The chain, source first.
 */
internal fun describeValue(
    value: TransactionValue<*>,
    stepIndices: Map<StepHandle<*>, Int>? = null
): String = when (value) {
    is TransactionValue.Value -> "literal"

    is TransactionValue.FromStep ->
        stepIndices?.get(value.handle)?.let { "step $it" } ?: value.handle.toString()

    is TransactionValue.Transformed<*, *> ->
        "${describeValue(value.source, stepIndices)}.map(#${value.position})"
}

/** As [describeValue], for the one thing that goes among a step's parameters without being one. */
private fun describeParameter(value: Any?, stepIndices: Map<StepHandle<*>, Int>): String = when (value) {
    is SpreadParameters -> "spread of ${describeValue(value.source, stepIndices)}, this name dropped"
    is TransactionValue<*> -> describeValue(value, stepIndices)
    else -> "literal"
}

/**
 * The whole of what a plan will do, as text.
 *
 * A plan is assembled by one layer and run by another, so the code holding it at the moment it fails is
 * usually not the code that decided what went in. This is what that code can print.
 *
 * @param entries The plan's steps, in the order they will run.
 * @return One block per step: its index, its SQL, and where each parameter comes from.
 */
internal fun describePlan(entries: List<TransactionPlan.PlannedStep>): String {
    if (entries.isEmpty()) return "TransactionPlan, no steps"

    val stepIndices = HashMap<StepHandle<*>, Int>(entries.size)
    entries.forEachIndexed { index, step -> stepIndices[step.handle] = index }

    return buildString {
        appendLine("TransactionPlan, ${entries.size} ${if (entries.size == 1) "step" else "steps"}")

        entries.forEachIndexed { index, step ->
            appendLine()
            appendLine("step $index")
            appendLine(renderSql(step.query).prependIndent("  "))

            // Padded to the longest name so the arrows line up and the sources read as a column.
            val width = step.params.keys.maxOfOrNull { it.length } ?: 0
            for ((name, value) in step.params) {
                appendLine("  @${name.padEnd(width)} <- ${describeParameter(value, stepIndices)}")
            }
        }
    }.trimEnd()
}

/**
 * A step's SQL, or why there is none.
 *
 * A description is reached for when something is already wrong, and a plan holding a query that cannot render
 * is one of the things that can be wrong with it - so this says so in place of that step's SQL rather than
 * throwing, and the other nineteen steps still get described.
 */
private fun renderSql(query: RunnableQuery<*>): String =
    try {
        query.toSql().trim()
    } catch (e: InvalidOperationException) {
        "<this query cannot be rendered: ${e.details}>"
    }
