## Version 0.9.7 (v0.9.7)

#### Added

- Logging across the connection, transaction, COPY and statement lifecycle, documented in [Logging](docs/logging.md) with every logger name and what each level costs. the driver's own `info` stays silent apart from one line per database when the type catalog is loaded - reporting how many types were read and how long it took - and one more for each explicit `reloadTypes()`, server notices keeping their own logger name and the server's severity as before; `debug` adds a line per physical connection - carrying the backend process id that `pg_stat_activity` and the server's own log are keyed by, and the `sslmode` alongside the outcome, since `plaintext` alone does not say whether that was intended - plus transactions, savepoints, `LISTEN`/`UNLISTEN`, listener loops, and COPY transfers with their row and byte counts; `trace` adds every statement, once on its way out and again when it returns with its duration, the startup handshake in five lines, and any session parameter the server re-reports mid-session - a hand-written `SET search_path` changes which schema everything after it resolves against, and until now left no trace at all. No level ever writes a password, a SCRAM proof or a parameter value
- Every exception the driver used to swallow is now logged. `isValid()` answering `false`, a cancel request that never arrived, a large object descriptor left open and a socket that refused to close each recorded the fact nowhere, so a pool could evict a connection with the reason available nowhere in the process. A session that cannot reset its connection state during `close()` logs at `warn`, being evicted rather than returned
- `logParameterValues` connection property (default `false`), putting the values bound to a statement on its `trace` line instead of only their count, numbered to match the `$n` placeholders. Deliberately a property rather than a consequence of the level: raising a log level is an operational decision taken during an incident, often across a whole deployment, while writing the contents of your tables into a file that will be shipped and retained is a decision about data - so turning the driver up to find a slow statement does not start recording who it was about. Exposed on `OctaviusDataSource` like every other setting
- `TypeDictionary.size`, the number of types the dictionary holds

#### Changed

- **Breaking:** the block passed to `transaction.required` and `transaction.nested` is `crossinline`, so a bare `return` out of it no longer compiles - `return@required` / `return@nested` return from the block as before. A non-local return is not an exception, so it slipped past the `catch` entirely while the compiler still copied the `finally` onto its path: out of `required` it committed whatever the block had already done, reading at the call site like an abort, and out of `nested` it passed the release and the rollback alike and left the savepoint standing until the outer transaction ended. Deciding the fate of a transaction from inside the block is the one thing the restricted receiver exists to prevent, and this was the back door to it. The compiler names every call site that has to change

#### Fixed

- Reading or setting the network timeout on a connection the peer had already dropped raised a raw `java.net.SocketException`, straight out of `Connection.getNetworkTimeout` and `setNetworkTimeout` - methods JDBC declares as throwing `SQLException`, and which a pool therefore only guards against as one. Both now raise `NetworkException(CONNECTION_ERROR)` like every other socket operation, reaching a JDBC caller as `SQLExceptionWrapper`, and mark the stream broken so the next `checkClosed()` reports it
- `QueryContext` rendered parameter values straight into the exception message, so a `ByteArray` printed as an identity hash rather than its size and a large `text` or `json` parameter printed in full. It now shares one formatter with `CodecException` and the trace log, so a value reads the same wherever a diagnostic shows it
- That formatter now bounds a value **as it renders it** rather than trimming the result. Cutting `toString()` down to size materialises the whole thing first, and a bulk write legitimately passes ten thousand elements as one parameter - so a failed bulk insert used to assemble megabytes of string on its way out, to keep a hundred characters of it. Collections, arrays and maps are now walked element by element against a budget and then counted (`[0, 1, 2, ... +9990 more]`), Kotlin's primitive arrays render their contents instead of an identity hash, and a nested `ByteArray` is named like a top-level one. A class the driver does not know still renders through its own `toString()`, which nothing here can bound
- `isValid()` raised instead of answering `false` when the connection died between its own closed check and the probe - reading the timeout it has to save and restore is itself a socket operation, and that read sat outside the guarded region
- The parameter buffer sized itself in `Int` arithmetic, which a large enough parameter carries past `Int.MAX_VALUE`. A buffer already over a gigabyte that had to grow again doubled itself into a negative capacity and from there to `0`, where the growth loop spun forever holding the connection and the thread that borrowed it; a single value big enough to overflow `position + needed` on its own skipped growing altogether and came out as an out-of-bounds copy. Sizes are computed in `Long` now, and a buffer that would have to outgrow the largest array a JVM hands out raises `InvalidOperationException(INVALID_ARGUMENT)` naming the size it wanted.
- `transaction.required` and `transaction.nested` lost the exception that sent them into the rollback whenever the cleanup failed in its turn - the rollback of a connection that had just died raised over the top of it, and a throw from restoring auto-commit replaced it outright, so the one diagnostic worth keeping was the one that went missing on the failure path it was most likely to be on. Both now attach the cleanup failure to the original as a suppressed exception. On the way out of a scope that **committed** there is no original to attach to, and a failed restore is logged at `warn` instead of raised: the work is in the database, and an exception over the top of a commit that succeeded reads as a failed transaction and invites a retry that writes everything twice. What it commits there is the empty transaction the successful `commit()` left open behind it, which the server has no reason to refuse, so what fails is the socket - and that is already on the record, `checkClosed()` raising `NetworkException` the next time anything touches the session. The `catch` stays on `Throwable` deliberately: restoring auto-commit is a `COMMIT` on the wire, so a path that let an `Error` past the rollback would commit the work the block had done before it failed

## Version 0.9.6 (v0.9.6)

#### Added

