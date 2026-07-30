package io.github.octaviusframework.driver.exception

enum class ConstraintViolationExceptionMessage {
    /** A duplicate value was provided for a unique column or index (PostgreSQL 23505). */
    UNIQUE_CONSTRAINT_VIOLATION,

    /** A value was provided that does not exist in the referenced table (PostgreSQL 23503). */
    FOREIGN_KEY_VIOLATION,

    /** A null value was provided for a non-nullable column (PostgreSQL 23502). */
    NOT_NULL_VIOLATION,

    /** A value was provided that fails a CHECK constraint (PostgreSQL 23514). */
    CHECK_CONSTRAINT_VIOLATION,

    /** Exclusion constraint violations (PostgreSQL 23P01). */
    EXCLUSION_CONSTRAINT_VIOLATION,

    UNKNOWN
}

class ConstraintViolationException(
    val reason: ConstraintViolationExceptionMessage,
    val details: String? = null,
    sqlState: String? = null,
    val schema: String? = null,
    val table: String? = null,
    val column: String? = null,
    val constraint: String? = null
) : OctaviusException(
    message = reason.name,
    sqlState = sqlState
) {
    override fun getDetailedMessage(): String = buildString {
        appendLine("message: ${generateDeveloperMessage(reason)}")
        if (details != null) appendLine("Details: $details")
        if (schema != null) appendLine("Schema: $schema")
        if (table != null) appendLine("Table: $table")
        if (column != null) appendLine("Column: $column")
        if (constraint != null) appendLine("Constraint: $constraint")
    }
}

private fun generateDeveloperMessage(messageEnum: ConstraintViolationExceptionMessage): String =
    when (messageEnum) {
        ConstraintViolationExceptionMessage.UNIQUE_CONSTRAINT_VIOLATION -> "A duplicate value was provided for a unique column or index (PostgreSQL 23505)."
        ConstraintViolationExceptionMessage.FOREIGN_KEY_VIOLATION -> "A value was provided that does not exist in the referenced table (PostgreSQL 23503)."
        ConstraintViolationExceptionMessage.NOT_NULL_VIOLATION -> "A null value was provided for a non-nullable column (PostgreSQL 23502)."
        ConstraintViolationExceptionMessage.CHECK_CONSTRAINT_VIOLATION -> "A value was provided that fails a CHECK constraint (PostgreSQL 23514)."
        ConstraintViolationExceptionMessage.EXCLUSION_CONSTRAINT_VIOLATION -> "Exclusion constraint violations (PostgreSQL 23P01)."
        ConstraintViolationExceptionMessage.UNKNOWN -> "Unknown constraint violation."
    }
