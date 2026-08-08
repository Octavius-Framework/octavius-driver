# Error Handling and Exceptions in Octavius JDBC Driver

Octavius builds its error handling around a single base class — `OctaviusException` — that carries rich diagnostic context. Specific subclasses represent distinct categories of failure, so you're rarely left guessing what went wrong.

Most exceptions carry an **enum** identifying the exact reason for the failure, which means you don't have to hand-parse PostgreSQL `SQLSTATE` codes yourself.

---

## Message Format and Logging

The base `message` property of an `OctaviusException` always follows a predictable `EXCEPTION_NAME[:REASON_ENUM]` shape (the `:REASON_ENUM` part is omitted if there is no specific reason enum for a given exception). This makes it easy to spot at a glance and easy to filter on programmatically without string-matching gymnastics.

A unique-constraint violation, for instance, produces the message `CONSTRAINT_VIOLATION_EXCEPTION:UNIQUE_CONSTRAINT_VIOLATION`.

When an exception is logged (or `toString()` is called on it), it prints a structured, readable block containing the error type, SQL state, message, details, and query context.

### Example Log Output

```text
--------------------------------------------------------------------------------
MESSAGE: CONSTRAINT_VIOLATION_EXCEPTION:UNIQUE_CONSTRAINT_VIOLATION
SQLSTATE: 23505
EXCEPTION DETAILS:
Reason: A duplicate value was provided for a unique column or index (PostgreSQL 23505).
Database Message: Duplicate value violates unique constraint "senators_cognomen_key" 
Details: Key (cogomen_id)=(1)
Schema: roma
Table: senators
Constraint: senators_cognomen_key
================================================================================
DATABASE EXECUTION CONTEXT
================================================================================
HIGH-LEVEL SQL:
INSERT INTO senators (cognomen) VALUES (?)
--------------------------------------------------------------------------------
PARAMETERS:
1 - Scipio
================================================================================
--------------------------------------------------------------------------------
```

(The Senate, it turns out, already had a Scipio.)

---

## Error Context (QueryContext)

Every `OctaviusException` can carry a `QueryContext`. Once you've caught the error, this gives you:
- **`sql`** — the high-level SQL query your application executed.
- **`parameters`** — the map of parameters passed into the query.
- **`dbSql`** — the actual SQL sent to the database, after translation.
- **`dbParameters`** — the values sent directly to the database.

---

## Exception Categories and Enums

### 1. `ConstraintViolationException`
**Thrown when:** a database constraint is violated during execution (e.g. inserting a duplicate primary key).
**Context / properties:**
- `schema`, `table`, `column` — identifies the column/table the violation affects.
- `constraint` — the name of the violated constraint.

| Reason (`ConstraintViolationExceptionReason`) | Description                                                |
|-----------------------------------------------|------------------------------------------------------------|
| `UNIQUE_CONSTRAINT_VIOLATION`                 | Duplicate value provided for a unique column or index.     |
| `FOREIGN_KEY_VIOLATION`                       | Value does not exist in the referenced table.              |
| `NOT_NULL_VIOLATION`                          | Null value provided for a non-nullable column.             |
| `CHECK_CONSTRAINT_VIOLATION`                  | Value fails a CHECK constraint.                            |
| `EXCLUSION_CONSTRAINT_VIOLATION`              | Exclusion constraint violation (e.g., overlapping ranges). |
| `UNKNOWN`                                     | Unmapped or generic constraint violation.                  |

### 2. `DataException`
**Thrown when:** the query itself is well-formed, but the data values trigger a database error (e.g. numeric overflow, malformed JSON).
**Context / properties:** `details` (the detailed database message).

| Reason (`DataExceptionReason`) | Description                                              |
|--------------------------------|----------------------------------------------------------|
| `DATA_TRUNCATION`              | String or datetime value was truncated.                  |
| `NUMERIC_OUT_OF_RANGE`         | Numeric value is out of bounds for the target data type. |
| `DIVISION_BY_ZERO`             | Attempted to divide by zero.                             |
| `INVALID_FORMAT`               | Invalid data format or text representation.              |
| `ARRAY_SUBSCRIPT_ERROR`        | Array subscript out of bounds.                           |
| `NULL_VALUE_NOT_ALLOWED`       | Null value provided where prohibited.                    |
| `JSON_ERROR`                   | Error while parsing or operating on JSON/JSONB data.     |
| `XML_ERROR`                    | Error in XML operations.                                 |
| `ESCAPE_CHARACTER_ERROR`       | Invalid escape character or sequence.                    |
| `REGEX_ERROR`                  | Invalid regular expression.                              |

