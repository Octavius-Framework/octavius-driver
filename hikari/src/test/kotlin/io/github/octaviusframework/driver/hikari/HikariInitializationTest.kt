package io.github.octaviusframework.driver.hikari

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertNotNull

class HikariInitializationTest {

    @Test
    fun `should initialize hikari with octavius data source via class name`() {
        val config = HikariConfig()
        config.dataSourceClassName = "io.github.octaviusframework.driver.jdbc.OctaviusDataSource"
        config.addDataSourceProperty("serverName", "localhost")
        config.addDataSourceProperty("portNumber", "5432")
        config.addDataSourceProperty("databaseName", "octavius_test")
        config.addDataSourceProperty("user", "postgres")
        config.addDataSourceProperty("password", "1234")

        val ds = HikariDataSource(config)
        assertDoesNotThrow {
            ds.connection.use { conn ->
                assertNotNull(conn)
            }
        }
        ds.close()
    }

    @Test
    fun `should initialize hikari with jdbc url directly`() {
        val config = HikariConfig()
        config.jdbcUrl = "jdbc:octavius://localhost:5432/octavius_test"
        config.username = "postgres"
        config.password = "1234"
        
        // When using jdbcUrl, Hikari will try to use DriverManager to find the driver
        val ds = HikariDataSource(config)
        assertDoesNotThrow {
            ds.connection.use { conn ->
                assertNotNull(conn)
            }
        }
        ds.close()
    }

    @Test
    fun `should initialize hikari using properties`() {
        val props = java.util.Properties()
        props.setProperty("dataSourceClassName", "io.github.octaviusframework.driver.jdbc.OctaviusDataSource")
        props.setProperty("dataSource.serverName", "localhost")
        props.setProperty("dataSource.portNumber", "5432")
        props.setProperty("dataSource.databaseName", "octavius_test")
        props.setProperty("dataSource.user", "postgres")
        props.setProperty("dataSource.password", "1234")
        
        val config = HikariConfig(props)
        val ds = HikariDataSource(config)
        assertDoesNotThrow {
            ds.connection.use { conn ->
                assertNotNull(conn)
            }
        }
        ds.close()
    }
}
