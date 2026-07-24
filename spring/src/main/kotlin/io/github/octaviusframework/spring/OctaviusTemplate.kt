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

class OctaviusTemplate(private val dataSource: DataSource, val exceptionTranslator: SQLExceptionTranslator = OctaviusExceptionTranslator()) {

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
            DataSourceUtils.doReleaseConnection(con, dataSource)
        }
    }
}
