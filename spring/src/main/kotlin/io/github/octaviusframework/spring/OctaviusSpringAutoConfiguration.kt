package io.github.octaviusframework.spring

import io.github.octaviusframework.driver.session.OctaviusSession
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.transaction.PlatformTransactionManager
import javax.sql.DataSource

@AutoConfiguration(after = [DataSourceAutoConfiguration::class])
@ConditionalOnClass(OctaviusSession::class, DataSource::class)
open class OctaviusSpringAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    open fun octaviusTemplate(dataSource: DataSource): OctaviusTemplate {
        return OctaviusTemplate(dataSource)
    }

    @Bean
    @ConditionalOnMissingBean
    open fun transactionManager(dataSource: DataSource): PlatformTransactionManager {
        val tm = DataSourceTransactionManager(dataSource)
        tm.isNestedTransactionAllowed = true
        return tm
    }
}
