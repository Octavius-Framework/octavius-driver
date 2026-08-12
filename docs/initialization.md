# Session Initialization and Configuration

Octavius provides three distinct ways to initialize an `OctaviusSession`. Depending on your use case, you can use standard JDBC approaches or leverage native typed functions.

Under the hood, all connections are configured using the strongly-typed `OctaviusProperties` class. The native `getOctaviusSession` functions and the `OctaviusDataSource` allow you to pass these typed properties directly, whereas `DriverManager` relies on parsing string-based parameters from a JDBC URL.

## 1. Using `DriverManager`

The driver is automatically registered with `java.sql.DriverManager`. You configure the connection via a JDBC URL or by passing a `java.util.Properties` object. Keys coming from a URL or `Properties` object are matched case-insensitively against `OctaviusProperties`.

```kotlin
import java.sql.DriverManager
import io.github.octaviusframework.driver.jdbc.getOctaviusSession

// Using purely a JDBC URL with string parameters
val url = "jdbc:octavius://localhost:5432/res_publica?user=consul&password=senatus_populusque"
val connection = DriverManager.getConnection(url)

// Obtain native Octavius session via extension function
val session = connection.getOctaviusSession()
```

## 2. Using `getOctaviusSession` Functions

If you prefer a purely typed approach without connection pooling, you can construct an `OctaviusProperties` object and pass it directly to the native `getOctaviusSession` function.

Because the standard fields are strongly typed properties rather than string keys, configuring a connection in code stays type-safe — string keys are reserved for the genuinely custom, driver-unaware parameters:

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
    ssl = true
    sslmode = SslMode.REQUIRE
    
    // Any custom, driver-unaware parameter
    additionalProperties["application_name"] = "LegioXIII-App"
}

// Create session directly
val session = getOctaviusSession("jdbc:octavius:", properties)
```

## 3. Using `DataSource`

You can also use the native `OctaviusDataSource` (`io.github.octaviusframework.driver.jdbc.OctaviusDataSource`), which bypasses the `DriverManager` overhead and allows you to set strongly-typed properties directly. 

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

// Obtain native Octavius session
val session = dataSource.getOctaviusSession()
```

---

## Adding HikariCP Connection Pooling

Octavius Driver is fully compatible with connection pools like [HikariCP](https://github.com/brettwooldridge/HikariCP). Although Octavius is a Kotlin-first driver, it implements standard `java.sql.Connection` specifically to slot smoothly into modern connection pools.

You can layer HikariCP on top of either the `DriverManager` (URL-based) or `DataSource` approach.

### Via JDBC URL (DriverManager)

```kotlin
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.github.octaviusframework.driver.jdbc.getOctaviusSession

fun configureHikari(): HikariDataSource {
    val config = HikariConfig().apply {
        jdbcUrl = "jdbc:octavius://localhost:5432/res_publica"
        username = "consul"
        password = "senatus_populusque"
        
        maximumPoolSize = 10
    }
    return HikariDataSource(config)
}

val pool = configureHikari()
val session = pool.getOctaviusSession() // borrows a session
```

### Via OctaviusDataSource

```kotlin
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.github.octaviusframework.driver.jdbc.getOctaviusSession

fun configureHikariWithDataSource(): HikariDataSource {
    val config = HikariConfig().apply {
        dataSourceClassName = "io.github.octaviusframework.driver.jdbc.OctaviusDataSource"
        
        addDataSourceProperty("serverName", "localhost")
        addDataSourceProperty("portNumber", "5432")
        addDataSourceProperty("databaseName", "res_publica")
        addDataSourceProperty("user", "consul")
        addDataSourceProperty("password", "senatus_populusque")
        
        maximumPoolSize = 10
    }
    return HikariDataSource(config)
}

val pool = configureHikariWithDataSource()
val session = pool.getOctaviusSession() // borrows a session
```

---

## Available Configuration Options

When configuring `OctaviusProperties` (or passing parameters via URL / DataSource properties), the following fields are supported:

### Connection and Authentication
* **`user`** — database username.
* **`password`** — password for that user.
* **`serverName`** (or **`host`**) — address of the database server (default: `localhost`).
* **`portNumber`** (or **`port`**) — port the server listens on (default: `5432`).
* **`databaseName`** (or **`database`**) — database to connect to (default: `postgres`).

### Network and Limits
* **`loginTimeout`** — seconds to wait for a successful login.
* **`socketTimeout`** — seconds to wait on a socket read before timing out.
* **`maxCachedRowSize`** — maximum size, in bytes, of a row kept in memory.
* **`notificationBufferCapacity`** — capacity of the LISTEN/NOTIFY buffer (default: `256`).
* **`noticeHandler`** — fully-qualified class name of a custom `NoticeHandler` implementation to intercept database notices (warnings, infos, etc.). If the provided type is a Kotlin `object`, its singleton instance is reused across all connections. Otherwise, a new instance is created via the empty constructor for each new connection.
* **`initialParameterWriterCapacity`** — initial capacity in bytes of the per-connection buffer for serialized query parameters (default: `1024`).
* **`maxParameterWriterCapacity`** — maximum capacity in bytes of the per-connection parameter buffer. If exceeded, the buffer shrinks back to its initial capacity after query execution (default: `65536`).

### SSL Configuration
* **`ssl`** — whether to encrypt the connection (`true`/`false`).
* **`sslmode`** — SSL operating mode (see `SslMode`).
* **`sslrootcert`** — path to the root CA certificate.
* **`sslcert`** — path to the client certificate.
* **`sslkey`** — path to the client private key.
* **`sslpassword`** — password protecting the client private key.

*Note: Any unrecognized property is forwarded as an additional property to the server in the connection's Startup Message.*
**Character encoding** is the one exception you can't override: the driver always declares UTF-8 to the server. There's no plan to support anything else, so it isn't exposed as a setting.
