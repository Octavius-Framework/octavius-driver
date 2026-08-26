package io.github.octaviusframework.client.transaction

import io.github.octaviusframework.driver.exception.InvalidOperationException
import io.github.octaviusframework.driver.exception.InvalidOperationExceptionReason
import io.github.octaviusframework.driver.exception.MappingException
import io.github.octaviusframework.driver.exception.MappingExceptionReason
import io.github.octaviusframework.driver.exception.OctaviusException
import io.github.octaviusframework.driver.row.Row
import kotlin.reflect.typeOf

/**
 * Replaces the unresolved values in a step's parameters with what the earlier steps produced.
 *
 * A [TransactionValue.FromStep.Row] is spread rather than assigned: its entries become parameters under their
 * own column names and the name it was filed under disappears, which is what lets a whole row be carried into
 * the next step without naming every column.
 */
internal fun resolveParams(
    raw: Map<String, Any?>,
    results: Map<StepHandle<*>, Any?>
): Map<String, Any?> {
    if (raw.none { it.value is TransactionValue<*> }) return raw

    val resolved = LinkedHashMap<String, Any?>(raw.size)
    for ((name, value) in raw) {
        if (value !is TransactionValue<*>) {
            resolved[name] = value
            continue
        }
        val actual = resolveOne(value, results, name)
        if (value is TransactionValue.FromStep.Row) {
            @Suppress("UNCHECKED_CAST")
            resolved.putAll(actual as Map<String, Any?>)
        } else {
            resolved[name] = actual
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

    is TransactionValue.Transformed<*, *> -> {
        val input = resolveOne(value.source, results, paramName)
        @Suppress("UNCHECKED_CAST")
        val transform = value.transform as (Any?) -> Any?
        // The one place a plan runs code the caller wrote, and the only one that can raise something from
        // outside the driver's hierarchy. Left alone, a ClassCastException out of `map { it as Int }` would
        // travel as itself - past dbResult and transactionResult, which catch OctaviusException and nothing
        // else, so the result style would not see it at all. It arrives as a MappingException instead, naming
        // the parameter, with what was actually thrown as the cause.
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

    is TransactionValue.FromStep.Whole -> resultOf(value.handle, results)

    is TransactionValue.FromStep.Field ->
        rowAt(resultOf(value.handle, results), value.rowIndex, value.handle)
            .let { it.mapped(columnIndex(it, value.columnName, value.handle)) }

    is TransactionValue.FromStep.Column -> {
        val rows = rowsOf(resultOf(value.handle, results), value.handle)
        rows.map { it.mapped(columnIndex(it, value.columnName, value.handle)) }
    }

    is TransactionValue.FromStep.Row ->
        rowAt(resultOf(value.handle, results), value.rowIndex, value.handle).asMap()
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

/** The rows of a step's result, for a handle used as though the step were row-shaped. */
private fun rowsOf(result: Any?, handle: StepHandle<*>): List<Row> = when (result) {
    is Row -> listOf(result)
    is List<*> -> result.map {
        it as? Row ?: throw notRowShaped(handle, result)
    }
    else -> throw notRowShaped(handle, result)
}

private fun rowAt(result: Any?, rowIndex: Int, handle: StepHandle<*>): Row {
    val rows = rowsOf(result, handle)
    return rows.getOrNull(rowIndex) ?: throw InvalidOperationException(
        InvalidOperationExceptionReason.INVALID_ARGUMENT,
        details = "$handle produced ${rows.size} row(s); row $rowIndex was asked for."
    )
}

private fun columnIndex(row: Row, columnName: String, handle: StepHandle<*>): Int {
    val index = row.columnNames.indexOf(columnName)
    if (index < 0) {
        throw InvalidOperationException(
            InvalidOperationExceptionReason.INVALID_ARGUMENT,
            details = "$handle produced rows with columns ${row.columnNames}; '$columnName' is not among them."
        )
    }
    return index
}

private fun notRowShaped(handle: StepHandle<*>, result: Any?) = InvalidOperationException(
    InvalidOperationExceptionReason.INVALID_ARGUMENT,
    details = "$handle produced ${result?.let { it::class.simpleName } ?: "null"}, which has no columns to " +
        "take. Use value() for the result of a typed terminal, or give the step a fetchRow* terminal."
)

/** The one target type asked for below, hoisted because `typeOf` is not free at every column of every row. */
private val ANY_TYPE = typeOf<Any?>()

private fun Row.mapped(index: Int): Any? = get(index, ANY_TYPE)

/** A row as a map of column name to value, which is what a spread row becomes. */
private fun Row.asMap(): Map<String, Any?> {
    val names = columnNames
    val map = LinkedHashMap<String, Any?>(names.size)
    for (index in names.indices) map[names[index]] = mapped(index)
    return map
}