- SCRAM-SHA-256-PLUS with channel binding - the client proof covers a hash of the certificate the server presented, so an intermediary terminating TLS with a certificate of its own produces a proof the real server rejects. New `ChannelBinding` enum (`DISABLE`, `PREFER`, `REQUIRE`) and a `channelBinding` property defaulting to `PREFER`
- `cancelSignalTimeout` connection property (default `10` seconds), covering both the connect and the reads of a cancel request - it travels on a connection of its own, so it gets a budget separate from `loginTimeout` and `socketTimeout`
- Every `OctaviusProperties` setting is now exposed on `OctaviusDataSource`, alongside a generic `setProperty(name, value)`
- Documentation index, plus pages for bulk writes, concurrency and virtual threads, arrays/ranges/JSON and composites with reflective mapping; reworked README, `octavius-vs-jdbc`, `spring-integration`, `performance` and `queries`
- `CHANGELOG.md` and `LICENSE` in the repository
- KDoc published for release and snapshot versions side by side, behind a landing page
- KDoc on the parts of the public surface that had none
- Benchmarks measuring what reflection costs against a hand-written converter
- Tests for channel binding, local SSL test infrastructure driven by `scripts/ssl-test-server.ps1`, HikariCP initialization, greedy-converter diagnostics, reflective missing-value handling and parameter type mismatches

#### Changed

- `fetchField` treats a missing row and a `NULL` value alike, and the nullability of `T` decides whether either is allowed** - `fetchField<String>()` used to return `null` on an empty result while raising `MappingException(REQUIRED_ATTRIBUTE_MISSING)` for a row carrying `NULL`; both now raise it, and `fetchField<String?>()` returns `null` for both.
- `fetchField` returns `T` instead of `T?`, following from the above - a non-nullable `T` can no longer come back null, so nullability lives in `T` alone and survives the round trip, matching how `Row.get<T>()`, `fetchFields<T>` and `fetchFieldStrict<T>` have always read it.
- `PgNotice` is a `data class` of named properties, carrying every field a notice can hold mirroring ServerErrorMessage.
- `ServerErrorMessage.severity` and `code` are non-null, and a `localizedSeverity` joins them - the protocol requires both in every `ErrorResponse`.
- `kotlinx.coroutines.core`, `kotlinx.serialization.json` and `kotlinx.datetime` are `api` dependencies.
- `Row.get` is a member function instead of an extension, so nothing has to be imported at the call site
- A cancel request's socket performs the same SSL handshake as the connection it cancels, instead of connecting in the clear
- `ResultMapper` compares what a converter produced against the type that was requested - a converter whose `canConvert` accepts more than it can produce now raises `MappingException(CONVERSION_ERROR)` naming it, instead of a `ClassCastException` in the caller's own frame with nothing in the stack to identify it
- The `unknown` pseudo-type is loaded into the type catalog.
- A default value in a data class constructor covers an **absent** column or attribute only - SQL `NULL` is a value, reaching a nullable property as `null` and raising `REQUIRED_ATTRIBUTE_MISSING` for a non-nullable one whether a default is declared. Previously a default silently replaced any `NULL`
- `registerAutoComposite` rejects a class that is not a data class with `InvalidOperationException(INVALID_ARGUMENT)`.
- `ReflectionCompositeParameterConverter` no longer claims an unregistered data class when the expected OID happens to name a composite
- `PgNotice` carries `processId`. The id comes from the `BackendKeyData` of the startup handshake, so a notice raised before that handshake finished reports `-1`; it is unrelated to `PgNotification.processId`
- A parameter that reaches the end of the converter chain unclaimed is now checked against the codec bound to its target OID, where one is known. A class that codec cannot encode raises `MappingException(NO_CONVERTER_FOUND)` naming both sides, with the attribute or element index in `path`, matching how the read direction reports the same mistake - previously an unregistered nested data class surfaced as a bare `ClassCastException` and a width mismatch such as an `Int` bound for `int8` as `CodecException(ENCODING)` with no path

#### Fixed

- A query-string value containing `=` is no longer dropped from the JDBC URL - only the first `=` separates the key from the value, so a password or an `options=-c search_path=curia` string arrives intact instead of being silently ignored
- A JDBC URL now overrides the `Properties` it is parsed against, consistently - the host and port were read from the URL's authority only if `info` had left them unset, so `getConnection("jdbc:octavius://prod:5432/db", info)` silently connected to whatever host `info` carried, while the database name had the opposite fault and replaced the one `info` supplied even when the URL named none. The URL now wins where it states something and leaves `info` alone where it does not. Parsing no longer fills in defaults either - an unstated host, port or database stays `null` until the connection factory resolves it
- KDoc that described something the code no longer does

## Version 0.9.5 (v0.9.5)

#### Added

- Documentation for queries, COPY, large objects, performance, Spring integration and Octavius vs legacy JDBC; reworked README
- KDoc for exceptions and geometric types
- `UncategorizedDatabaseException` for SQLSTATEs no routing branch claims
- `RegistryKey` - type registries are now cached per host + port + database instead of per full URL, so credentials and SSL settings no longer fragment the cache
- `ServerErrorMessage` - data class carrying every field of the server's `ErrorResponse`, attached to all exceptions
- `ReflectionMappingUtils`
- `UNEXPECTED_RESULT`, `COPY_IN_PROGRESS` and `EXECUTION_IN_PROGRESS` reasons for `InvalidOperationException`
- Benchmarks for `reWriteBatchedInserts`
- Tests for pooled session cleanup, Spring exception translation and `isValid` under a concurrent query

#### Changed

