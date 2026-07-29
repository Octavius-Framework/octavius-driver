# Driver Configuration Options (OctaviusProperties)

Configuration properties in the Octavius driver are strongly typed and represented by the `OctaviusProperties` class. 

They can be provided directly via the `OctaviusProperties` object, extracted from a standard Java `java.util.Properties` object, or parsed from the connection URL (e.g., `jdbc:octavius://host:port/database?key=value`). When parsed from a URL or Java `Properties`, the string keys are case-insensitive.

Below is the list of supported configuration fields available in the `OctaviusProperties` class:

## Connection and Authentication
* **`user`** - The database user name.
* **`password`** - The password for the user.
* **`serverName`** (or **`host`**) - The address of the database server (default: `localhost`).
* **`portNumber`** (or **`port`**) - The port the database server is listening on (default: `5432`).
* **`databaseName`** (or **`database`**) - The name of the database to connect to (default: `postgres`).

## Network and Limits
* **`loginTimeout`** - The time to wait (in seconds) for a successful login.
* **`socketTimeout`** - The time to wait (in seconds) for a socket read operation (socket timeout).
* **`maxCachedRowSize`** - The maximum size (in bytes) of a row cached in memory.
* **`notificationBufferCapacity`** - The maximum capacity of the notification buffer (default: `256`).

## SSL Configuration
* **`ssl`** - Whether to use an encrypted connection (`true`/`false`).
* **`sslmode`** - The SSL operating mode (based on `SslMode` values).
* **`sslrootcert`** - The path to the root certificate (CA).
* **`sslcert`** - The path to the client SSL certificate.
* **`sslkey`** - The path to the client private key.
* **`sslpassword`** - The password to decrypt the client private key.

## Additional Properties
Any other properties provided in the configuration that do not match the standard options listed above are treated as **additional properties** (`additionalProperties`).

These properties are primarily forwarded directly to the database during the connection initialization (Startup Message).

**Character Encoding:** By default, the driver hardcodes the **UTF-8** character encoding and passes it to the database as an additional property. It is assumed that this is the only supported and expected encoding—there is no need or plan to support sending any other format, and this behavior will always remain as UTF-8.

## Programmatic Usage in Kotlin

Because standard configuration options in `OctaviusProperties` are exposed as strongly typed properties, you can configure the connection programmatically in Kotlin in a clean and type-safe way, relying on string-based keys only for custom parameters:

```kotlin
import io.github.octaviusframework.driver.properties.OctaviusProperties
import io.github.octaviusframework.driver.jdbc.OctaviusConnectionFactory
import io.github.octaviusframework.driver.ssl.SslMode

val properties = OctaviusProperties().apply {
    serverName = "localhost"
    portNumber = 5432
    databaseName = "my_database"
    user = "postgres"
    password = "secret_password"
    
    loginTimeout = 10
    notificationBufferCapacity = 512
    
    ssl = true
    sslmode = SslMode.REQUIRE
    
    // Any other custom parameter
    additionalProperties["application_name"] = "MyOctaviusApp"
}

// Pass directly to the factory
val connection = OctaviusConnectionFactory.createConnection("jdbc:octavius:", properties)
```
