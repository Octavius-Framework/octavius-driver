# HikariCP Integration

Octavius Driver is fully compatible with [HikariCP](https://github.com/brettwooldridge/HikariCP), the fastest and most widely used JDBC connection pool. 

Although Octavius is a Kotlin-first driver, it deliberately implements `java.sql.Connection` purely to slot smoothly into modern connection pools.

## Configuration using JDBC URL

The simplest way to configure HikariCP is by providing the JDBC URL. HikariCP will automatically use the `DriverManager` to locate the Octavius driver.

```kotlin
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.github.octaviusframework.driver.jdbc.getOctaviusSession

fun configureHikari(): HikariDataSource {
    val config = HikariConfig().apply {
        jdbcUrl = "jdbc:octavius://localhost:5432/my_database"
        username = "my_user"
        password = "my_password"
        
        // Recommended pool settings
        maximumPoolSize = 10
        minimumIdle = 2
        connectionTimeout = 30000 // 30 seconds
    }
    
    return HikariDataSource(config)
}
```

## Configuration using DataSource Class

Alternatively, you can configure HikariCP using the Octavius native `DataSource` implementation (`io.github.octaviusframework.driver.jdbc.OctaviusDataSource`). This avoids the `DriverManager` overhead and allows you to set properties directly.

```kotlin
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource

fun configureHikariWithDataSource(): HikariDataSource {
    val config = HikariConfig().apply {
        dataSourceClassName = "io.github.octaviusframework.driver.jdbc.OctaviusDataSource"
        
        addDataSourceProperty("serverName", "localhost")
        addDataSourceProperty("portNumber", "5432")
        addDataSourceProperty("databaseName", "my_database")
        addDataSourceProperty("user", "my_user")
        addDataSourceProperty("password", "my_password")
        
        maximumPoolSize = 10
    }
    
    return HikariDataSource(config)
}
```

## Getting the Session

Once HikariCP is configured, you don't use standard `java.sql.Connection` directly. Instead, you extract an `OctaviusSession` using the `.getOctaviusSession()` extension function:

```kotlin
val dataSource = configureHikari()

// Get native Octavius session
val session = dataSource.getOctaviusSession()

// Work with the session
val rows = session.createNamedQuery("SELECT 1 as number").fetchRows()

// Return connection to the pool
session.close()
```
