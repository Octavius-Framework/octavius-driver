package io.github.octaviusframework.driver.exception

enum class MappingExceptionMessage {
    NO_CONVERTER_FOUND,
    COLUMN_NOT_FOUND,
    CONVERSION_ERROR,
    REQUIRED_ATTRIBUTE_MISSING
}

class MappingException(
    val messageEnum: MappingExceptionMessage,
    val details: String? = null,
    cause: Throwable? = null,
    sqlState: String? = null,
    val path: MutableList<String> = mutableListOf()
) : OctaviusException("MAPPING_EXCEPTION:${messageEnum.name}", cause, sqlState) {
    override fun getDetailedMessage(): String = buildString {
        appendLine("message: ${generateDeveloperMessage(messageEnum)}")
        if (details != null) appendLine("Details: $details")
        if (path.isNotEmpty()) {
            appendLine("Path: ${path.asReversed().joinToString(" -> ")}")
        }
    }
}

private fun generateDeveloperMessage(messageEnum: MappingExceptionMessage): String =
    when (messageEnum) {
        MappingExceptionMessage.NO_CONVERTER_FOUND -> "No converter was found for the specified types."
        MappingExceptionMessage.COLUMN_NOT_FOUND -> "The requested column or index was not found in the row metadata."
        MappingExceptionMessage.CONVERSION_ERROR -> "An error occurred during type conversion or casting."
        MappingExceptionMessage.REQUIRED_ATTRIBUTE_MISSING -> "A required non-nullable attribute is missing or null."
    }

