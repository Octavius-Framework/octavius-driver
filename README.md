# Octavius JDBC Driver

A native, high-performance, and lightweight PostgreSQL database driver for Kotlin/Java, implementing the standard JDBC interfaces while communicating directly with PostgreSQL via the Wire Protocol v3.

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