- `session.types` renamed to `session.typeManager`
- `TypeManager` and `ContainerFactory` moved to `registry` package
- `ParameterSerializer` and `QueryExecutor` moved to `execution` package
- `QueryContext` moved to `query` package, `ServerErrorMessage` to `message`, `ExceptionTranslator` to `message.translator`
- `Range` and `MultiRange` moved to `type.range`, `PgInterval` and `DateTimeExtensions` to `type.datetime`
- `ReflectionCompositeCache` renamed to `ReflectionCache` and moved to `util.reflection` - it deserializes `Row` as well, not just composites
- `OctaviusException` is now an abstract class
- `getDefaultTypeName` takes `sourceClass` alongside the context
- `OctaviusTemplate.execute` takes the session as an implicit receiver
- Converters are now internal objects
- Closing a session resets connection state - `UNLISTEN *` when it subscribed, aborting an unfinished `COPY`, rolling back a transaction left open by hand-written SQL
- Using a session from inside a `forEach` block or a converter now fails fast with `EXECUTION_IN_PROGRESS` instead of desynchronizing the connection
- `isValid` no longer shortens the deadline of a query running on another thread, never relaxes a configured socket timeout, and reports misuse instead of answering `false`
- `OctaviusTemplate` translates failures to obtain a connection, and the Spring translator recognizes an `OctaviusException` anywhere in the cause chain - a pool that could not connect now surfaces as `OctaviusDataAccessException` wrapping `InitializationException` instead of a generic pool timeout
- `Multirange`/`Range` parameter converters resolve the type OID for auto-registered composites
- Auto-registered composites no longer resolve their OID twice
- `toUrl` no longer renders the password, and `additionalProperties` is copied before being mutated for the startup message
- `PgSslUpgrader` merged into `SslNegotiator`
- `Authenticator` is now a singleton
- `hikari` module renamed to `hikari-integration-tests`

#### Removed
- `UNSUPPORTED_ISOLATION_LEVEL`, `INVALID_TIMEOUT` and `NULL_SQL` reasons - replaced by `INVALID_ARGUMENT`
- `CompositeRegistration`
- `PgSslUpgrader`
- Naming conventions from `ReflectionCache`

## Version 0.9.4 (v0.9.4)

#### Added

- Codecs for `xml`, `bit`/`varbit` (`java.util.BitSet`), network types (`inet`, `cidr`, `macaddr`, `macaddr8` as `String`) and geometric types (`point`, `line`, `lseg`, `box`, `path`, `polygon`, `circle`)
- `PgPoint`, `PgLine`, `PgLseg`, `PgBox`, `PgPath`, `PgPolygon`, `PgCircle` data classes
- `XML` and `XML_ARRAY` in `PgStandardType`
- `NegotiateProtocolVersionMessage` - a server that does not speak protocol 3.2 now fails with `InitializationException(UNSUPPORTED_SERVER_VERSION)` naming the highest minor version it supports, instead of an unrecognized message
- `benchmarks` module with JMH benchmarks against pgjdbc - simple types, structural data, arrays and inserts
- Documentation for quickstart, session initialization, Spring integration and performance
- Spring Boot example app in `examples/spring-app`
- Codec, SSL and unsupported-version integration tests, plus CI jobs running them against an `ssl=on` server and PostgreSQL 17

#### Changed

- Required Java version lowered from 25 to 21
- `CopyIn` and `CopyOut` are now classes - `CopyInImpl` and `CopyOutImpl` are gone
- `CodecException` carries the type `name` and `schema` instead of `pgType`, and codecs now fill them in
- `NumericCodec` throws on `Infinity` and `-Infinity` - `BigDecimal` cannot represent them, so they were silently decoded as garbage
- `driver-spring-integration` exposes `driver` and `spring-boot-starter-jdbc` as `api` dependencies
- Only `driver` and `driver-spring-integration` are published
- Exception details in `PgComposite` are in English
- README states the requirements - Java 21+ and PostgreSQL 18+

#### Removed

- `CopyIn` and `CopyOut` interfaces
- `transactionStepIndex` and `withTransactionStep` from `QueryContext`
- `docs/properties.md` - merged into `initialization.md`

## Version 0.9.3 (v0.9.3)

#### Added

- `ConcurrencyException` with reasons `LOCK_NOT_AVAILABLE`, `DEADLOCK_DETECTED`, `SERIALIZATION_FAILURE`, `UNKNOWN`
- `ExecutionAbortedException` with reasons `TRANSACTION_TIMEOUT` and `QUERY_CANCELED`
- `TransactionIsolationLevel` enum
- `PgName` annotation - replaces `MapKey`
- Non-reified `TypeManager.registerAutoComposite(KClass, ...)`, `TypeManager.registerEnum(KClass, ...)` and `ConverterRegistry.registerAutoCompositeType(kClass, ...)` - registration works when the class is only known at runtime
- `DeserializationContext.convert` overload taking a source OID instead of a `PgType`
- `JsonObject`, `JsonArray` and `JsonPrimitive` are now accepted as result types for `json`/`jsonb`, not just `JsonElement`
- Documentation for COPY and large objects
- Tests for Spring auto-configuration and bean back-off, and for transaction isolation levels

#### Changed

- `session.transactionIsolationLevel` is a `TransactionIsolationLevel` instead of a `java.sql.Connection` int constant
- `25P03`/`25P04` and `57014` now translate to `ExecutionAbortedException`, `40001`/`40P01`/`55P03` to `ConcurrencyException`
- `TypeManager.registry` is now private - `typeDictionary` and `converterRegistry` are the public entry points
- `registerAutoCompositeType` is no longer `inline` with a `reified` parameter

#### Fixed

- `slf4j-api` added as an `implementation` dependency - `KotlinLogging` no longer throws `ClassNotFoundException` at runtime

#### Removed

- `TransactionException` and `TransactionExceptionReason`
- `MapKey` annotation
- `typeRegistry` from `PgComposite` and `PgRange`, along with `PgComposite.getAttributeType`
- `typeRegistry` from `OctaviusQuery`

