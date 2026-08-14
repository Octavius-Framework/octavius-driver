# Octavius Driver

[![Maven Central](https://img.shields.io/maven-central/v/io.github.octavius-framework/driver)](https://central.sonatype.com/search?q=io.github.octavius-framework.driver)
[![Build and Test](https://github.com/Octavius-Framework/octavius-driver/actions/workflows/tests.yml/badge.svg)](https://github.com/Octavius-Framework/octavius-driver/actions/workflows/tests.yml)
[![API Reference](https://img.shields.io/badge/docs-API%20reference-blue)](https://octavius-framework.github.io/octavius-driver/)
![Status](https://img.shields.io/badge/status-Work%20In%20Progress-orange)
![License](https://img.shields.io/badge/license-Apache%202.0-blue)

A native, high-performance, lightweight PostgreSQL driver for Kotlin.

**Octavius is not a traditional JDBC driver.** It leans on standard JDBC interfaces (`java.sql.Connection`) purely as a way in — enough to slot smoothly into modern connection pools like **HikariCP**. 
Past that point, it deliberately sheds the legacy JDBC baggage — stateful `ResultSet`s, `CallableStatement`s, manual resource bookkeeping — in favor of a purely Kotlin-first API.

It speaks PostgreSQL's Wire Protocol v3 directly, with no other database driver wrapped or delegated to underneath.

## Key Features

- **Native protocol implementation** — talks PostgreSQL Wire Protocol v3 directly, with nothing wrapped or delegated underneath.
- **Extended Query Protocol by default** — every data manipulation and query goes through the safer, more efficient Parse/Bind/Execute/Sync cycle.
- **A type system that actually fits Kotlin** — the `GlobalTypeRegistry` handles standard PostgreSQL types alongside composites, arrays, ranges, records, and JSON without friction.
- **Asynchronous notifications** — `LISTEN` / `NOTIFY` exposed as a Kotlin Coroutines `SharedFlow`.
- **Connection pool ready** — built to work effortlessly with modern JDBC connection pools like **HikariCP**, while still exposing its Kotlin session API on top.
- **Modern and lightweight** — no `CallableStatement`, no legacy JDBC `Blob`/`Clob` interface, no stateful mutable `ResultSet` — just a streamlined, predictable, fast abstraction.
- **Large Objects & COPY Support** — built-in native support for PostgreSQL Large Objects (`lo`) and bulk data transfers via the `COPY` protocol.

## Architecture

The driver is organized in clear, modular layers:
- **`driver` module** — core driver logic.
  - **IO & SSL** — efficient socket stream handling (`PgStream`), buffering, and secure connection negotiation.
  - **Message** — parsing and building of PostgreSQL Wire Protocol v3 packets.
  - **Query & Execution** — the operational core, running queries through the Extended Query Protocol with named-parameter support.
  - **Codec, Converter & Registry** — a type system (`GlobalTypeRegistry`) mapping raw binary/text data to and from Kotlin types, including composites, arrays, records, and enums.
  - **Session & Transaction** — `OctaviusSession` and `OctaviusSavepoint` provide a native Kotlin interface for database work and transaction control.
  - **Notification & LO** — `LISTEN`/`NOTIFY` support and native Large Object handling.
  - **JDBC** — the compatibility layer bridging the native Octavius API with legacy JDBC infrastructure, for pools like HikariCP.
- **`driver-spring-integration` module** — native Spring Framework / Spring Boot integration (`OctaviusTemplate`, exception translation, autoconfiguration).
- **`hikari-integration-tests` module** — dedicated integration testing layer for HikariCP.
- **`benchmarks` module** — JMH benchmarks comparing Octavius against `pgjdbc` (see [Performance](docs/performance.md)).
- **`examples/spring-app`** — a runnable sample Spring Boot application demonstrating `driver-spring-integration` end to end.

## Requirements

- **Java 21+**
- **PostgreSQL 18+** — Octavius exclusively speaks **PostgreSQL Wire Protocol v3.2** (introduced in PostgreSQL 18). Attempting to connect to older database versions (which expect v3.0) will fail at the protocol level during the initial handshake.

## Quick Start

Add the Octavius driver to your `build.gradle.kts` dependencies:

```kotlin
dependencies {
    implementation("io.github.octavius-framework:driver:0.9.4")
}
```

Octavius replaces the legacy, stateful JDBC `ResultSet` with its own modern API — you talk to the database through `OctaviusSession`:

```kotlin
import io.github.octaviusframework.driver.jdbc.getOctaviusSession
import io.github.octaviusframework.driver.row.get
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource

val config = HikariConfig().apply {
    jdbcUrl = "jdbc:octavius://localhost:5432/res_publica"
    username = "consul"
    password = "senatus_populusque"
}
val dataSource = HikariDataSource(config)

// 1. Pull a session through HikariCP via the custom jdbc:octavius protocol
val session = dataSource.getOctaviusSession()

// 2. Run a query with named parameters (Strict = exactly one row, or an exception)
val row = session.createNamedQuery("SELECT id, cognomen FROM senators WHERE id = @id")
    .fetchRowStrict("id" to 1)

// 3. Strongly typed extraction, no ResultSet in sight
val id: Int = row.get("id")
val cognomen: String = row.get("cognomen")

session.close() // Safely returns the connection to the pool
```

## Documentation

**[API Reference](https://octavius-framework.github.io/octavius-driver/)** — generated KDoc for every declaration across `driver`, `driver-spring-integration` and `hikari-integration-tests`. Rebuilt and published on each push to `master`. Reach for it when you need a signature, a property, or the full set of values in an enum.

The guides below cover the things a signature can't show — how the pieces behave together:
- [**Octavius vs Legacy JDBC (why this driver breaks the mold)**](docs/octavius-vs-jdbc.md)
- [Quickstart](docs/quickstart.md)
- [Session Initialization and Configuration](docs/initialization.md)
- [Spring Integration](docs/spring-integration.md)
- [Executing Queries (Native & Named)](docs/queries.md)
- [Transaction Management](docs/transactions.md)
- [Type System & Mapping](docs/type-system.md)
- [Listen & Notify (Asynchronous Flow)](docs/listen-notify.md)
- [Error Handling & Exceptions](docs/exceptions.md)
- [Functions and Procedures](docs/functions-procedures.md)
- [COPY Protocol (Bulk Data Transfers)](docs/copy.md)
- [Large Objects (LO)](docs/large-objects.md)
- [Performance (JMH benchmarks vs pgjdbc)](docs/performance.md)

## License

Octavius Driver is licensed under the [Apache License, Version 2.0](https://www.apache.org/licenses/LICENSE-2.0).
