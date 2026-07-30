package io.github.octaviusframework.driver.exception

/**
 * Represents specific reasons for a SQL statement execution failure.
 */
enum class StatementExceptionReason {
    SYNTAX_ERROR,
    UNCLOSED_QUOTE,
    UNCLOSED_DOLLAR_QUOTE,
    UNCLOSED_COMMENT,
    INVALID_DEFINITION,
    UNDEFINED_OBJECT,
    DUPLICATE_OBJECT,
    AMBIGUOUS_OBJECT,
    DATA_TYPE_ERROR,
    INVALID_TRANSACTION_STATE
}

/**
 * Exception thrown when an error occurs during the parsing, planning, or execution of a SQL statement.
 *
 * This exception covers various query-related errors such as syntax errors, undefined objects,
 * ambiguous references, and data type mismatches. If the database provides error positioning, 
 * this exception will format the original SQL query to visually indicate where the error occurred.
 *
 * @property reason The categorized reason for the statement failure.
 * @property details Additional context or hints provided by the database regarding the error.
 * @property position The 1-based character position in the SQL string where the error occurred, if available.
 */
class StatementException(
    val reason: StatementExceptionReason,
    val details: String? = null,
    val position: Int? = null,
    cause: Throwable? = null,
    sqlState: String? = null
) : OctaviusException(reason.name, cause, sqlState) {
    override fun getDetailedMessage(): String = buildString {
        appendLine("message: ${generateDeveloperMessage(reason)}")
        if (details != null) appendLine("Details: $details")
        
        val sqlContext = queryContext?.dbSql ?: queryContext?.sql
        if (sqlContext != null && position != null && position > 0 && position <= sqlContext.length + 1) {
            appendLine("Error at position $position:")
            val beforeError = sqlContext.substring(0, position - 1)
            val lastNewlineIndex = beforeError.lastIndexOf('\n')
            val lineStart = if (lastNewlineIndex == -1) 0 else lastNewlineIndex + 1
            
            val afterError = sqlContext.substring(position - 1)
            val nextNewlineIndex = afterError.indexOf('\n')
            val lineEnd = if (nextNewlineIndex == -1) sqlContext.length else (position - 1) + nextNewlineIndex
            
            val errorLine = sqlContext.substring(lineStart, lineEnd).replace("\r", "")
            val column = (position - 1) - lineStart
            
            appendLine(errorLine)
            appendLine(" ".repeat(maxOf(0, column)) + "^")
        }
    }
}

private fun generateDeveloperMessage(reason: StatementExceptionReason): String =
    when (reason) {
        StatementExceptionReason.SYNTAX_ERROR -> "The SQL statement contains a syntax error."
        StatementExceptionReason.UNCLOSED_QUOTE -> "The SQL statement contains an unclosed string or identifier quote."
        StatementExceptionReason.UNCLOSED_DOLLAR_QUOTE -> "The SQL statement contains an unclosed dollar-quoted string."
        StatementExceptionReason.UNCLOSED_COMMENT -> "The SQL statement contains an unclosed multi-line comment."
        StatementExceptionReason.INVALID_DEFINITION -> "Invalid definition or object state."
        StatementExceptionReason.UNDEFINED_OBJECT -> "The referenced object is undefined."
        StatementExceptionReason.DUPLICATE_OBJECT -> "The referenced object already exists."
        StatementExceptionReason.AMBIGUOUS_OBJECT -> "The referenced object is ambiguous."
        StatementExceptionReason.DATA_TYPE_ERROR -> "Data type error in statement."
        StatementExceptionReason.INVALID_TRANSACTION_STATE -> "Invalid transaction state."
    }