## Version 0.9.2 (v0.9.2)

#### Added

- `initialParameterWriterCapacity` and `maxParameterWriterCapacity` connection properties - initial and maximum size in bytes of the per-connection parameter buffer (defaults `1024` and `65536`), documented in `docs/properties.md`

#### Changed

- The buffer for serialized query parameters is allocated once per connection and reused between queries instead of being created on every execution - it is cleared before each use and shrinks back to the initial capacity when it grows past the maximum
- `QueryExecutor` and `ParameterSerializer.serializeAll` take `Array<out Any?>` instead of `List<Any?>`, and `serializeAll` writes into a passed-in writer instead of returning a new one - no `toList()` copy per call, and named parameters are collected straight into an array
- The connection socket sets `tcpNoDelay` - no Nagle delay on small protocol messages
- README wording

## Version 0.9.1 (v0.9.1)

#### Added

- COPY protocol support - `session.copy` (`CopyManager`) with `copyIn`/`copyOut`, streaming `CopyIn`/`CopyOut` handles and `InputStream`/`OutputStream` overloads
- Large object support - `session.largeObjects` (`LargeObjectManager`) with `create`, `open` and `unlink`, and `LargeObject` offering `read`, `write`, `seek`, `tell`, `truncate`, `inputStream()` and `outputStream()`
- `NoticeHandler` and `PgNotice` - server notices can be intercepted by a custom handler set through the `noticeHandler` connection property
- Documentation for queries, transactions, LISTEN/NOTIFY, exceptions and Octavius vs JDBC, plus KDoc across the public API
- Dokka API documentation published to GitHub Pages
- Publication to Maven Central - POM metadata, signing and a release workflow
- Tests for COPY, large objects, notice handling, session lifecycle and Spring exception translation

#### Changed

- Every `*ExceptionMessage` enum is now `*ExceptionReason`, and the `messageEnum` property on exceptions is now `reason`
- `InvalidOperationExceptionReason.STATEMENT_CLOSED` renamed to `OBJECT_CLOSED` - it also covers closed large objects
- `DataException` and `ConstraintViolationException` carry `dbMessage`, `details` and `where` taken from the server's `ErrorResponse` instead of one concatenated string
- `OctaviusException.toString()` reformatted - message, SQLSTATE, details, query context and cause in fixed sections
- `ParameterConverter.getDefaultOid` replaced by `getDefaultTypeName` returning a `QualifiedName` - declaring a default type no longer needs an OID lookup
- Spring classes moved from `io.github.octaviusframework.spring` to `io.github.octaviusframework.driver.spring`
- Spring auto-configuration builds a `JdbcTransactionManager` with `OctaviusExceptionTranslator` instead of a plain `DataSourceTransactionManager`
- `PgByteWriter` moved from the `codec` package to `io`
- Collections are flattened and converted in a single pass, and serialized parameters are written straight to the output stream without an intermediate array copy
- A `DataRow` arriving before `RowDescription` throws `InvalidOperationException(UNEXPECTED_RESULT)` instead of `IllegalStateException` (impossible state in normal PostgreSQL communication)
- README reworked and docs reworded

#### Fixed

- `SQLExceptionWrapper` no longer leaks through the Spring integration - `OctaviusDataAccessException` always wraps the original `OctaviusException`, and a `SQLException` that cannot be translated becomes Spring's `UncategorizedSQLException`

#### Removed

- `OctaviusInternalException` - unreachable paths use `error()` now

## Version 0.8.9 (v0.8.9)

#### Added

- `forEachRow`, `forEachObject` and `forEachField` on native and named-parameter queries - rows are streamed from a portal in batches of `fetchSize` instead of being materialized into a list
- `ContainerFactory`, reachable as `typeManager.containers` - `createComposite`, `createRange`, `createEmptyRange` and `createMultirange` moved out of `TypeManager`
- `TypeDictionary` and `CodecDictionary` - immutable type and codec lookups split out of `TypeRegistry`, with `getRangeType(elementOid)` and `getMultirangeType(rangeOid)` alongside the array lookup
- `ConverterRegistry` - result converters, parameter converters and composite registrations in one place, exposed as `typeManager.converterRegistry`
- `CodecException` - encoding and decoding failures report the action, the offending value, the PostgreSQL type and the Kotlin class
- `rangeOf` and `multiRangeOf` helper functions
- `supportedClass` and `getDefaultOid` in `ParameterConverter`
- `path` on `MappingException` - nested conversions record where they failed
- `Flush` and `Close` frontend messages
- Documentation for functions and procedures
- Test suites for codec, mapping, type, network, data, constraint, permission, routine, statement and transaction exceptions, plus complex data and record integration tests

#### Changed

- `TypeRegistry` is now just a holder for the dictionaries and the converter registry
- `ParameterConverter.canConvert` takes a `KClass` and a `SerializationContext` instead of the value and a `TypeManager`; `convert` gets the typed source and reads the `TypeManager` from the context
- `ResultConverter.canConvert` likewise takes a `KClass` and the `DeserializationContext`
- `Range<T>` and `MultiRange<T>` require `T : Any` and carry `elementClass`
- `PgArray` and `PgRecord` no longer hold a `TypeRegistry`
- Deserializing into `Any` resolves enums, collections and registered composites instead of failing
- Exception messages are prefixed with the exception type - e.g. `TYPE_EXCEPTION:MISSING_CODEC`
- `MappingException` reasons trimmed to `NO_CONVERTER_FOUND`, `COLUMN_NOT_FOUND`, `CONVERSION_ERROR` and `REQUIRED_ATTRIBUTE_MISSING`
- `TypeException` reasons trimmed to `TYPE_NOT_FOUND`, `NOT_A_CONTAINER`, `MISSING_CODEC`, `ANONYMOUS_RECORD_NOT_SUPPORTED` and `NESTED_PGTYPED_NOT_ALLOWED`
- `TransactionException` reasons `ROLLBACK` and `INVALID_TRANSACTION_STATE` replaced by `DEADLOCK_DETECTED` and `SERIALIZATION_FAILURE`
- `ParameterMapper` and `ResultMapper` wrap anything a converter throws in a `MappingException` carrying the path
- Built-in converters throw plain Kotlin exceptions for their own validation - the mappers translate them
- Array parameters resolve their element type from `PgContainer` values as well

