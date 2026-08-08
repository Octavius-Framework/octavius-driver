# Driver Configuration Options (OctaviusProperties)

Octavius configuration is strongly typed, centered on the `OctaviusProperties` class. You can build one directly in code, extract it from a standard `java.util.Properties` object, or let it parse itself out of a connection URL (`jdbc:octavius://host:port/database?key=value`). Keys coming from a URL or `Properties` object are matched case-insensitively.

Below is the full list of fields `OctaviusProperties` understands.

## Connection and Authentication
* **`user`** — database username.
* **`password`** — password for that user.
* **`serverName`** (or **`host`**) — address of the database server (default: `localhost`).
* **`portNumber`** (or **`port`**) — port the server listens on (default: `5432`).
* **`databaseName`** (or **`database`**) — database to connect to (default: `postgres`).

## Network and Limits
* **`loginTimeout`** — seconds to wait for a successful login.
* **`socketTimeout`** — seconds to wait on a socket read before timing out.
* **`maxCachedRowSize`** — maximum size, in bytes, of a row kept in memory.
* **`notificationBufferCapacity`** — capacity of the LISTEN/NOTIFY buffer (default: `256`).
* **`noticeHandler`** — fully-qualified class name of a custom `NoticeHandler` implementation to intercept database notices (warnings, infos, etc.). If the provided type is a Kotlin `object`, its singleton instance is reused across all connections. Otherwise, a new instance is created via the empty constructor for each new connection.

## SSL Configuration
* **`ssl`** — whether to encrypt the connection (`true`/`false`).
* **`sslmode`** — SSL operating mode (see `SslMode`).
* **`sslrootcert`** — path to the root CA certificate.
* **`sslcert`** — path to the client certificate.
* **`sslkey`** — path to the client private key.
* **`sslpassword`** — password protecting the client private key.

## Additional Properties
Anything you set that isn't one of the fields above is treated as an **additional property** and forwarded as-is to the server in the connection's Startup Message — a way to pass through parameters Octavius doesn't model explicitly.

**Character encoding** is the one exception you can't override: the driver always declares UTF-8 to the server. There's no plan to support anything else, so it isn't exposed as a setting.

## Programmatic Usage in Kotlin

Because the standard fields are strongly typed properties rather than string keys, configuring a connection in code stays type-safe — string keys are reserved for the genuinely custom, driver-unaware parameters:

```kotlin
import io.github.octaviusframework.driver.properties.OctaviusProperties
import io.github.octaviusframework.driver.jdbc.OctaviusConnectionFactory
import io.github.octaviusframework.driver.ssl.SslMode

val properties = OctaviusProperties().apply {
    serverName = "localhost"
    portNumber = 5432
    databaseName = "res_publica"
    user = "consul"
    password = "senatus_populusque"

    loginTimeout = 10
    notificationBufferCapacity = 512

    ssl = true
    sslmode = SslMode.REQUIRE

    // Any custom, driver-unaware parameter
    additionalProperties["application_name"] = "LegioXIII-App"
}

// Create session
val session = getOctaviusSession("jdbc:octavius:", properties)
```
