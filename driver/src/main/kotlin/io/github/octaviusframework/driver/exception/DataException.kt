package io.github.octaviusframework.driver.exception

enum class DataExceptionReason {
    DATA_TRUNCATION,          // 22001, 22008, 22015
    NUMERIC_OUT_OF_RANGE,     // 22003, 22022
    DIVISION_BY_ZERO,         // 22012
    INVALID_FORMAT,           // 22007, 22P02, 22P03, 22018
    ARRAY_SUBSCRIPT_ERROR,    // 2202E
    NULL_VALUE_NOT_ALLOWED,   // 22004, 22002
    JSON_ERROR,               // 2203X
    XML_ERROR,                // 2200L - 2200T
    ESCAPE_CHARACTER_ERROR,   // 22019, 2200D, 22025, 22P06, 2200C, 2200B
    REGEX_ERROR,              // 2201B
    UNKNOWN
}

/**
 * Exception thrown when an operation fails due to invalid data values during execution.
 *
 * Unlike syntax or definition errors, this occurs when the query structure is correct, 
 * but the runtime values (often parameters) cause an error. Examples include string truncation, 
 * numeric overflow, division by zero, or invalid text representation of a data type.
 */
class DataException(
    val reason: DataExceptionReason,
    val details: String? = null,
    cause: Throwable? = null,
    sqlState: String? = null
) : OctaviusException("DATA_EXCEPTION:${reason.name}", cause, sqlState) {
    override fun getDetailedMessage(): String = buildString {
        appendLine("message: ${generateDeveloperMessage(reason)}")
        if (details != null) appendLine("Details: $details")
    }
}

private fun generateDeveloperMessage(reason: DataExceptionReason): String =
    when (reason) {
        DataExceptionReason.DATA_TRUNCATION -> "A string, interval or datetime value was truncated or overflowed."
        DataExceptionReason.NUMERIC_OUT_OF_RANGE -> "A numeric value is out of bounds for its target data type."
        DataExceptionReason.DIVISION_BY_ZERO -> "Attempted to divide by zero."
        DataExceptionReason.INVALID_FORMAT -> "The provided data has an invalid format or text/binary representation for the target type."
        DataExceptionReason.ARRAY_SUBSCRIPT_ERROR -> "Array subscript out of bounds or invalid array dimensions."
        DataExceptionReason.NULL_VALUE_NOT_ALLOWED -> "A null value was provided where it is not allowed by a data constraint."
        DataExceptionReason.JSON_ERROR -> "An error occurred while parsing or operating on JSON data."
        DataExceptionReason.XML_ERROR -> "An error occurred while parsing or operating on XML data."
        DataExceptionReason.ESCAPE_CHARACTER_ERROR -> "Invalid escape character or escape sequence."
        DataExceptionReason.REGEX_ERROR -> "Invalid regular expression."
        DataExceptionReason.UNKNOWN -> "A generic data error occurred."
    }
