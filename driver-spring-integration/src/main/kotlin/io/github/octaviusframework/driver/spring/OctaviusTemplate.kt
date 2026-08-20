package io.github.octaviusframework.driver.spring

import io.github.octaviusframework.driver.exception.findOctaviusCause
import io.github.octaviusframework.driver.jdbc.getOctaviusSession
import io.github.octaviusframework.driver.session.OctaviusSession
import io.github.octaviusframework.driver.session.OctaviusSessionOperations
import io.github.octaviusframework.driver.spring.exception.OctaviusDataAccessException
import io.github.octaviusframework.driver.spring.exception.OctaviusExceptionTranslator
import org.springframework.dao.DataAccessException
import org.springframework.jdbc.UncategorizedSQLException
import org.springframework.jdbc.datasource.DataSourceUtils
import org.springframework.jdbc.support.SQLExceptionTranslator
import java.sql.SQLException
import javax.sql.DataSource

/**
 * Template class that simplifies executing Octavius operations and provides proper integration
 * with Spring's transaction management and exception translation mechanism.
 *
 * @property dataSource the data source used to obtain connections
 * @property exceptionTranslator the translator used to convert SQLExceptions into Spring's DataAccessException hierarchy
 */
class OctaviusTemplate(private val dataSource: DataSource, val exceptionTranslator: SQLExceptionTranslator = OctaviusExceptionTranslator()) {

    /**
     * Executes the given action within an [OctaviusSession], translating any exceptions thrown.
     * The session is passed as the receiver of [action], so its operations are available directly.
     * Connection management and transaction synchronization are handled automatically.
     *
     * @param action the action to execute, with the session as its receiver
     * @return the result of the action
     * @throws org.springframework.dao.DataAccessException if a database access error occurs or an exception is translated
     */
    fun <T> execute(action: OctaviusSessionOperations.() -> T): T {
        // Acquisition is translated too, so a pool timeout or a refused connection still arrives
        // as an OctaviusDataAccessException rather than a raw SQLException.
        val con = try {
            DataSourceUtils.doGetConnection(dataSource)
        } catch (ex: SQLException) {
            throw translate("OctaviusTemplate connection acquisition", ex)
        }

        try {
            val session = con.getOctaviusSession()
            return session.action()
        } catch (ex: SQLException) {
            throw translate("OctaviusTemplate execution", ex)
        } catch (ex: RuntimeException) {
            // Covers the driver's own exceptions, which are runtime exceptions, and anything that
            // wrapped one on its way here; anything else is left as it is.
            throw ex.findOctaviusCause()?.let { OctaviusDataAccessException(it) } ?: ex
        } finally {
            DataSourceUtils.releaseConnection(con, dataSource)
        }
    }

    /**
     * Runs [ex] through the configured [exceptionTranslator], falling back to [UncategorizedSQLException]
     * when it declines to translate.
     *
     * @param task readable text describing the task being attempted
     * @param ex the offending SQLException
     * @return the translated exception, ready to be thrown
     */
    private fun translate(task: String, ex: SQLException): DataAccessException =
        exceptionTranslator.translate(task, null, ex) ?: UncategorizedSQLException(task, null, ex)
}
