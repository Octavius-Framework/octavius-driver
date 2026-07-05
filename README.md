# Octavius JDBC Driver

![Version](https://img.shields.io/badge/version-0.4.0-blue)
![Status](https://img.shields.io/badge/status-Work%20In%20Progress-orange)

A native, high-performance, and lightweight PostgreSQL database driver for Kotlin, implementing some the standard JDBC interfaces while communicating directly with PostgreSQL via the Wire Protocol v3.

> **🚧 Work In Progress / Status**
> 
> The current version is **0.4.0**. The driver generally works and is capable of handling database interactions, but **there is still a lot of work to do** before it reaches a fully stable 1.0 release. Expect some rough edges and missing features.

## Features

- **Native Protocol Implementation**: Directly implements PostgreSQL Wire Protocol v3 without wrapping traditional drivers.
- **Extended Query Protocol by Default**: Enforces the safer and more efficient Extended Query Protocol (Parse, Bind, Execute, Sync) for data manipulation and querying.
- **Strong Type System Mapping**: Deep integration with Kotlin's type system, featuring an extensive `GlobalTypeRegistry` capable of handling both standard and custom PostgreSQL types (like composites and arrays).
- **Modern and Lightweight**: Strips away legacy JDBC features (like `CallableStatement`, CLOB/BLOB handling, and stateful result sets) to provide a streamlined, high-speed abstraction that works seamlessly with modern connection pools like HikariCP.

## Architecture

The driver architecture is split into several distinct layers:
- **IO / Message**: Low-level handling of socket streams (`PgStream`) and parsing/building of Postgres wire packets.
- **Query**: The `QueryExecutor` acts as the operational heart, routing simple queries through the Simple Query Protocol and DML/DQL through the Extended Query Protocol.
- **Type / Mapping**: Maps raw binary data directly to and from Kotlin types. Supports dynamic codecs for complex types.
- **JDBC**: Implements essential `java.sql.Connection` and `java.sql.Statement` functionality.

## Quick Start

You can add the Octavius driver to your project by declaring the dependency in your `build.gradle.kts`:

```kotlin
dependencies {
    implementation("io.github.octavius-framework:octavius-driver:0.4.0")
}
```

Since Octavius strips away legacy JDBC stateful `ResultSet`, you interact with the database using its query API:

```kotlin
import io.github.octaviusframework.driver.jdbc.getOctaviusConnection
import io.github.octaviusframework.driver.query.get
import java.util.Properties

val props = Properties().apply {
    setProperty("user", "postgres")
    setProperty("password", "secret")
}

// 1. Establish connection using the custom jdbc:octavius protocol
val conn = getOctaviusConnection("jdbc:octavius://localhost:5432/my_db", props)

// 2. Execute a query with named parameters
val row = conn.createNamedQuery("SELECT id, name FROM users WHERE id = @id")
    .fetchOne("id" to 1)

// 3. Strongly typed data extraction without ResultSet legacy
val id: Int = row.get("id")
val name: String = row.get("name")
```

## Roadmap
- [ ] Add support for `getWarnings()` and `clearWarnings()`.
- [ ] Full support for connection pools (HikariCP)
- [ ] Better query API
- [ ] Converters optimizations
- [ ] More tests
- [ ] Better README
- [ ] Documentation
- [ ] Many other things
