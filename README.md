# Octavius JDBC Driver

![Version](https://img.shields.io/badge/version-0.5.0-blue)
![Status](https://img.shields.io/badge/status-Work%20In%20Progress-orange)

A native, high-performance, and lightweight PostgreSQL database driver for Kotlin, implementing some the standard JDBC interfaces while communicating directly with PostgreSQL via the Wire Protocol v3.

> **🚧 Work In Progress / Status**
> 
> The current version is **0.5.0**. The driver generally works and is capable of handling database interactions, but **there is still a lot of work to do** before it reaches a fully stable 1.0 release. Expect some rough edges and missing features.

## Features

- **Native Protocol Implementation**: Directly implements PostgreSQL Wire Protocol v3 without wrapping traditional drivers.
- **Extended Query Protocol by Default**: Enforces the safer and more efficient Extended Query Protocol (Parse, Bind, Execute, Sync) for data manipulation and querying.
- **Strong Type System Mapping**: Deep integration with Kotlin's type system, featuring an extensive `GlobalTypeRegistry` capable of handling both standard and custom PostgreSQL types (like composites and arrays).
- **Modern and Lightweight**: Strips away legacy JDBC features (like `CallableStatement`, CLOB/BLOB handling, and stateful result sets) to provide a streamlined, high-speed abstraction.

## Architecture

The driver architecture is split into several sub-projects and layers:
- **`driver` module**: Core driver logic.
  - **IO / Message**: Low-level handling of socket streams (`PgStream`) and parsing/building of Postgres wire packets.
  - **Query**: The `QueryExecutor` acts as the operational heart, routing simple queries through the Simple Query Protocol and DML/DQL through the Extended Query Protocol.
  - **Type / Mapping**: Maps raw binary data directly to and from Kotlin types. Supports dynamic codecs for complex types.
  - **Session**: `OctaviusSession` and `OctaviusSavepoint` APIs wrapping connections.
- **`hikari` module**: Dedicated integration and tests for HikariCP.

## Quick Start

You can add the Octavius driver to your project by declaring the dependency in your `build.gradle.kts`:

```kotlin
dependencies {
    implementation("io.github.octavius-framework:octavius-driver:0.5.0")
    // Optionally include HikariCP integration
    implementation("io.github.octavius-framework:octavius-hikari:0.5.0")
}
```

Since Octavius strips away legacy JDBC stateful `ResultSet`, you interact with the database using its modern session API:

```kotlin
import io.github.octaviusframework.driver.jdbc.getOctaviusSession
import io.github.octaviusframework.driver.query.get
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource

val config = HikariConfig().apply {
    jdbcUrl = "jdbc:octavius://localhost:5432/my_db"
    username = "postgres"
    password = "password"
}
val dataSource = HikariDataSource(config)

// 1. Establish session using the custom jdbc:octavius protocol via HikariCP
val session = dataSource.getOctaviusSession()

// 2. Execute a query with named parameters
val row = session.createNamedQuery("SELECT id, name FROM users WHERE id = @id")
    .fetchOne("id" to 1)

// 3. Strongly typed data extraction without ResultSet legacy
val id: Int = row.get("id")
val name: String = row.get("name")

session.close() // Returns connection safely to Hikari
```

## Roadmap
- [ ] Better query API
- [ ] Converters optimizations
- [ ] More tests
- [ ] Better README
- [ ] Documentation
- [ ] Many other things
