# Octavius vs Legacy JDBC

Octavius takes a deliberately radical stance on database access in the JVM world. 
It technically implements `java.sql.Driver` and `java.sql.Connection` — but it strips out and disables most of what the JDBC specification actually asks of a driver.

The reasoning: JDBC's core contracts (`ResultSet`, `Statement`) were designed for a different era of Java. They push developers toward stateful, mutable objects, manual index-based binding, and a constant risk of resource leaks if something isn't closed. 
Octavius replaces all of that with a functional, Kotlin-first API — and even the driver's own name nods to reinvention: Octavius was the birth name of Gaius Octavius, before Rome came to know him as Augustus.

## The "Trojan Horse" Strategy (Connection Pools)

Fittingly for a driver named after Augustus — who traced his lineage back to Aeneas, the Trojan refugee of legend — Octavius's JDBC compatibility layer borrows a trick from the same story. If the driver throws away most of JDBC, why implement `java.sql.Connection` at all?

The answer is connection pooling. Production JVM applications lean on mature pools like **HikariCP**, and those pools need standard JDBC surfaces to manage, validate, and recycle connections. So Octavius implements just enough JDBC — a gift left outside the gates — to get waved through:
- Connection lifecycle (`close()`, `isClosed()`, `isValid()`)
- Basic transaction boundaries (`commit()`, `rollback()`, `setAutoCommit()`)
- Network timeout handling

Once the connection has cleared the gate, though, you stop reaching for JDBC. Instead, pull the native `OctaviusSession` straight out of the `DataSource`:
```kotlin
// Internally calls .getConnection() and wraps the result in the native API
val session = dataSource.getOctaviusSession()

// From here on, 'session' is all you need
```

## What Is Explicitly Unsupported?

Calling any of the following legacy methods on the underlying `OctaviusConnection` throws an `InvalidOperationException` with reason `FEATURE_NOT_SUPPORTED`.

### 1. `PreparedStatement` and `CallableStatement`
No index-based binding (`stmt.setString(1, "value")`). Octavius replaces both with `createNativeQuery` and `createNamedQuery`, removing the statement lifecycle entirely and making parameter binding harder to get wrong.

### 2. `ResultSet`
There's no mutable cursor to walk with `.next()`. Octavius hydrates results straight into memory — as `List<Row>`, a single typed field via `fetchField<T>()`, or fully-formed Kotlin data classes via `fetchObject<T>()`.

### 3. JDBC Batching
`addBatch()` / `executeBatch()` aren't implemented in their standard JDBC shape. Octavius favors PostgreSQL-native bulk techniques — array binding, structured inserts — instead.

### 4. Legacy LOBs (BLOB, CLOB)
`createBlob()` / `createClob()` don't exist here. Binary and text data map directly to plain Kotlin `ByteArray` and `String`, backed by PostgreSQL's `bytea` and `text` through the `GlobalTypeRegistry`.

### 5. DatabaseMetaData
The heavyweight JDBC metadata API is skipped entirely. If you need metadata, query `pg_catalog` directly through `OctaviusSession`.

## Summary

Dropping JDBC's historical baggage buys Octavius:
- **No resource-leak busywork** — no nested `try-with-resources` blocks just to close a `ResultSet` and a `Statement`.
- **A genuinely Kotlin-idiomatic API** — reified generics and safe mapping in place of old Java patterns.
- **Protocol-level safety** — the Extended Query Protocol (v3) is enforced at the wire level, not emulated by the driver.
