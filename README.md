# Octavius Driver

![Version](https://img.shields.io/badge/version-0.8.9-blue)
![Status](https://img.shields.io/badge/status-Work%20In%20Progress-orange)

A native, high-performance, and lightweight PostgreSQL database driver for Kotlin. 

**Octavius is NOT a traditional JDBC driver.** It uses standard JDBC interfaces (`java.sql.Connection`) merely as a "Trojan Horse" to integrate smoothly with modern connection pools like **HikariCP**. Once connected, it intentionally throws away legacy JDBC baggage—like stateful `ResultSet`s, `CallableStatement`s, and manual resource management—exposing a purely Kotlin-first, idiomatic API. 

It communicates directly with PostgreSQL via the Wire Protocol v3 without wrapping or delegating to any other database driver.

> **🚧 Work In Progress / Status**
> 
> The current version is **0.8.9**. The driver is fully capable of handling most database interactions, including complex types (arrays, composites, json) and integrates smoothly with connection pools like HikariCP, but **there is still some work to do** before it reaches a 1.0 release.

## Key Features

- **Native Protocol Implementation**: Directly implements PostgreSQL Wire Protocol v3, without delegating to or wrapping traditional PostgreSQL drivers.
- **Extended Query Protocol by Default**: Enforces the safer and more efficient Extended Query Protocol (Parse, Bind, Execute, Sync) for all data manipulation and queries.
- **Strong Type System Mapping**: Deep integration with Kotlin's type system. The `GlobalTypeRegistry` seamlessly handles standard PostgreSQL types as well as advanced structures like Composites, Arrays, Ranges, Records, and JSON.
- **Asynchronous Notifications**: Built-in support for PostgreSQL `LISTEN` / `NOTIFY` mechanisms via Kotlin Coroutines `SharedFlow`.
- **Connection Pool Ready**: Designed to work effortlessly with modern JDBC connection pools like **HikariCP**, while exposing its Kotlin session API.
- **Modern and Lightweight**: Strips away legacy JDBC features (e.g., `CallableStatement`, CLOB/BLOB handling, and stateful, mutable `ResultSet`s) to provide a streamlined, predictable, and fast abstraction.

## Architecture

The driver architecture is modular and highly layered:
- **`driver` module**: Core driver logic.
  - **IO & SSL**: Low-level, efficient handling of socket streams (`PgStream`), buffering, and secure connection negotiation.
  - **Message**: Parsing and building of PostgreSQL Wire Protocol v3 packets.
  - **Query**: The operational heart, executing queries using the Extended Query Protocol with support for named parameters.
  - **Codec, Converter & Registry**: A robust type system (`GlobalTypeRegistry`) that maps raw binary/text data directly to and from Kotlin types, supporting composites, arrays, records, and enums.
  - **Session & Transaction**: `OctaviusSession` and `OctaviusSavepoint` APIs providing a modern, native Kotlin interface for database interactions and transaction control.
  - **JDBC**: A compatibility layer bridging the native Octavius API with legacy JDBC infrastructure, enabling integration with connection pools like HikariCP.
- **`driver-spring-integration` module**: Provides native integration with the Spring Framework and Spring Boot (`OctaviusTemplate`, Exception Translation, AutoConfiguration).
- **`hikari` module**: Dedicated integration testing layer for HikariCP.

## Quick Start

Add the Octavius driver to your `build.gradle.kts` dependencies:

```kotlin
dependencies {
    implementation("io.github.octavius-framework:driver:0.8.9")
}
```

Since Octavius replaces legacy, stateful JDBC `ResultSet` with its own modern API, you interact with the database using `OctaviusSession`:

```kotlin
import io.github.octaviusframework.driver.jdbc.getOctaviusSession
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
    .fetchRow("id" to 1)

// 3. Strongly typed data extraction without ResultSet legacy
val id: Int = row.get("id")
val name: String = row.get("name")

session.close() // Safely returns the connection to the pool
```

## Documentation

Check out the detailed documentation available in the `docs/` folder:
- [**Octavius vs Legacy JDBC (Why this driver is different)**](docs/octavius-vs-jdbc.md)
- [Executing Queries (Native & Named)](docs/queries.md)
- [Transaction Management](docs/transactions.md)
- [Type System & Mapping](docs/type-system.md)
- [Listen & Notify (Asynchronous Flow)](docs/listen-notify.md)
- [Exception Translation](docs/exceptions.md)
- [Connection Properties](docs/properties.md)
- [Functions and Procedures](docs/functions-procedures.md)