### 3. `StatementException`
**Thrown when:** SQL parsing, planning, or execution fails (e.g. syntax errors, missing tables, ambiguous columns).
**Context / properties:** `position` — the exact 1-based character position of the error in the SQL string.

| Reason (`StatementExceptionReason`) | Description                                                               |
|-------------------------------------|---------------------------------------------------------------------------|
| `SYNTAX_ERROR`                      | SQL syntax error.                                                         |
| `UNCLOSED_QUOTE`                    | Unclosed string or identifier quote.                                      |
| `UNCLOSED_DOLLAR_QUOTE`             | Unclosed dollar-quoted string.                                            |
| `UNCLOSED_COMMENT`                  | Unclosed multi-line comment.                                              |
| `UNDEFINED_OBJECT`                  | Referenced function, column, or table does not exist.                     |
| `DUPLICATE_OBJECT`                  | Object already exists (DDL statements).                                   |
| `AMBIGUOUS_OBJECT`                  | Ambiguous reference (e.g., in JOINs).                                     |
| `DATA_TYPE_ERROR`                   | Type mismatch at the query level.                                         |
| `INVALID_DEFINITION`                | Invalid definition or object state.                                       |
| `INVALID_TRANSACTION_STATE`         | Invalid transaction state.                                                |
| `MISSING_NAMED_PARAMETER`           | Missing value for a named parameter.                                      |
| `INCORRECT_RESULT_SIZE`             | Query returned an unexpected number of rows (e.g., `SELECT INTO STRICT`). |

### 4. `InitializationException`
**Thrown when:** the driver fails to establish a connection or authenticate with the database server.

| Reason (`InitializationExceptionReason`) | Description                                                                  |
|------------------------------------------|------------------------------------------------------------------------------|
| `SERVER_REJECTED_CREDENTIALS`            | Invalid username or password.                                                |
| `UNSUPPORTED_MECHANISM`                  | Server requires an unsupported mechanism.                                    |
| `UNSUPPORTED_PASSWORD_ENCRYPTION`        | Server requires an unsupported password encryption method (e.g., cleartext). |
| `PROTOCOL_VIOLATION`                     | Unexpected message received during authentication protocol.                  |
| `MISSING_PROTOCOL_PARAMETER`             | Missing expected parameter in the server's authentication message.           |
| `SSL_ERROR`                              | TLS/SSL negotiation failed or is not supported by the server.                |
| `UNSUPPORTED_SERVER_VERSION`             | PostgreSQL version is not supported.                                         |
| `CONNECTION_ERROR`                       | General connection failure.                                                  |

### 5. `NetworkException`
**Thrown when:** a physical network error disrupts communication (e.g. timeout, server crash, broken pipe).

| Reason (`NetworkExceptionReason`) | Description                                          |
|-----------------------------------|------------------------------------------------------|
| `CONNECTION_ERROR`                | General network error.                               |
| `CONNECTION_TIMEOUT`              | Network operation timed out.                         |
| `CONNECTION_CLOSED_BY_PEER`       | Server abruptly closed the connection.               |
| `CONNECTION_CLOSED`               | Operation attempted on an already closed connection. |
| `CONNECTION_ABORTED`              | Connection explicitly aborted by the client.         |

### 6. `TransactionException`
**Thrown when:** a transaction fails due to environment or concurrency issues.

| Reason (`TransactionExceptionReason`) | Description                                                |
|---------------------------------------|------------------------------------------------------------|
| `TIMEOUT`                             | Transaction or statement timed out.                        |
| `LOCK_NOT_AVAILABLE`                  | Required lock could not be obtained (`NOWAIT`).            |
| `DEADLOCK_DETECTED`                   | Transaction deadlock detected.                             |
| `SERIALIZATION_FAILURE`               | Transaction failed due to a serialization/isolation issue. |
| `UNKNOWN`                             | Unknown transaction exception.                             |

