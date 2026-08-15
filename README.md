# Octavius Driver

[![Maven Central](https://img.shields.io/maven-central/v/io.github.octavius-framework/driver)](https://central.sonatype.com/search?q=io.github.octavius-framework.driver)
[![Build and Test](https://github.com/Octavius-Framework/octavius-driver/actions/workflows/tests.yml/badge.svg)](https://github.com/Octavius-Framework/octavius-driver/actions/workflows/tests.yml)
[![API Reference](https://img.shields.io/badge/docs-API%20reference-blue)](https://octavius-framework.github.io/octavius-driver/)
![Status](https://img.shields.io/badge/status-Work%20In%20Progress-orange)
![License](https://img.shields.io/badge/license-Apache%202.0-blue)

A native, high-performance, lightweight PostgreSQL driver for Kotlin.

**Octavius is not a traditional JDBC driver.** It implements `java.sql.Connection` purely as a way in — enough to slot into modern connection pools like **HikariCP**. Past that point it sheds the legacy baggage: no stateful `ResultSet`, no `CallableStatement`, no manual resource bookkeeping. It speaks PostgreSQL's Wire Protocol v3.2 directly, with no other driver wrapped or delegated to underneath.

```kotlin
val senators: List<Senator> = session
    .createNamedQuery("SELECT id, cognomen FROM senators WHERE province_id = @province")
    .fetchObjects("province" to 7)
```

## Key Features

- **Native protocol implementation** — Wire Protocol v3.2 spoken directly, nothing wrapped underneath.
- **Parameters bound, not interpolated** — queries and DML go through the Extended Query Protocol's Parse/Bind/Execute cycle, in binary. Statements with nothing to bind (DDL, `SET`, `LISTEN`, `COPY`) use the simple protocol, which is what it is for.
- **A type system that reads your database** — the catalog is loaded from *your* schema at connect time, so a type you created is never an unknown OID: enums, composites, domains, ranges and table row types all come back as usable values without being taught to the driver. Binding one to a class of your own is a single `registerEnum<T>()` or `registerAutoComposite<T>()` at startup, and nested structures like `List<YourDataClass>` follow from there.
- **Results in the shape you asked for** — `fetchRows`, `fetchObjects<T>`, `fetchField<T>`, each with single-row and strict variants, plus `forEach*` for streaming results too large to hold.
- **Exceptions you can act on** — a flat hierarchy keyed by SQLSTATE, each carrying the server's own error fields plus the SQL and parameters your application sent.
- **Asynchronous notifications** — `LISTEN` / `NOTIFY` as a Kotlin Coroutines `SharedFlow`.
- **Bulk paths that are actually fast** — native `COPY` support, and `UNNEST` inserts that beat classic JDBC batching by ~3×.
- **Large Objects as a first-class API** — `lo` support without unwrapping to a vendor interface.
- **Connection pool ready** — designed around HikariCP, with the Kotlin session API layered on top.

## Requirements

- **Java 21+**
- **PostgreSQL 18+** — Octavius speaks **Wire Protocol v3.2** exclusively, introduced in PostgreSQL 18. Older servers expect v3.0 and the connection fails during the handshake.

## Quick Start

```kotlin
dependencies {
    implementation("io.github.octavius-framework:driver:0.9.4")
    implementation("com.zaxxer:HikariCP:5.1.0")

    // Or, for Spring Boot - brings the driver in transitively
    // implementation("io.github.octavius-framework:driver-spring-integration:0.9.4")
}
```

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

// 2. Named parameters, and exactly one row expected (or an exception)
val row = session.createNamedQuery("SELECT id, cognomen FROM senators WHERE id = @id")
    .fetchRowStrict("id" to 1)

val id: Int = row.get("id")
val cognomen: String = row.get("cognomen")

// 3. Or skip the row entirely and map straight onto your own class
data class Senator(val id: Int, val cognomen: String)

val active: List<Senator> = session
    .createNativeQuery("SELECT id, cognomen FROM senators WHERE active = $1")
    .fetchObjects(true)

// 4. Group work into a transaction - committed on success, rolled back on any throw
session.transaction.required {
    createNativeQuery("INSERT INTO senators (cognomen) VALUES ($1)").update("Cato")
    createNativeQuery("INSERT INTO senate_logs (event) VALUES ($1)").update("New senator inducted")
}

session.close() // Safely returns the connection to the pool
```

## Performance

Measured against `pgjdbc` on the same machine, same JVM, same work ([full numbers and caveats](docs/performance.md)):

- **Object mapping ties** — 0.206 ± 0.027 against 0.199 ± 0.008 ops/ms, a dead heat on the path most applications live on.
- **`UNNEST` bulk inserts are 3.2× faster than classic JDBC batching**, and tie with pgjdbc's `reWriteBatchedInserts` optimization — while remaining usable for `UPDATE` and `DELETE`, where that optimization does not apply.
- **Array decoding costs ~22%** for going through the general conversion machinery — the same machinery that makes a `List<Senator>` of composites work.

## Documentation

**[API Reference](https://octavius-framework.github.io/octavius-driver/)** — generated KDoc for every declaration across `driver`, `driver-spring-integration` and `hikari-integration-tests`, rebuilt on each push to `master`. Reach for it when you need a signature, a property, or the values of an enum.

The guides cover what a signature cannot show — how the pieces behave together.

**Start here**
- [Quickstart](docs/quickstart.md) — from an empty project to a first query.
- [**Octavius vs Legacy JDBC**](docs/octavius-vs-jdbc.md) — what was dropped, and why.
- [Session Initialization and Configuration](docs/initialization.md) — every connection option, pooling, and what survives a return to the pool.

**Everyday work**
- [Executing Queries](docs/queries.md) — parameters, the `fetch*` family, streaming.
- [Transaction Management](docs/transactions.md) — block API, manual control, savepoints, isolation.
- [Type System & Mapping](docs/type-system.md) — how columns become Kotlin types, and how to extend that.
- [Error Handling & Exceptions](docs/exceptions.md) — the hierarchy, and catching at the right altitude.
- [Spring Integration](docs/spring-integration.md) — `OctaviusTemplate` and autoconfiguration.

**Specialized**
- [Functions and Procedures](docs/functions-procedures.md) — no `CallableStatement` required.
- [COPY Protocol](docs/copy.md) — bulk import and export.
- [Listen & Notify](docs/listen-notify.md) — asynchronous events as a flow.
- [Large Objects](docs/large-objects.md) — beyond what `bytea` can hold.
- [Performance](docs/performance.md) — JMH benchmarks against `pgjdbc`.

## Architecture

- **`driver`** — the core.
  - **IO & SSL** — socket handling (`PgStream`), buffering, TLS negotiation.
  - **Message** — parsing and building Wire Protocol v3.2 packets.
  - **Query & Execution** — the operational core: Extended Query Protocol with named-parameter support.
  - **Codec, Converter & Registry** — the two-layer type system, from raw binary through to your own classes.
  - **Session & Transaction** — `OctaviusSession`, transaction blocks and savepoints.
  - **Notification & LO** — `LISTEN`/`NOTIFY` and Large Objects.
  - **JDBC** — the compatibility layer that lets pools like HikariCP manage the connection.
- **`driver-spring-integration`** — `OctaviusTemplate`, exception translation, Spring Boot autoconfiguration.
- **`hikari-integration-tests`** — integration tests against a real HikariCP pool.
- **`benchmarks`** — JMH benchmarks against `pgjdbc`.
- **`examples/spring-app`** — a runnable Spring Boot sample.

## License

Octavius Driver is licensed under the [Apache License, Version 2.0](https://www.apache.org/licenses/LICENSE-2.0).
