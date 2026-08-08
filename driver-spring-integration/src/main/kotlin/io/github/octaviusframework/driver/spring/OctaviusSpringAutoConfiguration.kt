package io.github.octaviusframework.driver.spring

import io.github.octaviusframework.driver.session.OctaviusSession
import io.github.octaviusframework.driver.spring.exception.OctaviusExceptionTranslator
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.jdbc.support.JdbcTransactionManager
import org.springframework.transaction.PlatformTransactionManager
import javax.sql.DataSource

@AutoConfiguration(after = [DataSourceAutoConfiguration::class])
@ConditionalOnClass(OctaviusSession::class, DataSource::class)
open class OctaviusSpringAutoConfiguration {

    /**
     * Creates and registers an [OctaviusTemplate] bean if one is not already present in the application context.
     *
     * @param dataSource the underlying data source to use
     * @return a new instance of [OctaviusTemplate]
     */
    @Bean
    @ConditionalOnMissingBean
    open fun octaviusTemplate(dataSource: DataSource): OctaviusTemplate {
        return OctaviusTemplate(dataSource)
    }

    /**
     * Creates and registers a [PlatformTransactionManager] bean using a [DataSourceTransactionManager]
     * if one is not already present in the application context.
     * Nested transactions are enabled by default.
     *
     * @param dataSource the underlying data source to use
     * @return a new instance of [PlatformTransactionManager]
     */
    @Bean
    @ConditionalOnMissingBean
    open fun transactionManager(dataSource: DataSource): PlatformTransactionManager {
        val tm = JdbcTransactionManager(dataSource)
        tm.exceptionTranslator = OctaviusExceptionTranslator()
        tm.isNestedTransactionAllowed = true
        return tm
    }
}
