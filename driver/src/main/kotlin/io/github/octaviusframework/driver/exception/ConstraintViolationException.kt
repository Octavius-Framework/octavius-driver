package io.github.octaviusframework.driver.exception

import io.github.octaviusframework.driver.message.ServerErrorMessage

/**
 * Represents specific types of database constraint violations that can occur during query execution.
 *
 * This enum categorizes standard constraint violation errors (such as unique, foreign key, or not-null violations)
 * allowing the application to handle specific constraint errors programmatically without parsing SQL error codes manually.
 */
enum class ConstraintViolationExceptionReason {
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

    /**
     * General or unmapped constraint violations (e.g., PostgreSQL 23000, 23001, 40002).
     * 
     * In practice, these generic constraint violation codes rarely occur during standard operations.
     * They are typically encountered only if explicitly raised within a stored procedure, 
     * trigger, or when using specific database extensions.
     */
    UNKNOWN
}

/**
 * Exception thrown when a database operation violates a defined constraint (e.g., unique index, foreign key, check constraint).
 *
 * This exception encapsulates details about the constraint violation, including the specific type of violation 
 * ([reason]), and optional metadata such as the schema, table, column, and constraint name involved in the error.
 *
 * @property reason The specific type of constraint violation mapped from the database error.
 * @property details Additional, human-readable details about the violation provided by the database.
 * @property schema The name of the schema containing the table where the violation occurred, if available.
 * @property table The name of the table where the constraint violation occurred, if available.
 * @property column The name of the column associated with the constraint violation, if available.
 * @property constraint The specific name of the constraint that was violated, if available.
 */
class ConstraintViolationException(
    val reason: ConstraintViolationExceptionReason,
    sqlState: String,
    serverErrorMessage: ServerErrorMessage
) : OctaviusException(
    message = "CONSTRAINT_VIOLATION_EXCEPTION:${reason.name}",
    sqlState = sqlState,
    serverErrorMessage = serverErrorMessage
) {
    val dbMessage: String get() = serverErrorMessage!!.message
    val details: String? get() = serverErrorMessage!!.detail
    val where: String? get() = serverErrorMessage!!.where
    val schema: String? get() = serverErrorMessage!!.schema
    val table: String? get() = serverErrorMessage!!.table
    val column: String? get() = serverErrorMessage!!.column
    val constraint: String? get() = serverErrorMessage?.constraint

    override fun getDetailedMessage(): String = buildString {
        appendLine("Reason: ${generateDeveloperMessage(reason)}")
        if (dbMessage != null) appendLine("Database Message: $dbMessage")
        if (details != null) appendLine("Details: $details")
        if (where != null) appendLine("Context: $where")
        if (schema != null) appendLine("Schema: $schema")
        if (table != null) appendLine("Table: $table")
        if (column != null) appendLine("Column: $column")
        if (constraint != null) appendLine("Constraint: $constraint")
    }
}

private fun generateDeveloperMessage(reason: ConstraintViolationExceptionReason): String =
    when (reason) {
        ConstraintViolationExceptionReason.UNIQUE_CONSTRAINT_VIOLATION -> "A duplicate value was provided for a unique column or index (PostgreSQL 23505)."
        ConstraintViolationExceptionReason.FOREIGN_KEY_VIOLATION -> "A value was provided that does not exist in the referenced table (PostgreSQL 23503)."
        ConstraintViolationExceptionReason.NOT_NULL_VIOLATION -> "A null value was provided for a non-nullable column (PostgreSQL 23502)."
        ConstraintViolationExceptionReason.CHECK_CONSTRAINT_VIOLATION -> "A value was provided that fails a CHECK constraint (PostgreSQL 23514)."
        ConstraintViolationExceptionReason.EXCLUSION_CONSTRAINT_VIOLATION -> "Exclusion constraint violations (PostgreSQL 23P01)."
        ConstraintViolationExceptionReason.UNKNOWN -> "Unknown constraint violation."
    }
