package io.github.octaviusframework.driver.exception

/**
 * Represents the specific reason for a mapping failure.
 */
enum class MappingExceptionReason {
    /** No suitable converter was found for the target data types. */
    NO_CONVERTER_FOUND,
    /** The specified column name or index does not exist in the row. */
    COLUMN_NOT_FOUND,
    /** The data could not be converted or cast to the target type. */
    CONVERSION_ERROR,
    /** A required, non-nullable property in the target object received a null value. */
    REQUIRED_ATTRIBUTE_MISSING
}

/**
 * Exception thrown when a type conversion or data mapping operation fails.
 *
 * This includes scenarios like failing to convert data between incompatible types (e.g., mapping an Int to a String),
 * missing columns in a database row, or missing required non-nullable properties in a Kotlin class.
 *
 * @property reason The reason the mapping failed.
 * @property details Additional details about the mapping failure.
 * @property path The path in the nested object structure where the mapping error occurred.
 * @param cause The underlying exception that caused this failure, if any.
 */
class MappingException(
    val reason: MappingExceptionReason,
    val details: String,
    cause: Throwable? = null,
    val path: MutableList<String> = mutableListOf()
) : OctaviusException("MAPPING_EXCEPTION:${reason.name}", cause = cause) {
    override fun getDetailedMessage(): String = buildString {
        appendLine("Reason: ${generateDeveloperMessage(reason)}")
        appendLine("Details: $details")
        if (path.isNotEmpty()) {
            appendLine("Path: ${path.asReversed().joinToString(" -> ")}")
        }
    }
}

private fun generateDeveloperMessage(reason: MappingExceptionReason): String =
    when (reason) {
        MappingExceptionReason.NO_CONVERTER_FOUND -> "No converter was found for the specified types."
        MappingExceptionReason.COLUMN_NOT_FOUND -> "The requested column or index was not found in the row metadata."
        MappingExceptionReason.CONVERSION_ERROR -> "An error occurred during type conversion or casting."
        MappingExceptionReason.REQUIRED_ATTRIBUTE_MISSING -> "A required non-nullable attribute is missing or null."
    }

