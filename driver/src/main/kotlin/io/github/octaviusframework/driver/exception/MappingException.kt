package io.github.octaviusframework.driver.exception

enum class MappingExceptionReason {
    NO_CONVERTER_FOUND,
    COLUMN_NOT_FOUND,
    CONVERSION_ERROR,
    REQUIRED_ATTRIBUTE_MISSING
}

class MappingException(
    val reason: MappingExceptionReason,
    val details: String? = null,
    cause: Throwable? = null,
    sqlState: String? = null,
    val path: MutableList<String> = mutableListOf()
) : OctaviusException("MAPPING_EXCEPTION:${reason.name}", cause, sqlState) {
    override fun getDetailedMessage(): String = buildString {
        appendLine("Reason: ${generateDeveloperMessage(reason)}")
        if (details != null) appendLine("Details: $details")
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