#### Fixed

- A codec or type exception thrown while reading a row no longer desynchronizes the protocol - the error is held until the server finishes the exchange
- `getOctaviusSession` merges properties parsed from the URL with the explicitly passed `OctaviusProperties` instead of dropping the URL ones
- Reading an array containing nulls into a collection of a non-nullable type throws instead of returning nulls

#### Removed

- `getCodecByOid`, `getCodecByClass`, `getOidForCodec` and `resolveOid` from `TypeRegistry`
- Container factory methods from `TypeManager`
- `set` operator on `PgArray`

## Version 0.8.1 (v0.8.1)

#### Added

- `fetchObjectStrict` on `NativeQuery` and `NamedParameterQuery` - the object counterpart of `fetchRowStrict`/`fetchFieldStrict`, throws unless the result is exactly one row

#### Changed

- Terminal query methods renamed to one `fetch<What>` / `fetch<What>Strict` scheme on both `NativeQuery` and `NamedParameterQuery`:
    - `fetchAll` → `fetchRows`
    - `fetchOne` → `fetchRow`
    - `fetchOneStrict` → `fetchRowStrict`
    - `fetchListOf` → `fetchObjects`
    - `fetchSingleOf` → `fetchObject`
    - `fetchColumn` → `fetchFields`
    - `fetchField` and `fetchFieldStrict` keep their names
- `fetchObject` returns `T?` and no longer throws on an empty result - `fetchObjectStrict` covers the old behaviour
- Result-size exception messages unified across all terminal methods

## Version 0.8.0 (v0.8.0)

#### Added

- A full exception hierarchy, one class per file - `DataException`, `ConstraintViolationException`, `StatementException`, `TransactionException`, `InitializationException`, `InvalidOperationException`, `PermissionDeniedException`, `RoutineExecutionException`, `DatabaseSystemException`, `MappingException`, `TypeException` and `OctaviusInternalException`
- `ExceptionTranslator` routes SQLSTATE classes 08, 0A/21/3D/3F, 22, 23, 25, 28, 40, 42, 53/54/55/57/58/XX to those exceptions with a specific reason instead of returning a bare `OctaviusException` with a text message
- `ConstraintViolationException` carries `schema`, `table`, `column` and `constraint` from the server's `ErrorResponse`; `StatementException` carries the error position
- `MappingException` - deserialization failures were plain `IllegalArgumentException` before
- `INCORRECT_RESULT_SIZE` and `MISSING_NAMED_PARAMETER` reasons on `StatementException`, thrown inside the query context so the failure reports the SQL and parameters
- `UNRESOLVED_OID` constant and the `Int.isKnownOid` extension
- `containerOid` on `PgContainer`
- Integration tests for constraint violations and statement exceptions, plus more parameter serializer tests

#### Changed

- `OctaviusTypeException` renamed to `TypeException`, `BadStatementException` to `StatementException`
- `AuthException` and the connection-level parts of `OctaviusJdbcException` (`INVALID_URL`, `SSL_ERROR`, `UNSUPPORTED_SERVER_VERSION`) merged into `InitializationException`
- The remaining `OctaviusJdbcException` reasons and `UnsupportedFeatureException` merged into `InvalidOperationException`
- `ConstraintViolationExceptionMessage` renamed to `ConstraintViolationExceptionReason`, same for `StatementException` and `TransactionException`
- Result-size checks throw `StatementException` instead of `check()` failing with `IllegalStateException`, and they run inside the query context
- `expectedOid` in the parameter converters is a plain `Int` using `UNRESOLVED_OID` instead of `Int?`
- Nested `PgTyped` throws `TypeException(NESTED_PGTYPED_NOT_ALLOWED)` instead of being silently wrapped
- `PgTyped` and `withPgType` moved to their own file; `PgStandardType.kt` renamed to `Oid.kt`
- `TypeRegistryLoader.load` no longer takes a search path, and the registry uses a single lock instead of a separate load lock

#### Fixed

- A codec that is not bound to an OID resolves it by type name instead of falling back to `UNRESOLVED_OID`
- `isClosed()` and the internal close check detect a broken stream, so a dead connection reports itself as closed instead of handing out a usable one (matters for pool eviction)
- `fetchSingleOf` throws when the query returns no rows and the target type is not nullable

#### Removed

- `Exceptions.kt` monolith, `AuthException` and `UnsupportedFeatureException`

## Version 0.7.0 (v0.7.0)

#### Added

- `NetworkException` with reasons `CONNECTION_ERROR`, `CONNECTION_TIMEOUT`, `CONNECTION_CLOSED`, `CONNECTION_CLOSED_BY_PEER` and `CONNECTION_ABORTED` - IO failures no longer surface as raw `IOException`/`SocketTimeoutException`
- `fetchOneStrict` and `fetchFieldStrict` on `NativeQuery` and `NamedParameterQuery`
- `notificationBufferCapacity` connection property (default `256`)
- Server notices are logged under a dedicated logger name, `io.github.octaviusframework.driver.Notice`, mapped by severity (`WARNING` → warn, `NOTICE`/`INFO`/`LOG` → info, `DEBUG` → debug), so they can be filtered on their own in logback
- `docs/properties.md`, plus KDoc for `PgStream` and the wire-protocol messages
- `NotificationManagerTest`