### 7. `RoutineExecutionException`
**Thrown when:** an error occurs inside a PL/pgSQL routine (e.g. explicit `RAISE EXCEPTION` or a failed assertion).
**Context / properties:** `dbDetail`, `hint`, `whereContext` (stack trace inside the procedure).

| Reason (`RoutineExecutionExceptionReason`) | Description                                                                  |
|--------------------------------------------|------------------------------------------------------------------------------|
| `RAISE_EXCEPTION`                          | User-defined exception raised by the procedure.                              |
| `NO_DATA_FOUND`                            | Strict row count expectations (single row) were not met (returned 0).        |
| `TOO_MANY_ROWS`                            | Strict row count expectations (single row) were not met (returned multiple). |
| `ASSERT_FAILURE`                           | Assertion failed during execution.                                           |
| `UNKNOWN`                                  | Unknown PL/pgSQL execution error.                                            |

### 8. `PermissionDeniedException`
**Thrown when:** the database user lacks the privileges to execute an action or access an object.
**Context / properties:** `schema`, `table`, `column`, `routine`, `datatype` — identifies exactly what access was denied.

### 9. `InvalidOperationException`
**Thrown when:** the driver is asked to do something not allowed in its current state.

| Reason (`InvalidOperationExceptionReason`) | Description                                                                          |
|--------------------------------------------|--------------------------------------------------------------------------------------|
| `AUTO_COMMIT_VIOLATION`                    | Attempted manual transaction control (commit/rollback) while auto-commit is enabled. |
| `STATEMENT_CLOSED`                         | Operation attempted on a closed statement.                                           |
| `FEATURE_NOT_SUPPORTED`                    | Feature is not supported by the driver.                                              |
| `INVALID_SAVEPOINT`                        | Invalid savepoint operation.                                                         |
| `UNSUPPORTED_ISOLATION_LEVEL`              | Requested isolation level is not supported.                                          |
| `UNWRAP_ERROR`                             | Failed to unwrap the connection/statement.                                           |
| `INVALID_TIMEOUT`                          | Timeout value cannot be negative.                                                    |
| `NULL_SQL`                                 | SQL string cannot be null.                                                           |
| `UNEXPECTED_RESULT`                        | Execution returned rows when none were expected.                                     |

### 10. `TypeException` & `CodecException`
**Thrown when:** the driver runs into trouble resolving PostgreSQL types or converting data.
**Context (`CodecException`):** `action` (`ENCODING` / `DECODING`), `value`, `pgType`, `kotlinClass`.

| Reason (`TypeExceptionReason`)   | Description                                                 |
|----------------------------------|-------------------------------------------------------------|
| `TYPE_NOT_FOUND`                 | Type is missing from the registry.                          |
| `NOT_A_CONTAINER`                | Type is not a valid container type.                         |
| `MISSING_CODEC`                  | The driver does not have a codec for the specific type.     |
| `ANONYMOUS_RECORD_NOT_SUPPORTED` | PostgreSQL does not support anonymous composite parameters. |
| `NESTED_PGTYPED_NOT_ALLOWED`     | Nested `PgTyped` instances are not permitted.               |

| Action (`CodecAction`) | Description                                                              |
|------------------------|--------------------------------------------------------------------------|
| `ENCODING`             | Codec failed to encode the Kotlin object into PostgreSQL representation. |
| `DECODING`             | Codec failed to decode the PostgreSQL data into a Kotlin object.         |

### 11. `MappingException`
**Thrown when:** a type conversion or data mapping operation fails.
**Context / properties:** `path` — the object tree path where the mapping failed.

| Reason (`MappingExceptionReason`) | Description                                                          |
|-----------------------------------|----------------------------------------------------------------------|
| `COLUMN_NOT_FOUND`                | The requested variable/property was not found in the SQL result set. |
| `REQUIRED_ATTRIBUTE_MISSING`      | The database returned `NULL` for a non-nullable Kotlin property.     |
| `NO_CONVERTER_FOUND`              | No converter found for the specified types.                          |
| `CONVERSION_ERROR`                | Error during type casting or conversion (e.g., `Int` to `String`).   |

### 12. `DatabaseSystemException`
**Thrown when:** a generic database system error occurs (e.g. out of memory, disk full).
**Context / properties:** `errorMessage`.
