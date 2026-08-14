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

It exposes the fields a `DataSource` is normally configured with — address, credentials and the SSL group — but not the tuning knobs further down this page. Those go through its `url` property, which parses a full URL and merges it into the same underlying `OctaviusProperties`:

```kotlin
val dataSource = OctaviusDataSource().apply {
    url = "jdbc:octavius://localhost:5432/res_publica?maxCachedRowSize=8192&noticeHandler=com.example.MyNoticeHandler"
    user = "consul"
    password = "senatus_populusque"
}
```

> [!WARNING]
> Reading `dataSource.url` back rebuilds the URL from every property that is set — **including the password, in clear text**. It is a configuration round-trip, not something to log or expose in diagnostics.

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

    maximumPoolSize = 10
}
val pool = HikariDataSource(config)

val session = pool.getOctaviusSession()
```

Closing a session obtained from a pool returns its connection to the pool rather than shutting it down.

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