#### Changed

- `fetchOne` returns `Row?` - the old strict behaviour lives in `fetchOneStrict`, and `fetchOneOrNull` is gone
- `fetchField` returns `T?`, with `fetchFieldStrict` for the strict variant
- `fetchSingleOfOrNull` folded into `fetchSingleOf`, which no longer requires `T : Any`
- Object and single-column methods deserialize row by row inside the query context instead of fetching a `Row` first, so a mapping failure reports the SQL and parameters
- `PgStream` and `SslNegotiator` are internal, and `OctaviusConnection`, `QueryExecutor`, `NativeQuery`, `NamedParameterQuery` and `OctaviusQuery` only have internal constructors
- `OctaviusTemplate.execute` hands the block an `OctaviusSessionOperations` instead of an `OctaviusSession` - transaction management stays with Spring
- The notification listener rethrows a genuine network failure instead of quietly ending the loop; it exits silently only when the connection was closed or the coroutine cancelled
- Polling for notifications returns `null` on a socket timeout instead of throwing
- `TypeManager`, `NotificationManager` and `TransactionManager` on a session, and the cached attribute lists on `PgType.Composite`, are built eagerly instead of `by lazy`
- `additionalProperties` are sorted when rendering a URL, so `toUrl()` is stable

#### Fixed

- A blank identifier is quoted like any other name instead of being turned into an empty string

## Version 0.6.2 (v0.6.2)

#### Added

- `maxCachedRowSize` connection property (default `65536` bytes) - a row bigger than this gets its own array instead of the shared buffer, so a single huge value does not pin memory

#### Changed

- `DataRow` deserialization reuses per-connection buffers for the row bytes and the column offset/length arrays instead of allocating three arrays per row - the arrays in `DataRowMessage` are valid only until the next row is read, which is safe because `Row` parses every value eagerly
- `PgInputStream.readFully` takes an optional length, so it can fill part of a larger buffer

## Version 0.6.1 (v0.6.1)

#### Added

- `Range<T>` and `MultiRange<T>` - plain Kotlin views of PostgreSQL ranges and multiranges, with result and parameter converters, so custom ranges subtypes can be mapped by other converters instead of only by codecs
- `PgInterval` (`Finite`, `Infinity`, `MinusInfinity`) and its codec for the `interval` type
- Conversions between `PgInterval` and kotlinx-datetime types - `toDateTimePeriod`, `toPgInterval`, `toDurationExact`, `toPgIntervalExact`, `toDurationApproximate`, `toPgIntervalApproximate`
- `DateTimePeriod.INFINITY` and `DateTimePeriod.MINUS_INFINITY` markers for PostgreSQL's infinite intervals
- Range converter, range integration and interval integration tests

#### Changed

- `PgInputStream` and `PgOutputStream` use their own 8 KB buffer instead of `BufferedInputStream`/`BufferedOutputStream` and `DataInputStream`
- Registering a codec for a sealed class also maps its subclasses, so `PgInterval.Finite` and the infinity objects resolve to the interval codec
- `QueryExecutor` locks on the shared `PgStream.lock` instead of its own lock, and the notification listener takes the same lock while polling - a listener and a query can no longer read from the socket at once
- `NumericCodec` writes into the `PgByteWriter` directly instead of building an intermediate byte array
- `OctaviusTemplate` releases the connection with `DataSourceUtils.releaseConnection`
- `docs/type-system.md` covers ranges and intervals

#### Fixed

- `getOctaviusSession(url, user, password)` parses the properties from the URL instead of starting from an empty set, so settings carried in the URL are no longer dropped

#### Removed

- Unused `toByteArrayBE` helpers and `setShortBE` from `ByteExtensions`

## Version 0.6.0 (v0.6.0)

#### Added

- `driver-spring-integration` module - `OctaviusTemplate`, `OctaviusSpringAutoConfiguration` (registers the template and a `DataSourceTransactionManager` with nested transactions enabled), `OctaviusExceptionTranslator` and `OctaviusDataAccessException`
- `OctaviusProperties` - typed connection settings (`serverName`, `portNumber`, `databaseName`, `user`, `password`, `loginTimeout`, `socketTimeout`, SSL settings) with URL parsing, merging and `toUrl()`; unrecognized keys land in `additionalProperties` and are sent as startup parameters
- `OctaviusConnectionFactory` - connection creation extracted from `OctaviusDriver` and shared by the driver, the data source and `getOctaviusSession`
- JDBC savepoints on `OctaviusConnection` - `setSavepoint()`, `setSavepoint(name)`, `rollback(savepoint)` and `releaseSavepoint`, with a public `OctaviusSavepoint` interface
- `Connection.getOctaviusSession()`
- GitHub Actions workflow running the test suite
- Data source initialization, Hikari initialization and Spring integration tests

#### Changed

- `OctaviusDataSource` is backed by `OctaviusProperties` and exposes bean-style `serverName`, `portNumber` and `databaseName` setters, so a pool can configure it without a URL
- Property names follow the JDBC bean convention - `host` → `serverName`, `port` → `portNumber`, `database` → `databaseName` - and `sslmode` is an `SslMode` enum instead of a string
- `OctaviusProperties` holds its own fields instead of wrapping `java.util.Properties`
- Property values are URL-encoded when rendering a URL and decoded when parsing one
- `OctaviusSessionImpl` unwraps the Octavius connection itself instead of taking it as a second constructor argument
- `OctaviusSavepoint` split into a public interface and an internal `OctaviusSavepointImpl` implementing `java.sql.Savepoint`
- `isClosedFlag` on the connection is `@Volatile`
- Version catalogs split - `gradle/spring.versions.toml` and `gradle/hikari.versions.toml` alongside `libs.versions.toml`, and the Hikari tests moved into the `hikari` module

