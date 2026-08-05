package io.github.octaviusframework.driver.exception

enum class MappingExceptionMessage {
    UNKNOWN_ENUM_VALUE,
    NULL_FOR_NON_NULLABLE_ATTRIBUTE,
    MISSING_ATTRIBUTE,
    NO_CONVERTER_FOUND,
    INVALID_RECORD_FORMAT,
    COLUMN_NOT_FOUND,
    COLUMN_INDEX_OUT_OF_BOUNDS,
    CONVERTER_ERROR,
    CASTING_ERROR
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
        MappingExceptionMessage.UNKNOWN_ENUM_VALUE -> "The specified enum value could not be found."
        MappingExceptionMessage.NULL_FOR_NON_NULLABLE_ATTRIBUTE -> "Attempted to map a null database value to a non-nullable Kotlin property."
        MappingExceptionMessage.MISSING_ATTRIBUTE -> "A required non-nullable attribute is missing from the database result."
        MappingExceptionMessage.NO_CONVERTER_FOUND -> "No converter was found for the specified types."
        MappingExceptionMessage.INVALID_RECORD_FORMAT -> "The record data is in an invalid format."
        MappingExceptionMessage.COLUMN_NOT_FOUND -> "The requested column was not found in the row metadata."
        MappingExceptionMessage.COLUMN_INDEX_OUT_OF_BOUNDS -> "The requested column index is out of bounds for the row."
        MappingExceptionMessage.CONVERTER_ERROR -> "An exception was thrown by a user-provided converter or mapper."
        MappingExceptionMessage.CASTING_ERROR -> "Type casting error when converting database value to Kotlin type."
    }

