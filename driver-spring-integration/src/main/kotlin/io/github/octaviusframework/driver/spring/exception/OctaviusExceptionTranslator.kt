package io.github.octaviusframework.driver.spring.exception

import io.github.octaviusframework.driver.exception.findOctaviusCause
import org.springframework.dao.DataAccessException
import org.springframework.jdbc.support.SQLExceptionTranslator
import org.springframework.jdbc.support.SQLStateSQLExceptionTranslator
import java.sql.SQLException

/**
 * Translates Octavius-specific SQLException into Spring's DataAccessException hierarchy.
 */
class OctaviusExceptionTranslator : SQLExceptionTranslator {

    private val fallbackTranslator = SQLStateSQLExceptionTranslator()

    /**
     * Translates the given [SQLException] into a generic [DataAccessException].
     *
     * The cause chain is searched by [findOctaviusCause], which finds both shapes a driver failure
     * reaches Spring in: wrapped by the JDBC surface, or bare, the way a pool records one it could
     * not open a connection through. Either yields an [OctaviusDataAccessException] carrying the
     * original, so a driver failure keeps its type instead of collapsing into a generic one.
     *
     * If nothing Octavius-shaped is found, it falls back to the default [SQLStateSQLExceptionTranslator].
     *
     * @param task readable text describing the task being attempted
     * @param sql the SQL query or update that caused the problem (may be null)
     * @param ex the offending SQLException
     * @return the translated DataAccessException, or null if it could not be translated
     */
    override fun translate(task: String, sql: String?, ex: SQLException): DataAccessException? {
        ex.findOctaviusCause()?.let { return OctaviusDataAccessException(it) }

        return fallbackTranslator.translate(task, sql, ex)
    }
}