## Version 0.5.2 (v0.5.2)

#### Added

- `OctaviusSessionOperations` - the session API without transaction lifecycle control, so a transaction block cannot call `commit`, `rollback` or flip `autoCommit`; `OctaviusSession` extends it
- `TransactionManager.required` and `TransactionManager.nested` - `required` joins an active transaction or starts a new one, `nested` creates a savepoint when a transaction is already running
- `RowMetadata` and `FieldDescription` - the row description is parsed once per result instead of per row
- `DynamicContainerCodec` - a single codec for arrays, composites, ranges, multiranges and records, delegating to `ContainerCodec`
- `UnsupportedFeatureException` with reasons `UNSUPPORTED_ISOLATION_LEVEL`, `INVALID_TIMEOUT`, `UNWRAP_ERROR`, `FEATURE_NOT_SUPPORTED` and `NULL_SQL`
- `SQLExceptionWrapper` - the JDBC surface wraps an `OctaviusException` in a `SQLException`, so HikariCP's connection reset sees the exception type it expects
- `PortalSuspended` message handling and `maxRows` support in `QueryExecutor`
- KDoc for the session, transaction manager and connection

#### Changed

- `Row` is a class instead of an interface, and moved from the `query` package to `row` together with `FieldDescription`
- `fetchOne` and `fetchOneOrNull` run with `maxRows = 2` instead of fetching the whole result just to check the count
- `TransactionManager`'s `invoke` operator replaced by the explicit `required` and `nested` functions
- Standard codecs are named after their PostgreSQL types - `StringCodec` → `TextCodec`
- `OctaviusAuthException` renamed to `AuthException`
- Connection methods route their failures through a shared `wrapSqlException` helper

#### Fixed

- `NumericCodec` reads `sign` and `dscale` as unsigned shorts, and `NaN` throws `OctaviusTypeException(VALUE_OUT_OF_RANGE)` instead of a bare `error()`
- The SSL private key is decoded by dropping every PEM header line, so keys with headers other than `PRIVATE KEY`/`RSA PRIVATE KEY` load correctly

## Version 0.5.1 (v0.5.1)

#### Added

- `notifications.listen`, `unlisten`, `unlistenAll` and `notify` on `NotificationManager` - channel names are quoted as identifiers, no hand-written `LISTEN` needed
- `QueryContext` and `ExceptionTranslator` - a failed query reports the high-level SQL, the SQL actually sent and the parameters, and SQLSTATE codes are categorized instead of surfacing raw error text
- `OctaviusDispatchers` - a shared virtual-thread `ExecutorService` and the `Virtual` coroutine dispatcher
- `readOnly`, `networkTimeout` and `isValid(timeout)` on `OctaviusSession`
- `DISTANT_PAST`/`DISTANT_FUTURE` for `LocalDate` and `LocalDateTime`, plus `LocalTime.MIN`/`MAX`, mapping PostgreSQL's `infinity`, `-infinity` and `24:00:00`
- Type name and array-type caches in `TypeRegistry` - `typesByName` and `getArrayTypeByElementOid`, so resolving a type by name or finding an element's array type no longer scans every type
- `codecToOid` map, a `loaded` flag on `TypeRegistry`, and logging across authentication, the stream and registry loading
- `docs/type-system.md`, codec KDoc, and codec registration, datetime and notification tests

#### Changed

- Every query parameter is serialized into one shared `PgByteWriter` with the OIDs collected in an `IntArray`, instead of one byte array per parameter
- Parameter serialization moved inside `QueryExecutor`
- `PgType.Record` and `PgType.Void` are singletons instead of data classes carrying an OID
- `resolveOid` returns just the OID
- `codecsByName` removed - lookups go through `codecToOid` and the name cache
- Query methods use a `ReentrantLock` instead of `synchronized`
- The session delegates the plain JDBC methods to the raw connection, and `octaviusConnection` is private
- Aborting a session throws a `SQLException` with SQLSTATE `08000` so HikariCP evicts the connection, replacing the reflective call into Hikari internals
- `PgArray` dropped `setElement`/`setDimension` in favor of flat indexing
- Root `build.gradle.kts` holds the shared configuration, and HikariCP moved into the version catalog
- Exceptions during connection close are suppressed

#### Fixed

- A socket timeout while polling for notifications no longer marks the connection as broken - only a timeout in the middle of a message does

## Version 0.5.0 (v0.5.0)

#### Added

- `OctaviusSession` - the Kotlin-facing API over a JDBC `Connection`: queries, `types`, `notifications`, `transaction`, savepoints, search path, transaction state and `abort()` (which forces a pool like HikariCP to evict the connection)
- `getOctaviusSession` extensions for `DataSource` and `Connection`
- SSL support - `SslMode` (`disable`, `prefer`, `require`, `verify-ca`, `verify-full`), `SslNegotiator`, `PgSslUpgrader` and `SslConfiguration`, plus SSL fields and a login timeout on `OctaviusDataSource`
- SCRAM server signature verification - the server's proof in the SASL final message is checked instead of being ignored
- Multi-module build - the driver moved into a `driver` subproject and a `hikari` module holds the HikariCP integration test
- `IntObjectMap` - an int-keyed map used for OID lookups instead of boxing keys
- `PrimitiveArrayConverter` and `CollectionArrayConverter` - one converter per array shape, resolving the element type once instead of per element
- `TypeManager.registerCodec`
- `QualifiedName`, and `CompositeRegistration` in the registry package
- Transaction integration tests

