package io.github.octaviusframework.spring

import io.github.octaviusframework.driver.exception.OctaviusException
import io.github.octaviusframework.driver.exception.SQLExceptionWrapper
import io.github.octaviusframework.driver.jdbc.getOctaviusSession
import io.github.octaviusframework.driver.session.OctaviusSession
import io.github.octaviusframework.spring.exception.OctaviusDataAccessException
import io.github.octaviusframework.spring.exception.OctaviusExceptionTranslator
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
     * Connection management and transaction synchronization are handled automatically.
     *
     * @param action the action to execute
     * @return the result of the action
     * @throws org.springframework.dao.DataAccessException if a database access error occurs or an exception is translated
     */
    fun <T> execute(action: (OctaviusSession) -> T): T {
        val con = DataSourceUtils.doGetConnection(dataSource)
        try {
            val session = con.getOctaviusSession()
            return action(session)
        } catch (ex: SQLException) {
            val translated = exceptionTranslator.translate("OctaviusTemplate execution", null, ex)
            if (translated != null) {
                throw translated
            }
            throw OctaviusDataAccessException("Uncategorized SQL Exception during OctaviusTemplate execution", cause = ex)
        } catch (ex: OctaviusException) {
            throw OctaviusDataAccessException(ex)
        } catch (ex: RuntimeException) {
            var current: Throwable? = ex.cause
            while (current != null) {
                if (current is SQLExceptionWrapper) {
                    throw OctaviusDataAccessException(current.wrappedException)
                } else if (current is OctaviusException) {
                    throw OctaviusDataAccessException(current)
                }
                current = current.cause
            }
            throw ex
        } finally {
            DataSourceUtils.releaseConnection(con, dataSource)
        }
    }
}
