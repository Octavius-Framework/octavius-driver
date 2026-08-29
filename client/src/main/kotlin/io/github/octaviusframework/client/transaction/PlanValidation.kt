package io.github.octaviusframework.client.transaction

import io.github.octaviusframework.driver.exception.InvalidOperationException
import io.github.octaviusframework.driver.exception.InvalidOperationExceptionReason

/**
 * Checks what can be checked about a plan without running any of it, before a transaction is opened.
 *
 * A plan is assembled by one layer and run by another, so it can arrive malformed in ways the caller never
 * saw. Both faults below are properties of the plan itself rather than of the data, which is what makes them
 * worth finding here: run instead, they surface partway through, after the steps before them have already
 * done their work and with a transaction to unwind - and on a plan whose first eighteen steps are slow, after
 * those eighteen.
 *
 * The cost is one extra [RunnableQuery.toSql][io.github.octaviusframework.client.query.RunnableQuery.toSql]
 * per step, since rendering is not cached. Against a transaction's round trips that is nothing, and it buys
 * the whole plan being legible before any of it is committed to.
 *
 * What is deliberately **not** checked is a step depending on a later one. A [StepHandle] comes from
 * [TransactionPlan.add] and from nowhere else, so within the plan that made it a handle always names a step
 * already added - which is to say an earlier one, and a forward reference has no way to be written.
 * [TransactionPlan.addPlan] does not open that up: it appends whole plans in order, which preserves the
 * relative order inside each, and a result is filed under the handle itself rather than under a position, so
 * nothing about the merged sequence depends on where a step now sits. Checking for a forward reference would
 * describe a hazard the design has closed.
 *
 * A handle belonging to a plan that was never merged in is a different fault, and is caught below.
 *
 * The index it builds to answer that is returned rather than discarded, because resolving a parameter needs
 * the same map to say which step a value was taken from - and a handle's own index is the one it was created
 * at, which after [TransactionPlan.addPlan] is not where its step sits in the plan being run.
 *
 * @param steps The plan's steps, in the order they will run.
 * @return Where each step sits, by handle.
 * @throws InvalidOperationException `INVALID_ARGUMENT` where a step's query cannot render, or a step binds a
 * parameter to a handle from another plan.
 */
internal fun validatePlan(steps: List<TransactionPlan.PlannedStep>): Map<StepHandle<*>, Int> {
    val indexByHandle = HashMap<StepHandle<*>, Int>(steps.size)
    steps.forEachIndexed { index, step -> indexByHandle[step.handle] = index }

    steps.forEachIndexed { index, step ->
        try {
            step.query.toSql()
        } catch (e: InvalidOperationException) {
            throw InvalidOperationException(
                InvalidOperationExceptionReason.INVALID_ARGUMENT,
                details = "Step $index of the plan has a query that cannot be rendered: ${e.details}"
            )
        }

        for ((name, value) in step.params) {
            when (value) {
                is TransactionValue<*> -> checkHandles(value, index, name, indexByHandle)
                is SpreadParameters -> checkHandles(value.source, index, name, indexByHandle)
            }
        }
    }

    return indexByHandle
}

/** Walks a value for the handles it depends on, through however many [TransactionValue.Transformed] wrap it. */
private fun checkHandles(
    value: TransactionValue<*>,
    stepIndex: Int,
    paramName: String,
    indexByHandle: Map<StepHandle<*>, Int>
) {
    when (value) {
        is TransactionValue.Value -> Unit
        is TransactionValue.Transformed<*, *> -> checkHandles(value.source, stepIndex, paramName, indexByHandle)
        is TransactionValue.FromStep -> {
            if (!indexByHandle.containsKey(value.handle)) {
                throw InvalidOperationException(
                    InvalidOperationExceptionReason.INVALID_ARGUMENT,
                    details = "Step $stepIndex binds '$paramName' to ${value.handle}, which is not a step of " +
                        "this plan. A handle is useful only inside the plan whose add() returned it."
                )
            }
        }
    }
}
