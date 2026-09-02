package io.github.octaviusframework.driver.exception

/**
 * Represents the specific reason for a mapping failure.
 */
enum class MappingExceptionReason {
    /** No suitable converter was found for the target data types. */
    NO_CONVERTER_FOUND,
    /**
     * The requested column, composite attribute, record field or array element does not exist - by that
     * name, or at that position.
     */
    COLUMN_NOT_FOUND,
    /** The data could not be converted or cast to the target type. */
    CONVERSION_ERROR,
    /** A required, non-nullable property in the target object received a null value. */
    REQUIRED_ATTRIBUTE_MISSING,
    /**
     * The block handed to a streaming terminal threw something of its own.
     *
     * It is wrapped rather than let through because the result has to be drained before anything can be
     * rethrown, and by then the frame that knows the query is gone - so this carries the `QueryContext` that
     * a bare rethrow would lose. What the block actually threw is the `cause`.
     */
    BLOCK_FAILED
}

/**
 * Exception thrown when a type conversion or data mapping operation fails.
 *
 * This includes scenarios like failing to convert data between incompatible types (e.g., mapping an Int to a String),
 * missing columns in a database row, or missing required non-nullable properties in a Kotlin class.
 *
 * @property reason The reason the mapping failed.
 * @property details Additional details about the mapping failure.
 * @param cause The underlying exception that caused this failure, if any.
 * @param path Where in the nested object structure the mapping error occurred, for a frame that knows it
 * already. Kept on [OctaviusException], which every layer that has something to add to it writes to; this is
 * only the first segment, for the mapper that raises the exception at the depth it found the fault.
 */
class MappingException(
    val reason: MappingExceptionReason,
    val details: String,
    cause: Throwable? = null,
    path: MutableList<String> = mutableListOf()
) : OctaviusException("MAPPING_EXCEPTION:${reason.name}", cause = cause, path = path) {
    override fun getDetailedMessage(): String = buildString {
        appendLine("Reason: ${generateDeveloperMessage(reason)}")
        appendLine("Details: $details")
    }
}

private fun generateDeveloperMessage(reason: MappingExceptionReason): String =
    when (reason) {
        MappingExceptionReason.NO_CONVERTER_FOUND -> "No converter was found for the specified types."
        MappingExceptionReason.COLUMN_NOT_FOUND -> "The requested column, attribute, field or element was not found under that name or position."
        MappingExceptionReason.CONVERSION_ERROR -> "An error occurred during type conversion or casting."
        MappingExceptionReason.REQUIRED_ATTRIBUTE_MISSING -> "A required non-nullable attribute is missing or null."
        MappingExceptionReason.BLOCK_FAILED -> "The block handed to a streaming terminal threw; see the cause."
    }

