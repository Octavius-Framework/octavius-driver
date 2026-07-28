# Driver Configuration Options (Properties)

Configuration properties in the Octavius driver can be provided either in the connection URL (e.g., `jdbc:octavius://host:port/database?key=value`) or via a `Properties` object.

Below is the list of supported configuration options:

## Connection and Authentication
* **`user`** - The database user name.
* **`password`** - The password for the user.
* **`servername`** (or **`host`**) - The address of the database server (default: `localhost`).
* **`portnumber`** (or **`port`**) - The port the database server is listening on (default: `5432`).
* **`databasename`** (or **`database`**) - The name of the database to connect to (default: `postgres`).

## Network and Limits
* **`logintimeout`** - The time to wait (in seconds) for a successful login.
* **`sockettimeout`** - The time to wait (in seconds) for a socket read operation (socket timeout).
* **`maxcachedrowsize`** - The maximum size (in bytes) of a row cached in memory.
* **`notificationbuffercapacity`** - The maximum capacity of the notification buffer (default: `256`).

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
