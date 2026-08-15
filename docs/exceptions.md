# Error Handling and Exceptions

Octavius builds its error handling around a single base class — `OctaviusException` — that carries rich diagnostic context. Specific subclasses represent distinct categories of failure, so you're rarely left guessing what went wrong.

Three things are worth internalizing before the details:

* **The reason enum is a convenience layer, not a lossy one.** Most exceptions carry an enum identifying the exact reason for the failure, so you don't hand-parse `SQLSTATE` codes yourself. But nothing the server said is thrown away: `serverErrorMessage` still holds the complete parsed `ErrorResponse`.
* **A rejected statement does not desynchronize the connection.** When the *server* reports an error, the driver keeps reading the protocol stream until `ReadyForQuery`, *then* throws — so the session is still usable afterwards. The exception is I/O failure: a `NetworkException` means the socket itself is gone, and that connection is finished.
* **Which type you catch depends on the door you came in through.** The native session API throws `OctaviusException` subclasses, the raw JDBC surface throws `SQLExceptionWrapper`, Spring's `OctaviusTemplate` throws `OctaviusDataAccessException`. See [Crossing into JDBC and Spring](#crossing-into-jdbc-and-spring).

Contents:
* [The base class](#the-base-class)
* [From wire to exception](#from-wire-to-exception)
* [Message format and logging](#message-format-and-logging)
* [Query context](#query-context)
* [SQLSTATE routing](#sqlstate-routing)
* [Exception reference](#exception-reference)
* [Catching at the right altitude](#catching-at-the-right-altitude)
* [Crossing into JDBC and Spring](#crossing-into-jdbc-and-spring)
* [Practical rules and gotchas](#practical-rules-and-gotchas)

## The base class

Every failure the driver reports is an `OctaviusException`:

```kotlin
abstract class OctaviusException(
    message: String,
    val sqlState: String? = null,
    val serverErrorMessage: ServerErrorMessage? = null,
    cause: Throwable? = null
) : RuntimeException(message, cause)
```

| Member                 | Type                  | What it holds                                                                                                |
|:-----------------------|:----------------------|:-------------------------------------------------------------------------------------------------------------|
| `message`              | `String`              | The machine-readable identifier, `EXCEPTION_NAME[:REASON_ENUM]` — never prose.                               |
| `sqlState`             | `String?`             | The five-character SQLSTATE. `null` for purely client-side failures that never reached the server.           |
| `serverErrorMessage`   | `ServerErrorMessage?` | The complete parsed `ErrorResponse` from PostgreSQL. `null` when the error originated in the driver.         |
| `queryContext`         | `QueryContext?`       | What *your application* executed — SQL, parameters, and their database-level forms. Attached on the way out. |
| `cause`                | `Throwable?`          | The underlying exception, where one exists (an `IOException` under a `NetworkException`, for example).       |
| `getDetailedMessage()` | `String?`             | The human-readable explanation, assembled per subclass. This is what the log block renders.                  |

`serverErrorMessage` is a plain data class mirroring the wire message field for field: `severity`, `code`, `message`, `detail`, `hint`, `position`, `internalPosition`, `internalQuery`, `where`, `schema`, `table`, `column`, `datatype`, `constraint`, `file`, `line`, `routine`. Most subclasses re-expose the fields that matter for their category as first-class properties (`constraint` on a constraint violation, `routine` on a permission denial), but the raw object is always there when you need something they don't surface.

The hierarchy is flat — fifteen concrete subclasses directly under `OctaviusException`, no intermediate layers to reason about.

## From wire to exception

### The server-side path

When PostgreSQL rejects something, it sends an `ErrorResponse` message. What happens next is deliberately unhurried:

1. **The executor records, it does not throw.** `QueryExecutor` stores the `ErrorResponseMessage` in a local and keeps looping.
2. **The protocol is drained.** The loop continues until `ReadyForQuery` arrives, which also carries the current transaction status (`I` idle, `T` in transaction, `E` failed transaction) and updates the executor's `transactionStatus`. Skipping this would leave unread bytes in the socket and poison every later query on that connection.
3. **`ExceptionTranslator` classifies.** The SQLSTATE is matched against the [routing table](#sqlstate-routing), producing a concrete subclass plus its reason enum, with `ServerErrorMessage.from(errorMsg)` attached.
4. **The query layer attaches context.** `OctaviusQuery.withQueryContext` wraps every execution method; on the way out it catches the `OctaviusException` and fills in `queryContext` — *only if it is still null*, so the innermost frame that knows the real SQL wins and outer frames don't overwrite it.
5. **It surfaces.** Which type you actually catch depends on the API you entered through — see [Crossing into JDBC and Spring](#crossing-into-jdbc-and-spring).

This is what the server-error path buys you: a `ConstraintViolationException` leaves the connection as clean as a successful `INSERT` would. It says nothing about the I/O path — if the socket dies mid-drain, the loop never reaches step 2, `PgStream` latches `isBroken`, and a `NetworkException` propagates out of an unfinished exchange. That connection is done.

### Errors raised during row mapping

Decoding and mapping happen *while* the result stream is being consumed, so a mapping failure is handled the same careful way: the exception is parked in a local (`executionError`), the loop keeps draining until `ReadyForQuery`, and only then is it thrown. Two consequences worth knowing:

* If the server *also* reported an error, the server's error wins — `errorResponse` is checked before `executionError`.
* Exceptions that aren't `OctaviusException` get wrapped in a `MappingException(CONVERSION_ERROR)`. **This includes exceptions thrown by your own code** inside `forEachRow` / `forEachObject` / `forEachField` blocks, which arrive as `MappingException` with details `"Exception in block: ..."` and the original as `cause`. If you need your own exception type to escape a streaming block, catch and re-throw it outside the block, or make it an `OctaviusException`.

### Errors that never reach the database

Plenty of failures are decided locally, before a single byte goes out. They have no `sqlState` (except where the driver picks a conventional one) and no `serverErrorMessage`:

| Situation                                                            | Exception                                     |
|:---------------------------------------------------------------------|:----------------------------------------------|
| Unclosed quote / dollar-quote / comment found while parsing `@names` | `StatementException(UNCLOSED_*)`              |
| A named parameter present in the SQL has no value supplied           | `StatementException(MISSING_NAMED_PARAMETER)` |
| `fetchRowStrict` got 0 rows, `fetchRow` got 2+                       | `StatementException(INCORRECT_RESULT_SIZE)`   |
| Commit on an auto-commit session, closed statement, unwrap failure   | `InvalidOperationException`                   |
| Type missing from the registry, no codec for an OID                  | `TypeException`                               |
| A codec's `toBinary` / `fromBinary` blew up                          | `CodecException`                              |
| A column is missing, a non-nullable property got `null`              | `MappingException`                            |
| Socket timeout, broken pipe, use of a closed connection              | `NetworkException`                            |
| Handshake, authentication, SSL negotiation, version check            | `InitializationException`                     |

## Message format and logging

`message` always follows a predictable `EXCEPTION_NAME[:REASON_ENUM]` shape. It is an identifier, not a sentence — easy to spot at a glance and easy to filter on programmatically without string-matching gymnastics.

```text
CONSTRAINT_VIOLATION_EXCEPTION:UNIQUE_CONSTRAINT_VIOLATION
STATEMENT_EXCEPTION:SYNTAX_ERROR
CODEC_EXCEPTION:DECODING
PERMISSION_DENIED_EXCEPTION
```

The `:REASON` half is omitted for the three exceptions that have no reason enum — `PermissionDeniedException`, `DatabaseSystemException`, `UncategorizedDatabaseException`. `CodecException` puts its `CodecAction` (`ENCODING` / `DECODING`) in that slot instead.

The human-readable part lives in `toString()`, which renders a structured block: error type, SQL state, the subclass's detailed message, the query context, and — if there is one — the cause. Loggers call `toString()` when you pass the exception as a throwable, so **log the exception, not `e.message`**:

```kotlin
logger.error(e) { "Failed to enrol senator" }   // full block
logger.error { e.message }                      // just "CONSTRAINT_VIOLATION_EXCEPTION:..."
```

### Example: a unique violation

```kotlin
session.createNamedQuery("INSERT INTO senators (cognomen) VALUES (@cognomen)")
    .update("cognomen" to "Scipio")
```

```text
--------------------------------------------------------------------------------
MESSAGE: CONSTRAINT_VIOLATION_EXCEPTION:UNIQUE_CONSTRAINT_VIOLATION
SQLSTATE: 23505
EXCEPTION DETAILS:
Reason: A duplicate value was provided for a unique column or index (PostgreSQL 23505).
Database Message: duplicate key value violates unique constraint "senators_cognomen_key"
Details: Key (cognomen)=(Scipio) already exists.
Schema: roma
Table: senators
Constraint: senators_cognomen_key
================================================================================
DATABASE EXECUTION CONTEXT
================================================================================
HIGH-LEVEL SQL:
INSERT INTO senators (cognomen) VALUES (@cognomen)
--------------------------------------------------------------------------------
PARAMETERS:
cognomen - Scipio
--------------------------------------------------------------------------------
DATABASE-LEVEL SQL (SENT TO DB):
INSERT INTO senators (cognomen) VALUES ($1)
--------------------------------------------------------------------------------
DATABASE-LEVEL PARAMETERS:
1 - Scipio
================================================================================
--------------------------------------------------------------------------------
```

(The Senate, it turns out, already had a Scipio.)

### Example: a syntax error, with the caret

`StatementException` goes one step further. When the database (or the parameter parser) reports a `position`, it slices out the offending line and draws a caret under the exact character:

```text
--------------------------------------------------------------------------------
MESSAGE: STATEMENT_EXCEPTION:SYNTAX_ERROR
SQLSTATE: 42601
EXCEPTION DETAILS:
Reason: The SQL statement contains a syntax error.
Details: syntax error at or near "FRO"
Error at position 10:
SELECT * FRO senators
         ^
================================================================================
DATABASE EXECUTION CONTEXT
...
```

The caret is drawn against `queryContext.dbSql` when there is one, falling back to `sql`. For a named query that means the caret lands on the **transformed** statement (`SELECT $1 FRO ...`), because that is the string PostgreSQL counted positions in. Multi-line SQL is handled — only the line containing the error is printed, and the column is computed relative to that line.

## Query context

`QueryContext` is what makes a failure reproducible. It carries both altitudes of the same statement:

| Property        | Type                  | Meaning                                                              |
|:----------------|:----------------------|:---------------------------------------------------------------------|
| `sql`           | `String`              | The high-level SQL your application wrote, `@names` and all.         |
| `parameters`    | `Map<String, Any?>`   | The parameters as you supplied them.                                 |
| `dbSql`         | `String?`             | The statement actually sent to the server, after transformation.     |
| `dbParameters`  | `List<Any?>?`         | The positional values actually bound.                                |

How it is populated depends on the query type and on *when* things broke:

| Query                                          | `sql`         | `parameters`                   | `dbSql`               | `dbParameters` |
|:-----------------------------------------------|:--------------|:-------------------------------|:----------------------|:---------------|
| `createNativeQuery` (`$1` placeholders)        | as written    | `{"1": …, "2": …}` positional  | identical to `sql`    | the values     |
| `createNamedQuery`, failing at execution       | as written    | your named map                 | the `$n` rewrite      | the values     |
| `createNamedQuery`, failing *before* rewriting | as written    | your named map                 | `null`                | `null`         |

That last row is the parser and missing-parameter case: an `UNCLOSED_QUOTE` or `MISSING_NAMED_PARAMETER` is detected before the SQL is transformed, so there is no database-level form to show.

Exceptions raised outside a query — connection setup, `session.commit()`, savepoint misuse — have `queryContext == null`. Always treat it as nullable.

## SQLSTATE routing

`ExceptionTranslator` is a single ordered `when` over the SQLSTATE string. Order matters: the first matching branch wins, which is how the specific codes (`40002`, `42501`, `55P03`, `57014`) escape their class-wide defaults.

| #  | SQLSTATE                   | Becomes                                                   |
|:---|:---------------------------|:----------------------------------------------------------|
| 1  | `08*`                      | `NetworkException(CONNECTION_ERROR)`                      |
| 2  | `22*`                      | `DataException` — reason per code, see below              |
| 3  | `28*`                      | `InitializationException(SERVER_REJECTED_CREDENTIALS)`    |
| 4  | `21*`, `0A*`, `3D*`, `3F*` | `StatementException(INVALID_DEFINITION)`                  |
| 5  | `23*`                      | `ConstraintViolationException` — reason per code          |
| 6  | `25P03`, `25P04`           | `ExecutionAbortedException(TRANSACTION_TIMEOUT)`          |
| 7  | `25*` (everything else)    | `StatementException(INVALID_TRANSACTION_STATE)`           |
| 8  | `40002`                    | `ConstraintViolationException(UNKNOWN)`                   |
| 9  | `40*` (everything else)    | `ConcurrencyException` — `40001`, `40P01`, else `UNKNOWN` |
| 10 | `42501`                    | `PermissionDeniedException`                               |
| 11 | `42*` (everything else)    | `StatementException` — reason per code, see below         |
| 12 | `54*`                      | `StatementException(SYNTAX_ERROR)`                        |
| 13 | `55P03`                    | `ConcurrencyException(LOCK_NOT_AVAILABLE)`                |
| 14 | `55*` (everything else)    | `DatabaseSystemException`                                 |
| 15 | `57014`                    | `ExecutionAbortedException(QUERY_CANCELED)`               |
| 16 | `57*`, `53*`, `58*`, `XX*` | `DatabaseSystemException`                                 |
| 17 | `P0*`                      | `RoutineExecutionException` — reason per code             |
| 18 | anything else              | `UncategorizedDatabaseException`                          |

**Class 22 (data) → `DataExceptionReason`**

| Codes                                                | Reason                   |
|:-----------------------------------------------------|:-------------------------|
| `22001`, `22008`, `22015`                            | `DATA_TRUNCATION`        |
| `22003`, `22022`                                     | `NUMERIC_OUT_OF_RANGE`   |
| `22012`                                              | `DIVISION_BY_ZERO`       |
| `22007`, `22P02`, `22P03`, `22018`                   | `INVALID_FORMAT`         |
| `2202E`                                              | `ARRAY_SUBSCRIPT_ERROR`  |
| `22004`, `22002`                                     | `NULL_VALUE_NOT_ALLOWED` |
| `2201B`                                              | `REGEX_ERROR`            |
| `22019`, `2200D`, `22025`, `22P06`, `2200C`, `2200B` | `ESCAPE_CHARACTER_ERROR` |
| `2200L`, `2200M`, `2200N`, `2200S`, `2200T`          | `XML_ERROR`              |
| `2203*`                                              | `JSON_ERROR`             |
| anything else in class 22                            | `UNKNOWN`                |

**Class 42 (syntax / access) → `StatementExceptionReason`**

| Codes                                                                           | Reason               |
|:--------------------------------------------------------------------------------|:---------------------|
| `42601`, `42602`, `42622`, `42939`, `42000`                                     | `SYNTAX_ERROR`       |
| `42703`, `42883`, `42P01`, `42P02`, `42704`                                     | `UNDEFINED_OBJECT`   |
| `42701`, `42723`, `42P03`, `42P04`, `42P05`, `42P06`, `42P07`, `42712`, `42710` | `DUPLICATE_OBJECT`   |
| `42702`, `42725`, `42P08`, `42P09`                                              | `AMBIGUOUS_OBJECT`   |
| `42804`, `42P18`, `42846`, `42P21`, `42P22`                                     | `DATA_TYPE_ERROR`    |
| anything else in class 42                                                       | `INVALID_DEFINITION` |

## Exception reference

### 1. `ConstraintViolationException`

**Thrown when:** a database constraint is violated during execution (e.g. inserting a duplicate primary key).
**Raised by:** the server, SQLSTATE class `23` (plus `40002`).
**Properties:** `reason`, `dbMessage`, `details`, `where`, `schema`, `table`, `column`, `constraint`.

Which of those the database actually fills in varies by violation: a unique violation names the `constraint`, `table` and `schema` and puts the offending key in `details`; a not-null violation names the `column`. Read them as nullable and prefer `constraint` over parsing `dbMessage`.

| Reason (`ConstraintViolationExceptionReason`) | SQLSTATE                  | Description                                                                                                                            |
|:----------------------------------------------|:--------------------------|:---------------------------------------------------------------------------------------------------------------------------------------|
| `UNIQUE_CONSTRAINT_VIOLATION`                 | `23505`                   | Duplicate value provided for a unique column or index.                                                                                 |
| `FOREIGN_KEY_VIOLATION`                       | `23503`                   | Value does not exist in the referenced table.                                                                                          |
| `NOT_NULL_VIOLATION`                          | `23502`                   | Null value provided for a non-nullable column.                                                                                         |
| `CHECK_CONSTRAINT_VIOLATION`                  | `23514`                   | Value fails a CHECK constraint.                                                                                                        |
| `EXCLUSION_CONSTRAINT_VIOLATION`              | `23P01`                   | Exclusion constraint violation (e.g. overlapping ranges).                                                                              |
| `UNKNOWN`                                     | `23000`, `23001`, `40002` | Unmapped or generic constraint violation. In practice these appear only when raised inside a trigger or procedure, or by an extension. |

### 2. `DataException`

**Thrown when:** the query itself is well-formed, but the runtime *values* trigger a database error — numeric overflow, malformed JSON, a text literal that won't parse as the target type. Typically a parameter problem rather than a SQL problem.
**Raised by:** the server, SQLSTATE class `22`.
**Properties:** `reason`, `dbMessage`, `details`, `where`.

| Reason (`DataExceptionReason`) | Description                                                |
|:-------------------------------|:-----------------------------------------------------------|
| `DATA_TRUNCATION`              | String, interval, or datetime value truncated/overflowed.  |
| `NUMERIC_OUT_OF_RANGE`         | Numeric value is out of bounds for the target data type.   |
| `DIVISION_BY_ZERO`             | Attempted to divide by zero.                               |
| `INVALID_FORMAT`               | Invalid text or binary representation for the type.        |
| `ARRAY_SUBSCRIPT_ERROR`        | Array subscript out of bounds or bad dimensions.           |
| `NULL_VALUE_NOT_ALLOWED`       | Null value provided where prohibited by a data constraint. |
| `JSON_ERROR`                   | Error while parsing or operating on JSON/JSONB data.       |
| `XML_ERROR`                    | Error in XML operations.                                   |
| `ESCAPE_CHARACTER_ERROR`       | Invalid escape character or sequence.                      |
| `REGEX_ERROR`                  | Invalid regular expression.                                |
| `UNKNOWN`                      | Generic class-22 error with no specific mapping.           |

### 3. `StatementException`

**Thrown when:** SQL parsing, planning, or execution fails — and also for a handful of client-side statement problems.
**Raised by:** the server (classes `42`, `54`, `25`, `21`, `0A`, `3D`, `3F`) **and** the driver (`SqlParameterParser`, `NamedParameterQuery`, `NativeQuery`).
**Properties:** `reason`, `details`, `position` — the 1-based character position of the error, used to render the caret.

| Reason (`StatementExceptionReason`) | Origin | Description                                                             |
|:------------------------------------|:-------|:------------------------------------------------------------------------|
| `SYNTAX_ERROR`                      | server | SQL syntax error (also class `54`, program limits exceeded).            |
| `UNCLOSED_QUOTE`                    | driver | Unclosed string or identifier quote found while scanning for `@params`. |
| `UNCLOSED_DOLLAR_QUOTE`             | driver | Unclosed dollar-quoted string.                                          |
| `UNCLOSED_COMMENT`                  | driver | Unclosed multi-line comment.                                            |
| `UNDEFINED_OBJECT`                  | server | Referenced function, column, or table does not exist.                   |
| `DUPLICATE_OBJECT`                  | server | Object already exists (DDL statements).                                 |
| `AMBIGUOUS_OBJECT`                  | server | Ambiguous reference (e.g. an unqualified column across JOINs).          |
| `DATA_TYPE_ERROR`                   | server | Type mismatch at the query level.                                       |
| `INVALID_DEFINITION`                | server | Invalid definition or object state; also the class-42 catch-all.        |
| `INVALID_TRANSACTION_STATE`         | server | Class `25` — read-only transaction, or a transaction already aborted.   |
| `MISSING_NAMED_PARAMETER`           | driver | A `@name` in the SQL had no value in the supplied map.                  |
| `INCORRECT_RESULT_SIZE`             | driver | `fetch*Strict` found 0 rows, or a single-row fetch found 2+.            |

The `INCORRECT_RESULT_SIZE` case is why single-row fetches request `maxRows = 2` — enough to detect "more than one" without dragging the rest of the result set across the wire.

### 4. `InitializationException`

**Thrown when:** the driver fails to establish a connection or authenticate.
**Raised by:** `OctaviusConnectionFactory`, `Authenticator`, `SslNegotiator`, `PgStream` — and by the translator for any SQLSTATE class `28`.
**Properties:** `reason`, `details`, `cause`.

| Reason (`InitializationExceptionReason`) | Description                                                                |
|:-----------------------------------------|:---------------------------------------------------------------------------|
| `SERVER_REJECTED_CREDENTIALS`            | Invalid username or password.                                              |
| `UNSUPPORTED_MECHANISM`                  | Server requires an authentication mechanism the driver does not implement. |
| `UNSUPPORTED_PASSWORD_ENCRYPTION`        | Server requested cleartext or MD5 rather than SCRAM-SHA-256.               |
| `PROTOCOL_VIOLATION`                     | Unexpected message received during the authentication exchange.            |
| `MISSING_PROTOCOL_PARAMETER`             | A required field was missing from the server's authentication challenge.   |
| `SSL_ERROR`                              | TLS negotiation failed, or the server does not support it.                 |
| `UNSUPPORTED_SERVER_VERSION`             | PostgreSQL older than 18 — Octavius speaks Wire Protocol v3.2 exclusively. |
| `CONNECTION_ERROR`                       | General connection failure before authentication could begin.              |

> [!NOTE]
> Class `28` maps here even mid-session. A `SET ROLE` to a role you may not assume produces an `InitializationException(SERVER_REJECTED_CREDENTIALS)` on a long-established connection.

### 5. `NetworkException`

**Thrown when:** a physical network error disrupts communication, or you touch a connection that is already gone.
**Raised by:** `PgStream` (socket layer), `OctaviusConnection.checkClosed()`, and the translator for SQLSTATE class `08`.
**Properties:** `reason`, `details`, `cause` (usually the original `IOException` / `SocketTimeoutException` / `EOFException`).

| Reason (`NetworkExceptionReason`) | Typical SQLSTATE | Description                                                |
|:----------------------------------|:-----------------|:-----------------------------------------------------------|
| `CONNECTION_ERROR`                | `08006`          | General network error, or the underlying stream is broken. |
| `CONNECTION_TIMEOUT`              | `08006`          | Read or connect timed out.                                 |
| `CONNECTION_CLOSED_BY_PEER`       | `08006`          | Server closed the connection abruptly (`EOF`).             |
| `CONNECTION_CLOSED`               | `08003`          | Operation attempted on an already-closed connection.       |
| `CONNECTION_ABORTED`              | `08000`          | Connection explicitly aborted by the client.               |

Once the socket breaks, `PgStream.isBroken` latches. The next `checkClosed()` flips the connection to closed and throws, which is exactly the signal HikariCP needs to evict it from the pool instead of handing it to the next caller.

### 6. `ConcurrencyException`

**Thrown when:** a transaction fails because of what other transactions were doing.
**Raised by:** the server, SQLSTATE class `40` (except `40002`) and `55P03`.
**Properties:** `reason`.

| Reason (`ConcurrencyExceptionReason`) | SQLSTATE    | Description                                                    |
|:--------------------------------------|:------------|:---------------------------------------------------------------|
| `LOCK_NOT_AVAILABLE`                  | `55P03`     | Required lock could not be obtained (`NOWAIT`).                |
| `DEADLOCK_DETECTED`                   | `40P01`     | Transaction deadlock detected and broken by the server.        |
| `SERIALIZATION_FAILURE`               | `40001`     | Serialization conflict under `REPEATABLE READ`/`SERIALIZABLE`. |
| `UNKNOWN`                             | other `40*` | Unmapped rollback error.                                       |

This is the one category that is routinely **retryable** — see the retry loop in [Catching at the right altitude](#catching-at-the-right-altitude).

### 7. `ExecutionAbortedException`

**Thrown when:** execution is stopped by the server rather than failing on its own merits.
**Raised by:** the server, `25P03` / `25P04` / `57014`.
**Properties:** `reason`.

| Reason (`ExecutionAbortedExceptionReason`) | SQLSTATE         | Description                                                           |
|:-------------------------------------------|:-----------------|:----------------------------------------------------------------------|
| `TRANSACTION_TIMEOUT`                      | `25P03`, `25P04` | `idle_in_transaction_session_timeout` or `transaction_timeout` fired. |
| `QUERY_CANCELED`                           | `57014`          | Cancelled via `statement_timeout` or `session.cancelQuery()`.         |

### 8. `RoutineExecutionException`

**Thrown when:** something goes wrong *inside* a PL/pgSQL routine — usually your own business rules, expressed as `RAISE EXCEPTION`.
**Raised by:** the server, SQLSTATE class `P0`.
**Properties:** `reason`, `dbMessage`, `dbDetail`, `hint`, `where`.

`where` is the PL/pgSQL call stack (`PL/pgSQL function elect_consul(text) line 12 at RAISE`), which is what makes this exception genuinely debuggable. `dbMessage` is the text you passed to `RAISE`, `dbDetail` and `hint` the `DETAIL` and `HINT` clauses.

| Reason (`RoutineExecutionExceptionReason`) | SQLSTATE    | Description                                      |
|:-------------------------------------------|:------------|:-------------------------------------------------|
| `RAISE_EXCEPTION`                          | `P0001`     | User-defined exception raised by the routine.    |
| `NO_DATA_FOUND`                            | `P0002`     | `SELECT INTO STRICT` returned no rows.           |
| `TOO_MANY_ROWS`                            | `P0003`     | `SELECT INTO STRICT` returned more than one row. |
| `ASSERT_FAILURE`                           | `P0004`     | An `ASSERT` failed during execution.             |
| `UNKNOWN`                                  | other `P0*` | Unmapped PL/pgSQL error.                         |

> [!TIP]
> `RAISE EXCEPTION` accepts `USING ERRCODE = '...'`. Raise a domain-specific SQLSTATE and it routes through the normal table — `ERRCODE = '23505'` will reach your application as a `ConstraintViolationException`, not a `RoutineExecutionException`.

See [Functions and Procedures](functions-procedures.md) for calling conventions.

### 9. `PermissionDeniedException`

**Thrown when:** the database user lacks the privileges for an action or object.
**Raised by:** the server, SQLSTATE `42501`.
**Properties:** `dbMessage`, `schema`, `table`, `column`, `datatype`, `routine`. No reason enum — its `message` is simply `PERMISSION_DENIED_EXCEPTION`.

The object-identifying fields come straight from the server's error message, so they pinpoint what was refused rather than making you parse `permission denied for table ...`.

### 10. `InvalidOperationException`

**Thrown when:** the driver is asked to do something not allowed in its current state. Purely client-side — no `sqlState`, no `serverErrorMessage`.
**Raised by:** `OctaviusConnection`, `OctaviusStatement`, `OctaviusSavepoint`, `OctaviusDataSource`, `LargeObject`, `CopyManager`, `QueryExecutor`.
**Properties:** `reason`, `details`.

| Reason (`InvalidOperationExceptionReason`) | Description                                                                                                                                                                    |
|:-------------------------------------------|:-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `AUTO_COMMIT_VIOLATION`                    | `commit()`, `rollback()` or a savepoint attempted while auto-commit is enabled.                                                                                                |
| `INVALID_SAVEPOINT`                        | Savepoint is unknown, already released, or belongs to another connection.                                                                                                      |
| `OBJECT_CLOSED`                            | Operation attempted on a closed statement or large object.                                                                                                                     |
| `INVALID_ARGUMENT`                         | An argument is not acceptable — a negative timeout, a null SQL string, an unsupported isolation level, a non-positive COPY `bufferSize`. `details` names the rejected value.   |
| `UNWRAP_ERROR`                             | JDBC `unwrap()` to an interface this object does not implement.                                                                                                                |
| `FEATURE_NOT_SUPPORTED`                    | A legacy JDBC feature Octavius deliberately does not implement.                                                                                                                |
| `UNEXPECTED_RESULT`                        | `execute()`/`update()` received result rows — use a `fetch*` method for DQL. Also raised when a `COPY` did not start, or when a `DataRow` arrives before its `RowDescription`. |
| `COPY_IN_PROGRESS`                         | The connection is in copy mode. Anything else on that session — a query, a second `COPY`, a notification listener — is refused until the transfer ends. See [COPY](copy.md).   |
| `EXECUTION_IN_PROGRESS`                    | A statement is already executing on this connection — usually a query issued from a `forEach` block or a converter. See [Queries](queries.md).                                 |
| `COPY_NOT_ACTIVE`                          | A `CopyIn` / `CopyOut` handle was used after it was ended or cancelled. Handles are single-use — start a new one through the `CopyManager`.                                    |

### 11. `TypeException`

**Thrown when:** type resolution fails in the registry.
**Raised by:** `TypeDictionary`, `ContainerFactory`, `PgTyped`, `ParameterSerializer`.
**Properties:** `reason`, `oid`, `typeName`, `details`.

| Reason (`TypeExceptionReason`)   | Description                                                                                                                    |
|:---------------------------------|:-------------------------------------------------------------------------------------------------------------------------------|
| `TYPE_NOT_FOUND`                 | Type is missing from the registry — often a `CREATE TYPE` executed after the catalog was loaded; call `session.reloadTypes()`. |
| `NOT_A_CONTAINER`                | The OID is not a composite/array/enum/range container.                                                                         |
| `MISSING_CODEC`                  | The driver has no codec for that OID (see [Type System](type-system.md#base-types-the-driver-does-not-implement)).             |
| `ANONYMOUS_RECORD_NOT_SUPPORTED` | PostgreSQL cannot accept an anonymous `record` as a parameter.                                                                 |
| `NESTED_PGTYPED_NOT_ALLOWED`     | A `PgTyped` cannot wrap another `PgTyped`.                                                                                     |

### 12. `CodecException`

**Thrown when:** encoding or decoding a value against PostgreSQL's binary format fails. Every codec call is routed through `encodeSafely` / `decodeSafely`, which catch *anything* the codec throws and re-wrap it here with the original as `cause`.
**Properties:** `action`, `value`, `name`, `schema`, `oid`, `kotlinClass`.

| Action (`CodecAction`) | Description                                                          |
|:-----------------------|:---------------------------------------------------------------------|
| `ENCODING`             | Failed to encode the Kotlin object into PostgreSQL's representation. |
| `DECODING`             | Failed to decode PostgreSQL's bytes into a Kotlin object.            |

`value` is truncated before it is stored — a `ByteArray` is reported as `ByteArray(n bytes)` and decoding keeps at most the first 100 bytes; a `toString()` longer than 100 characters is cut with an ellipsis. Diagnostics stay readable and a 40 MB `bytea` never ends up in your log file.

### 13. `MappingException`

**Thrown when:** a conversion or object-mapping step fails, on either side of the wire.
**Raised by:** `ResultMapper`, `ReflectionMappingUtils`, and the `PgComposite` / `PgRecord` / `PgArray` / `PgRange` accessors.
**Properties:** `reason`, `details`, `path`.

| Reason (`MappingExceptionReason`) | Description                                                                                  |
|:----------------------------------|:---------------------------------------------------------------------------------------------|
| `COLUMN_NOT_FOUND`                | The requested column, index, or composite attribute does not exist.                          |
| `REQUIRED_ATTRIBUTE_MISSING`      | The database returned `NULL` (or nothing) for a non-nullable Kotlin property.                |
| `NO_CONVERTER_FOUND`              | No converter registered for the source/target type pair.                                     |
| `CONVERSION_ERROR`                | Cast or conversion failed; also the wrapper for foreign exceptions escaping a mapping block. |

`path` accumulates as the exception unwinds through nested structures — each frame appends its own key name — and is printed reversed, outermost first: `Path: consul -> province -> founded`. That tells you *which* field five levels down in a nested composite was the problem.

### 14. `DatabaseSystemException`

**Thrown when:** the database engine itself is in trouble rather than your query being wrong — out of memory, disk full, configuration limits, internal errors.
**Raised by:** the server, SQLSTATE classes `53`, `55` (except `55P03`), `57` (except `57014`), `58`, `XX`.
**Properties:** `errorMessage` — a pre-formatted string that already includes the SQLSTATE and the server's message. No reason enum.

### 15. `UncategorizedDatabaseException`

**Thrown when:** a database error arrives with a SQLSTATE no branch of the routing table claims.
**Properties:** `details`. No reason enum.

Reaching for `serverErrorMessage` is the right move here — it holds everything the server said, and `sqlState` tells you which code slipped through.

## Catching at the right altitude

Three levels, pick per site.

**Everything.** For logging, a global handler, or a transaction wrapper:

```kotlin
try {
    session.transaction.required { /* ... */ }
} catch (e: OctaviusException) {
    logger.error(e) { "Database work failed" }
    throw e
}
```

**A category.** When the class alone tells you what to do:

```kotlin
try {
    session.createNamedQuery("INSERT INTO senators (cognomen) VALUES (@cognomen)")
        .update("cognomen" to cognomen)
} catch (e: ConstraintViolationException) {
    throw SenatorAlreadyEnrolled(e.constraint, e)
}
```

**A specific reason.** When the distinction inside a category is what you're branching on — no SQLSTATE string comparisons required:

```kotlin
catch (e: ConstraintViolationException) {
    when (e.reason) {
        ConstraintViolationExceptionReason.UNIQUE_CONSTRAINT_VIOLATION -> conflict(e.constraint)
        ConstraintViolationExceptionReason.FOREIGN_KEY_VIOLATION       -> badReference(e.table)
        else                                                           -> throw e
    }
}
```

### Retrying concurrency failures

`SERIALIZATION_FAILURE` and `DEADLOCK_DETECTED` mean "nothing was wrong with your statement, the timing was unlucky" — the same work may well succeed on a second attempt. Getting the retry right takes a little care about what state the transaction is actually in.

On such an error PostgreSQL **dooms** the transaction: everything it did is discarded, and the session moves to failed-transaction state (`ReadyForQuery` reports `E`), rejecting every further command with `25P02` → `StatementException(INVALID_TRANSACTION_STATE)`. What it does *not* do is end the transaction block — someone still has to send `ROLLBACK`. That someone is whoever owns the boundary: `transaction.required { }` if it opened the transaction, `session.rollback()` if you are driving `autoCommit = false` yourself.

So a retry has to restart the *whole* transaction — only a new transaction gets a new snapshot, which is the entire point under `REPEATABLE READ` / `SERIALIZABLE` — which puts the retry wrapper around the frame that owns the boundary:

```kotlin
fun <T> withRetry(attempts: Int = 3, block: () -> T): T {
    repeat(attempts - 1) {
        try {
            return block()
        } catch (e: ConcurrencyException) {
            if (e.reason == ConcurrencyExceptionReason.UNKNOWN) throw e
            Thread.sleep(50L shl it) // back off: 50ms, 100ms, ...
        }
    }
    return block()
}

val consul = withRetry {
    session.transaction.required {
        createNamedQuery("UPDATE aerarium SET balance = balance - @amount WHERE province_id = @id")
            .update("amount" to 100, "id" to 1)
        createNamedQuery("SELECT name FROM consuls WHERE id = @id").fetchFieldStrict<String>("id" to 1)
    }
}
```

Wrapping a `required { }` that merely *joined* an outer transaction retries nothing useful — the boundary is somewhere further out, so each attempt replays into the same doomed transaction. Put the retry where the transaction actually begins.

## Crossing into JDBC and Spring

The exception you catch depends on which door you came in through.

| Entry point                                                                         | You catch                                               |
|:------------------------------------------------------------------------------------|:--------------------------------------------------------|
| `OctaviusSession` — `createNativeQuery`, `createNamedQuery`, `commit`, `rollback` … | `OctaviusException` subclasses, unchanged               |
| A raw `java.sql.Connection` (`dataSource.connection`)                               | `SQLExceptionWrapper` (a `java.sql.SQLException`)       |
| Spring's `OctaviusTemplate`                                                         | `OctaviusDataAccessException` (a `DataAccessException`) |

### `SQLExceptionWrapper`

The JDBC surface must throw `SQLException` — connection pools depend on it to inspect SQLSTATE, evict dead connections, and decide what is recoverable. So `OctaviusConnection` wraps:

```kotlin
class SQLExceptionWrapper(val wrappedException: OctaviusException)
    : SQLException(wrappedException.message, wrappedException.sqlState)
```

The wrapper carries the message and SQLSTATE, but **not** the cause chain — `wrapper.cause` is `null`. Reach for `wrapper.wrappedException` to get back the typed exception with its context and stack trace intact:

```kotlin
try {
    dataSource.connection.use { /* raw JDBC */ }
} catch (e: SQLExceptionWrapper) {
    val octavius = e.wrappedException
    logger.error(octavius) { "..." }
}
```

Going the other way, `OctaviusSessionImpl` unwraps automatically: session-level operations that delegate to the JDBC connection (`autoCommit`, `commit()`, `rollback()`, `transactionIsolationLevel`, `networkTimeout`, …) catch `SQLExceptionWrapper` and re-throw the original. **Session API users never see the wrapper.**

### Spring

`OctaviusExceptionTranslator` plugs into Spring's `SQLExceptionTranslator` contract. It searches the incoming `SQLException` and its entire cause chain for anything Octavius-shaped — a `SQLExceptionWrapper`, or a bare `OctaviusException` such as the `InitializationException` a pool reports when it could not open a connection at all — and produces an `OctaviusDataAccessException` carrying the original. Searching the whole chain is what keeps a driver failure recognizable after HikariCP has wrapped it in an exception of its own. Anything it doesn't recognize falls through to Spring's own `SQLStateSQLExceptionTranslator`, so you never lose the standard hierarchy.

**Spring users never see the wrapper either**: whatever the layers on the way, `octaviusException` is the driver's exception itself.

`OctaviusDataAccessException` keeps the original available as `octaviusException`:

```kotlin
@ControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(OctaviusDataAccessException::class)
    fun handle(ex: OctaviusDataAccessException): ResponseEntity<Map<String, String>> {
        val root = ex.octaviusException

        if (root is ConstraintViolationException &&
            root.reason == ConstraintViolationExceptionReason.UNIQUE_CONSTRAINT_VIOLATION) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(mapOf("error" to "Already enrolled.", "constraint" to (root.constraint ?: "")))
        }

        if (root is StatementException &&
            root.reason == StatementExceptionReason.INVALID_TRANSACTION_STATE) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(mapOf("error" to "Cannot write in a read-only transaction."))
        }

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(mapOf("error" to "Unexpected database error."))
    }
}
```

See [Spring Integration](spring-integration.md) for the surrounding configuration.

## Practical rules and gotchas

* **Log the exception, not its message.** `message` is the `EXCEPTION_NAME:REASON` identifier by design. The diagnostic block — details, SQL, parameters, caret — lives in `toString()`, which loggers invoke when the throwable is passed as such.

* **One failed statement poisons the whole transaction.** After any error inside an explicit transaction, PostgreSQL marks the session aborted (`ReadyForQuery` reports `E`) and rejects every further statement with `25P02` → `StatementException(INVALID_TRANSACTION_STATE)` until you roll back. If a failure is *expected* — a speculative insert, say — isolate it in `session.transaction.nested { }` so only its savepoint is discarded. See [Transactions](transactions.md).

* **`queryContext` is nullable, and set once.** The first frame to unwind with a null context fills it in. Errors raised outside a query — handshake, `commit()`, savepoint misuse — never get one.

* **`position` indexes the database-level SQL.** For named queries that means the `$n` form, not your `@name` form. The two strings sit side by side in the query context precisely so you can line them up.

* **Parser errors have no `dbSql`.** `UNCLOSED_QUOTE`, `UNCLOSED_COMMENT`, `MISSING_NAMED_PARAMETER` all fire before the rewrite, so the database-level half of the context is empty.

* **Your exceptions don't escape streaming blocks unchanged.** Anything non-Octavius thrown inside a `forEachRow` / `forEachObject` / `forEachField` block comes back as `MappingException(CONVERSION_ERROR)` with your exception as `cause`.

* **A caught exception usually leaves the session alive — `NetworkException` does not.** For errors the server reported, and for mapping failures, the protocol is drained to `ReadyForQuery` before the throw, so you can keep using the connection. An I/O failure is the opposite case: there is nothing left to drain, `PgStream.isBroken` latches, and every later call fails immediately with `CONNECTION_CLOSED` / `CONNECTION_ERROR`. Don't retry on that session — drop it and take a fresh one from the pool.

* **Retry concurrency failures, not constraint violations.** `SERIALIZATION_FAILURE` and `DEADLOCK_DETECTED` are timing artifacts; a `UNIQUE_CONSTRAINT_VIOLATION` will fail identically forever.

* **`UncategorizedDatabaseException` is a request, not a dead end.** It means a SQLSTATE fell through every branch. `sqlState` and `serverErrorMessage` still hold everything the server said — and it is worth reporting so the routing table can grow a branch for it.
