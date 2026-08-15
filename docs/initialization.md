# Session Initialization and Configuration

Every connection Octavius opens is configured through one strongly-typed class — `OctaviusProperties`. Everything else on this page is a different route to filling it in: native factory functions take it directly, `OctaviusDataSource` exposes its common fields as bean properties, and `DriverManager` parses it out of a JDBC URL. String keys from a URL or a `java.util.Properties` object are matched case-insensitively against its fields; anything it does not recognize is kept aside and [sent to the server as a startup parameter](#startup-parameters).

## Getting a session

| Entry point                               | Reach for it when                                                     |
|:------------------------------------------|:----------------------------------------------------------------------|
| `getOctaviusSession(properties)`          | Native, typed, no pool. The simplest path in Kotlin code.             |
| `getOctaviusSession(url, user, password)` | You already have a URL and just need credentials alongside it.        |
| `getOctaviusSession(url, properties)`     | A URL supplies the address, typed properties supply the rest.         |
| `dataSource.getOctaviusSession()`         | Anything behind a `DataSource` — including a HikariCP pool.           |
| `connection.getOctaviusSession()`         | You are handed a `java.sql.Connection` and want the native API on it. |

### 1. Typed properties

No URL involved, everything type-checked:

```kotlin
import io.github.octaviusframework.driver.properties.OctaviusProperties
import io.github.octaviusframework.driver.jdbc.getOctaviusSession
import io.github.octaviusframework.driver.ssl.SslMode

val properties = OctaviusProperties().apply {
    serverName = "localhost"
    portNumber = 5432
    databaseName = "res_publica"
    user = "consul"
    password = "senatus_populusque"
    loginTimeout = 10
    sslmode = SslMode.REQUIRE

    // Anything the driver does not know becomes a server startup parameter
    additionalProperties["application_name"] = "LegioXIII-App"
}

val session = getOctaviusSession(properties)
```

If a URL carries part of the configuration, the `getOctaviusSession(url, properties)` overload parses it first and then merges the typed properties on top — so the typed values win on any field set in both.

### 2. `DriverManager`

The driver registers itself with `java.sql.DriverManager`, so a URL (optionally with a `Properties` object) is enough:

```kotlin
import java.sql.DriverManager
import io.github.octaviusframework.driver.jdbc.getOctaviusSession

val url = "jdbc:octavius://localhost:5432/res_publica?user=consul&password=senatus_populusque"
val connection = DriverManager.getConnection(url)

val session = connection.getOctaviusSession()
```

### 3. `OctaviusDataSource`

`OctaviusDataSource` bypasses `DriverManager` and takes typed values directly:

```kotlin
import io.github.octaviusframework.driver.jdbc.OctaviusDataSource
import io.github.octaviusframework.driver.jdbc.getOctaviusSession

val dataSource = OctaviusDataSource().apply {
    serverName = "localhost"
    portNumber = 5432
    databaseName = "res_publica"
    user = "consul"
    password = "senatus_populusque"
}

val session = dataSource.getOctaviusSession()
```

Every field of `OctaviusProperties` has an accessor here — address, credentials, the SSL group and every tuning knob in the [configuration reference](#configuration-reference) — so nothing is reachable only through a URL:

```kotlin
val dataSource = OctaviusDataSource().apply {
    serverName = "localhost"
    databaseName = "res_publica"
    user = "consul"
    password = "senatus_populusque"

    socketTimeout = 30
    maxCachedRowSize = 8192
    noticeHandler = "com.example.MyNoticeHandler"

    // Anything without an accessor of its own, startup parameters included
    setProperty("application_name", "LegioXIII-App")
}
```

That matters most behind a pool, because `dataSourceClassName` configuration goes through JavaBean setters and can only set what has one — see [below](#via-octaviusdatasource).

The `url` property remains the other route in: it parses a full URL and merges it into the same underlying `OctaviusProperties`, so the two can be combined.

```kotlin
val dataSource = OctaviusDataSource().apply {
    url = "jdbc:octavius://localhost:5432/res_publica?maxCachedRowSize=8192"
    user = "consul"
    password = "senatus_populusque"
}
```

> [!NOTE]
> Reading `dataSource.url` back renders the current configuration as a URL, but **never the password** — that string is the one most likely to reach a log. The password stays readable through `dataSource.password`, and `OctaviusProperties.copy()` is the lossless way to duplicate a full configuration.

## Adding HikariCP Connection Pooling

Octavius is fully compatible with connection pools like [HikariCP](https://github.com/brettwooldridge/HikariCP). Although it is a Kotlin-first driver, it implements standard `java.sql.Connection` precisely so it can slot into one.

### Via JDBC URL

```kotlin
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.github.octaviusframework.driver.jdbc.getOctaviusSession

val config = HikariConfig().apply {
    jdbcUrl = "jdbc:octavius://localhost:5432/res_publica"
    username = "consul"
    password = "senatus_populusque"
    maximumPoolSize = 10
}
val pool = HikariDataSource(config)

val session = pool.getOctaviusSession() // borrows a session
```

### Via `OctaviusDataSource`

```kotlin
val config = HikariConfig().apply {
    dataSourceClassName = "io.github.octaviusframework.driver.jdbc.OctaviusDataSource"

    addDataSourceProperty("serverName", "localhost")
    addDataSourceProperty("portNumber", "5432")
    addDataSourceProperty("databaseName", "res_publica")
    addDataSourceProperty("user", "consul")
    addDataSourceProperty("password", "senatus_populusque")

    // Tuning knobs work here too - each one is a bean property
    addDataSourceProperty("socketTimeout", "30")
    addDataSourceProperty("maxCachedRowSize", "8192")

    maximumPoolSize = 10
}
val pool = HikariDataSource(config)

val session = pool.getOctaviusSession()
```

`addDataSourceProperty` sets a JavaBean property by reflection, so the name has to match an accessor on `OctaviusDataSource` exactly — a name it does not know is an error at pool startup rather than a setting quietly ignored. Every property in the [configuration reference](#configuration-reference) has one.

Two things do **not** go through it:

* **`sslmode`**, because its accessor is typed as the `SslMode` enum. HikariCP converts a string value only for primitives, `Boolean` and `String`; anything else it passes through untouched, and a `String` arriving at a setter expecting `SslMode` fails the pool with *"argument type mismatch"*. Passing the enum itself — `addDataSourceProperty("sslmode", SslMode.REQUIRE)` — works, but a properties file or Spring's `data-source-properties` has only strings to offer.
* **Startup parameters**, which are not driver settings at all and so have no accessor by design.

Both fit on the `url` property, which is itself a bean property and takes a whole URL:

```kotlin
addDataSourceProperty("url", "jdbc:octavius://localhost:5432/res_publica?sslmode=require&application_name=curia-api")
```

Everything else — the timeouts, the buffer sizes, `ssl`, `noticeHandler`, the certificate paths — is a plain `Int`, `Boolean` or `String` accessor and takes a string value as it stands.

### Via a configured instance

The reflection above is only one of the two ways Hikari accepts a `DataSource`. Hand it an instance you configured yourself and it uses that object directly:

```kotlin
import io.github.octaviusframework.driver.ssl.SslMode

val octavius = OctaviusDataSource().apply {
    serverName = "localhost"
    databaseName = "res_publica"
    user = "consul"
    password = "senatus_populusque"

    sslmode = SslMode.REQUIRE   // an enum, not a string that has to survive a conversion
    socketTimeout = 30
    setProperty("application_name", "curia-api")
}

val pool = HikariDataSource(HikariConfig().apply {
    dataSource = octavius
    maximumPoolSize = 10
})
```

Nothing is set by name and nothing is coerced, so every accessor is usable at its real type and the caveats above do not apply — `sslmode` included. The compiler checks the configuration, which the string forms cannot. In Kotlin this is the route worth reaching for first; `dataSourceClassName` earns its place where the configuration has to come from a properties file or from Spring's `spring.datasource.hikari.data-source-properties`.

Closing a session obtained from a pool returns its connection to the pool rather than shutting it down.

Leave the pool's own auto-commit setting at its default (`true`). Configuring a pool with `auto-commit=false` makes every connection in it sit `idle in transaction` while waiting to be borrowed — see [Transactions](transactions.md#manual-control) for why, and what it collides with.

## What survives a return to the pool

A pooled connection outlives the session you borrowed it through, so whatever is left set on it becomes the next borrower's starting state. Some of that is cleaned up for you:

| Connection state                                       | Undone when the session closes?                                              |
|:-------------------------------------------------------|:-----------------------------------------------------------------------------|
| `autoCommit`, `readOnly`, `transactionIsolationLevel`  | Yes — HikariCP tracks these through its own proxy and restores its defaults. |
| A `COPY` the caller never finished                     | Yes — the session aborts it before handing the connection back.              |
| `LISTEN` registrations made via `notifications.listen` | Yes — the session issues `UNLISTEN *` if it subscribed to anything.          |
| A transaction left open by a hand-written `BEGIN`      | Yes — rolled back, since the driver cannot know what that work was for.      |
| **Anything else you set by running the SQL yourself**  | **No.**                                                                      |

That last row is the one to internalize, because it is not a gap anyone can close: neither the pool nor the driver parses the statements you send, so neither can know that a `SET search_path`, a `SET statement_timeout`, a `SET SESSION CHARACTERISTICS AS TRANSACTION ...`, a hand-written `LISTEN`, or a temporary table ever happened. All of it stays on the connection until the connection itself dies, and the next borrower inherits it.

Two habits keep you out of that:

* **Prefer the typed API wherever one exists** — `session.transactionIsolationLevel` over `SET SESSION CHARACTERISTICS`, `session.notifications.listen` over a raw `LISTEN`. Those are exactly the paths that are tracked and undone.
* **Put per-connection defaults in [startup parameters](#startup-parameters)** rather than setting them after connecting. A `search_path` supplied at connect time is part of every connection's identity, so there is nothing to leak and nothing to restore.

Queries themselves leave nothing behind: the driver executes through unnamed statements and portals, so nothing accumulates server-side from ordinary traffic.

## What happens when a session opens

The sequence is worth knowing, because two of its steps are where connections fail and one is why the *first* connection is slower than the rest:

1. **The socket is opened**, with `loginTimeout` as its connect timeout.
2. **SSL is negotiated** — unless `sslmode=disable`, the driver asks the server for TLS before anything else is sent. See the defaults below: by default it *tries*.
3. **The startup message goes out**, carrying `user`, `database`, `client_encoding=UTF8` and every [additional property](#startup-parameters) you set.
4. **Authentication runs** with the password supplied.
5. **Timeouts are applied** — `socketTimeout` becomes the socket read timeout, `maxCachedRowSize` the row buffer cap.
6. **The server version is checked.** Anything below PostgreSQL 18 gets the connection closed and an `InitializationException(UNSUPPORTED_SERVER_VERSION)`, naming the version received.
7. **The type catalog is loaded**, once per database — see below.

> [!IMPORTANT]
> **PostgreSQL 18 or newer is required.** Octavius speaks Wire Protocol v3.2 exclusively. Against an older server the failure shows up in step 6 as an `InitializationException`, or earlier at the protocol level during the handshake.

### The first connection pays for the type catalog

Step 7 reads the server's type catalog into a `TypeRegistry` keyed by **host, port and database name** — deliberately not by the full URL, so credentials, SSL settings and timeouts do not fragment the cache. Every later connection to that same database, from any pool in the JVM, reuses the loaded registry and skips the work.

In practice: the first connection a pool opens is measurably slower than its siblings, and pre-warming one connection at startup moves that cost out of your first request. If your application connects to thousands of distinct databases over its lifetime, `GlobalTypeRegistry.removeRegistry(url)` releases a registry you are done with. The details of what gets loaded, and how to refresh it after a migration, are in [Type System](type-system.md#the-catalog-load).

## Startup parameters

Any property the driver does not recognize is passed through to PostgreSQL in the startup message, which makes `additionalProperties` the place to set server-side session defaults at connect time — no `SET` round trip afterwards:

```kotlin
val properties = OctaviusProperties().apply {
    // ... address and credentials ...
    additionalProperties["application_name"] = "LegioXIII-App"  // shows up in pg_stat_activity
    additionalProperties["search_path"] = "castra, public"
    additionalProperties["statement_timeout"] = "5000"
}
```

The same works through a URL — `?application_name=LegioXIII-App` — since unknown query parameters land in the same place.

**Character encoding is the one exception you cannot override**: the driver always declares UTF-8 to the server, and there is no plan to support anything else, so it is not exposed as a setting.

## Notices from the server

PostgreSQL sends notices alongside ordinary traffic — a `RAISE NOTICE` from a routine, a "table does not exist, skipping" from a `DROP ... IF EXISTS`, a deprecation warning. They are not errors and nothing is interrupted by one.

JDBC's answer is `SQLWarning` objects accumulating on the connection until someone calls `clearWarnings()`, which is [a leak waiting to happen](octavius-vs-jdbc.md#what-answers-quietly-instead-of-throwing). Octavius pushes instead: every notice is logged as it arrives, and handed to a `NoticeHandler` if you configured one. Nothing accumulates and nothing needs draining.

### They are always logged

Regardless of any handler, notices go to a **dedicated logger** — `io.github.octaviusframework.driver.Notice` — at a level mirroring the server's severity:

| Server severity         | Logged at |
|:------------------------|:----------|
| `WARNING`               | `warn`    |
| `NOTICE`, `INFO`, `LOG` | `info`    |
| `DEBUG`                 | `debug`   |

The logger has its own name precisely so it can be turned up or down on its own, without touching the rest of the driver's logging:

```xml
<logger name="io.github.octaviusframework.driver.Notice" level="WARN"/>
```

Each line is prefixed with the backend process id, so notices from different connections stay distinguishable.

### Handling them yourself

Implement `NoticeHandler` and name the class in the [`noticeHandler` property](#network-and-limits):

```kotlin
import io.github.octaviusframework.driver.notice.NoticeHandler
import io.github.octaviusframework.driver.notice.PgNotice

object AuditNoticeHandler : NoticeHandler {
    override fun handleNotice(notice: PgNotice) {
        if (notice.severity == "WARNING") {
            metrics.counter("db.warnings", "code", notice.code).increment()
        }
    }
}
```

```kotlin
properties.noticeHandler = "com.example.AuditNoticeHandler"
// or on the URL: ?noticeHandler=com.example.AuditNoticeHandler
```

A Kotlin `object` is reused as a singleton across every connection; a class is instantiated per connection through its no-arg constructor. `PgNotice` exposes `severity`, `code`, `message`, `detail`, `hint` and `where`, plus `rawFields` for anything the server sent that those don't cover.

> [!WARNING]
> **`handleNotice` runs synchronously on the connection's network thread**, in the middle of reading the protocol stream. Whatever it does, the query waiting behind it waits too — so no blocking calls, no database work, no HTTP. Hand anything slow to a queue or an executor and return immediately.

An exception thrown by a handler is caught and logged rather than propagated: a broken handler cannot desynchronize the connection or fail somebody's query.

## Session health and lifecycle

Three members on the session that are easy to miss, all of them about the connection rather than about queries:

| Member             | Does                                                                                                                                        |
|:-------------------|:--------------------------------------------------------------------------------------------------------------------------------------------|
| `isValid(timeout)` | Round-trips an empty query to prove the connection is alive. `timeout` is in seconds and only ever *tightens* the existing network timeout. |
| `networkTimeout`   | Read timeout in milliseconds for this connection, readable and writable at any point. `socketTimeout` sets its initial value.               |
| `abort()`          | Kills the connection outright so a pool evicts it instead of reusing it. The blunt instrument for "this connection must not be handed on".  |

`isValid` is what HikariCP calls to validate a borrowed connection, and it takes the connection lock like any other exchange — so a validation probe never shortens the deadline of a query running on another thread. It answers `false` for a dead connection rather than throwing; the one thing it does throw for is misuse, such as being called from inside a streaming block on its own connection.

`abort()` is the same work `Connection.abort(executor)` does, minus the exception that method deliberately throws to make a pool notice. Reach for it when a connection is known to be in a state you would rather not pass on — after an interruptible listener loop, for instance, which uses it internally on shutdown.

## Configuration reference

### Connection and authentication

| Property                       | Default     | Meaning                         |
|:-------------------------------|:------------|:--------------------------------|
| `user`                         | `postgres`  | Database username.              |
| `password`                     | none        | Password for that user.         |
| `serverName` (or `host`)       | `localhost` | Address of the database server. |
| `portNumber` (or `port`)       | `5432`      | Port the server listens on.     |
| `databaseName` (or `database`) | `postgres`  | Database to connect to.         |

### Network and limits

| Property                         | Default                                                     | Meaning                                                                                                                                                                                                   |
|:---------------------------------|:------------------------------------------------------------|:----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `loginTimeout`                   | `DriverManager.getLoginTimeout()`, or 10 s when that is `0` | Seconds to wait for the socket connect and login.                                                                                                                                                         |
| `socketTimeout`                  | `0` — wait forever                                          | Seconds to wait on a socket read before failing.                                                                                                                                                          |
| `cancelSignalTimeout`            | `10`                                                        | Seconds allowed for a [cancel request](queries.md#cancelling-a-query-in-flight), covering both its connect and its reads. It travels on a connection of its own, so it gets a budget of its own.          |
| `maxCachedRowSize`               | `65536`                                                     | Largest row, in bytes, kept in the reusable row buffer.                                                                                                                                                   |
| `notificationBufferCapacity`     | `256`                                                       | Capacity of the `LISTEN`/`NOTIFY` buffer; see [Listen & Notify](listen-notify.md).                                                                                                                        |
| `noticeHandler`                  | none                                                        | Fully-qualified class name of a `NoticeHandler` for server notices. A Kotlin `object` is reused as a singleton across connections; a class is instantiated per connection through its no-arg constructor. |
| `initialParameterWriterCapacity` | `1024`                                                      | Starting size, in bytes, of the per-connection parameter buffer.                                                                                                                                          |
| `maxParameterWriterCapacity`     | `65536`                                                     | Cap for that buffer; it shrinks back to the initial size after a query that exceeded it.                                                                                                                  |

### SSL

| Property      | Default                                | Meaning                                                                                      |
|:--------------|:---------------------------------------|:---------------------------------------------------------------------------------------------|
| `sslmode`     | `PREFER`, or `REQUIRE` if `ssl = true` | Negotiation mode; see the table below.                                                       |
| `ssl`         | unset                                  | Shorthand: `true` raises the default to `REQUIRE`. Ignored when `sslmode` is set explicitly. |
| `sslrootcert` | none                                   | Path to the root CA certificate.                                                             |
| `sslcert`     | none                                   | Path to the client certificate.                                                              |
| `sslkey`      | none                                   | Path to the client private key.                                                              |
| `sslpassword` | none                                   | Password protecting the client private key.                                                  |

| `SslMode`     | Behaviour                                                                                                                  |
|:--------------|:---------------------------------------------------------------------------------------------------------------------------|
| `DISABLE`     | No TLS request is sent at all.                                                                                             |
| `PREFER`      | Ask for TLS; fall back to a plaintext connection if the server declines. Default.                                          |
| `REQUIRE`     | Encrypt or fail — but no certificate verification.                                                                         |
| `VERIFY_CA`   | Require TLS and verify the server certificate — against `sslrootcert` when given, otherwise the JVM's default trust store. |
| `VERIFY_FULL` | Also verify that the certificate matches the hostname.                                                                     |

The handshake is restricted to TLS 1.2 and 1.3. A server that refuses TLS under `REQUIRE`, `VERIFY_CA` or `VERIFY_FULL` produces an `InitializationException(SSL_ERROR)` naming the mode you asked for. Note the default: unless you say otherwise, Octavius *attempts* an encrypted connection and quietly accepts plaintext if the server has no TLS — set `REQUIRE` or stronger when that fallback is not acceptable.

The mode covers more than the session's own socket. [`cancelQuery()`](queries.md#cancelling-a-query-in-flight) has to open a second connection — the protocol gives it no choice — and it carries the backend's cancel key, so it negotiates under the same settings rather than falling back to plaintext.
