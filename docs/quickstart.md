# Quickstart

## 1. Add the Dependency

Add the Octavius driver to your project dependencies.

**Gradle (Kotlin DSL):**
```kotlin
dependencies {
    implementation("io.github.octavius-framework:driver:<version>")
}
```

## 2. Establish a Connection

While Octavius uses the standard JDBC `Connection` as an entry point, it replaces legacy JDBC `ResultSet`s with its own modern API.

You can connect directly using `HikariCP`:

```kotlin
import io.github.octaviusframework.driver.jdbc.getOctaviusSession
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource

fun main() {
    val config = HikariConfig().apply {
        jdbcUrl = "jdbc:octavius://localhost:5432/my_database"
        username = "my_user"
        password = "my_password"
    }
    
    val dataSource = HikariDataSource(config)
    
    // Pull a session through HikariCP via the custom jdbc:octavius protocol
    val session = dataSource.getOctaviusSession()
    
    // Proceed with querying...
    
    session.close() // Safely returns the connection to the pool
}
```

## 3. Execute a Query

Once you have a session, you can execute named queries and extract strongly-typed results without dealing with `ResultSet`s.

```kotlin
// Run a query with named parameters
val rows = session.createNamedQuery("SELECT id, name FROM users WHERE active = @active")
    .fetchRows("active" to true)

// Strongly typed extraction
for (row in rows) {
    val id: Int = row.get("id")
    val name: String = row.get("name")
    println("User: $id - $name")
}

// Inserting data
session.createNamedQuery("INSERT INTO users (id, name, active) VALUES (@id, @name, @active)")
    .update("id" to 1, "name" to "Marcus", "active" to true)
```

## Next Steps
- Learn more about [Type System](type-system.md)
- Configure [Connection Pooling with HikariCP](hikari.md)
- Integrate with [Spring Boot](spring-integration.md)