#### Changed

- `ResultConverter` takes two type parameters (source and target) instead of one
- `query` accepts a mapper
- `connection.transactions.transaction { }` is now `session.transaction { }`, and `transactions.savepoint` is `withSavepoint`; `TransactionManager` moved to the `transaction` package and works on a session - it opens a transaction when auto-commit is on and creates a savepoint when one is already running
- Savepoints split out of the connection into `OctaviusSavepoint`
- `PgStream` owns the whole protocol conversation, pulled out of `OctaviusConnection`
- Packages reorganized - `registry`, `container`, `identifier`, `message`, `session`, `ssl` and `transaction`
- Converter registries iterate by index instead of allocating iterators and lambdas per lookup
- Composite attribute OIDs are stored directly, `attributeNames` is cached, and arrays are created with `arrayOfNulls`
- Parameters are written through `PgByteWriter`
- `AuthException` moved to its own file, and the auth package documented
- Exceptions inherits from `RuntimeException` instead of `SQLException`

#### Fixed

- The enum converter resolves the type OID through the registry and checks the expected Kotlin class, instead of matching on the type name alone
- The enum parameter converter accepts subclasses of an enum class (enum constants with bodies)

## Version 0.4.1 (v0.4.1)

#### Added

- Terminal query methods beyond raw rows - `fetchListOf`, `fetchSingleOf`, `fetchSingleOfOrNull`, `fetchField` and `fetchColumn` on `NativeQuery` and `NamedParameterQuery`
- `cancelQuery()` - opens a second connection and sends a `CancelRequest` for the running statement
- Protocol 3.2 support, including the variable-length cancel key in `BackendKeyData`
- Multidimensional array parameters - nested collections are flattened with their dimensions
- Primitive array support - `PrimitiveArrayParameterConverter` and `PrimitiveArrayConverter` handle `IntArray`, `LongArray` and others
- `ParameterMapper` - parameter conversion pulled out of `ParameterSerializer`
- `PgNotification` data class, and `NotificationManager` moved to its own `notification` package
- Publishing to the local Maven repository

#### Changed

- Converter packages reorganized - `converter/parameter/{array,composite,mapper,standard}` and `converter/result/{array,composite,mapper,record,row,standard}`, and the container types moved from `type.containter` to `type.container`
- `ResultConverter` declares the source class it supports, so the registry looks converters up by class instead of trying all of them
- `QueryExecutor` builds `Row` objects while reading the stream instead of collecting `DataRowMessage`s and mapping afterwards
- OIDs are plain `Int` instead of `UInt`
- Array elements and range bounds carry nullability, so a null element in a non-nullable position is reported
- Wire protocol messages are internal
- Codecs read straight from the raw byte array with an offset and length

#### Fixed

- The reflection composite converter takes the parameter type from its cached metadata
- `_record` (array of records) is loaded from the catalog and mapped as an array type, so an array of records deserializes

#### Removed

- `ByteArrayWindow` and its extensions
- `ContainerField`


## Version 0.4.0 (v0.4.0)

First tagged release.

#### Added

**Protocol and connection**

- PostgreSQL Wire Protocol v3 spoken directly, with no other driver wrapped underneath - startup, authentication, query and termination messages implemented on both sides
- SCRAM-SHA-256 authentication; cleartext and MD5 requests are rejected with a clear message
- SSL/TLS negotiation
- PostgreSQL 18+ enforced - an older server is rejected during connect
- `PgStream`, `PgInputStream`, `PgOutputStream` and `PgByteWriter` - the binary IO layer over the socket

**JDBC surface**

- `OctaviusDriver` (registered through `META-INF/services`), `OctaviusDataSource`, `OctaviusConnection` and a minimal `OctaviusStatement` - enough of `java.sql` to run under a pool like HikariCP
- Transactions - auto-commit control, commit and rollback, isolation levels, read-only mode, `TransactionManager` for transaction blocks, and savepoints through `OctaviusSavepoint`
- `isValid`, network timeout, and reading and setting the `search_path`

**Queries**

- `createNativeQuery` with positional parameters, and `createNamedQuery` with named parameters translated by `SqlParameterParser` - which understands quotes, dollar quoting, comments and escape literals
- `fetchAll`, `fetchOne`, `fetchOneOrNull`, `update` and `execute`
- Extended Query Protocol (Parse/Bind/Execute/Sync) by default, with the Simple Query Protocol kept for statements like `BEGIN` and `SET`
- `Row` - values decoded eagerly, read by index or column name
- `LISTEN`/`NOTIFY` exposed as a coroutine `SharedFlow`, with a listener loop and a drop-oldest buffer

**Types**

- `TypeRegistry` loaded from the system catalog, cached per URL by `GlobalTypeRegistry`, resolving names against the search path
- Binary codecs for the standard types - integers, floats, `numeric`, `bool`, `text`/`varchar`/`bpchar`, `bytea`, `uuid`, `json`/`jsonb`, the date and time types, `void` and `record`
- Container types - `PgArray` (multidimensional), `PgComposite`, `PgRange`, `PgMultirange` and `PgRecord` - with dynamic codecs and factory methods on `TypeManager`
- Dynamic codecs for user-defined enums and domains
- Automatic composite mapping from Kotlin data classes (`registerAutoComposite`) and enum mapping (`registerEnum`), with configurable naming conventions and the `@MapKey` annotation
- Result converters for data classes, maps and records, parameter converters for collections, primitive arrays, composites and `JsonElement`, all pluggable through the converter registries
- `PgTyped` and `withPgType` for pinning a parameter's PostgreSQL type explicitly
- `OctaviusException` hierarchy with enum-based reasons
