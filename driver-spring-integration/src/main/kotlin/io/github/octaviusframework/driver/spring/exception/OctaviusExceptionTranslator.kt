package io.github.octaviusframework.driver.spring.exception

import io.github.octaviusframework.driver.exception.OctaviusException
import io.github.octaviusframework.driver.exception.SQLExceptionWrapper
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
     * The cause chain is searched for anything Octavius-shaped - a [SQLExceptionWrapper] thrown by the
     * JDBC surface, or a bare [OctaviusException] such as the [io.github.octaviusframework.driver.exception.InitializationException]
     * a pool reports when it cannot open a connection. Either one yields an [OctaviusDataAccessException]
     * carrying the original, so a driver failure keeps its type instead of collapsing into a generic one.
     *
     * If nothing Octavius-shaped is found, it falls back to the default [SQLStateSQLExceptionTranslator].
     *
     * @param task readable text describing the task being attempted
     * @param sql the SQL query or update that caused the problem (may be null)
     * @param ex the offending SQLException
     * @return the translated DataAccessException, or null if it could not be translated
     */
    override fun translate(task: String, sql: String?, ex: SQLException): DataAccessException? {
        if (ex is SQLExceptionWrapper) {
            return OctaviusDataAccessException(ex.wrappedException)
        }

        var cause: Throwable? = ex.cause
        while (cause != null) {
            when (cause) {
                is SQLExceptionWrapper -> return OctaviusDataAccessException(cause.wrappedException)
                is OctaviusException -> return OctaviusDataAccessException(cause)
            }
            cause = cause.cause
        }

        return fallbackTranslator.translate(task, sql, ex)
    }
}
