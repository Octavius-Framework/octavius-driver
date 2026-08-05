# Error Handling and Exceptions in Octavius JDBC Driver

The error handling architecture in the Octavius Driver is built around a single base class – `OctaviusException`. It provides a rich context to make debugging easier. Specific subclasses represent distinct categories of errors.

Most exceptions utilize **enums** to uniquely identify the reason for the error. This means developers do not have to manually parse PostgreSQL `SQLSTATE` codes.

---

## Message Format and Logging

The base `message` property of an `OctaviusException` is always formatted predictably as `EXCEPTION_NAME(:REASON_ENUM)`. This allows for fast visual identification and programmatic filtering without relying on complex string matching.

For example, a violation of a unique constraint will have the message `CONSTRAINT_VIOLATION_EXCEPTION:UNIQUE_CONSTRAINT_VIOLATION`.

When an exception is logged (or its `toString()` method is called), it generates a highly readable, structured block that includes the error type, SQL state, message, details, and query context.

### Example Log Output

```text
------------------------------------------------------------
ERROR: ConstraintViolationException
SQLSTATE: 23505
MESSAGE: CONSTRAINT_VIOLATION_EXCEPTION:UNIQUE_CONSTRAINT_VIOLATION
DETAILS: Reason: A duplicate value was provided for a unique column or index (PostgreSQL 23505).
Table: users
Constraint: users_email_key
================================================================================
DATABASE EXECUTION CONTEXT
================================================================================
HIGH-LEVEL SQL:
INSERT INTO users (email) VALUES (?)
--------------------------------------------------------------------------------
PARAMETERS:
1 - admin@example.com
================================================================================
------------------------------------------------------------
CAUSE:
------------------------------------------------------------
No cause available
------------------------------------------------------------
```

---

## Error Context (QueryContext)

Every `OctaviusException` can carry a `QueryContext` object. Once an error is caught, this context provides:
- **`sql`**: The high-level SQL query executed by the application.
- **`parameters`**: The map of parameters passed to the query.
- **`dbSql`**: The actual SQL query sent to the database (after translation).
- **`dbParameters`**: The list of values sent directly to the database.

---

## Exception Categories and Enums

### 1. `ConstraintViolationException`
**When it is thrown:** When a database constraint is violated during query execution (e.g., inserting a duplicate primary key).
**Context / Properties:** 
- `schema`, `table`, `column` – Identifies which column/table the violation affects.
- `constraint` – The name of the violated constraint.

| Reason (`ConstraintViolationExceptionReason`) | Description                                                |
|-----------------------------------------------|------------------------------------------------------------|
| `UNIQUE_CONSTRAINT_VIOLATION`                 | Duplicate value provided for a unique column or index.     |
| `FOREIGN_KEY_VIOLATION`                       | Value does not exist in the referenced table.              |
| `NOT_NULL_VIOLATION`                          | Null value provided for a non-nullable column.             |
| `CHECK_CONSTRAINT_VIOLATION`                  | Value fails a CHECK constraint.                            |
| `EXCLUSION_CONSTRAINT_VIOLATION`              | Exclusion constraint violation (e.g., overlapping ranges). |
| `UNKNOWN`                                     | Unmapped or generic constraint violation.                  |

### 2. `DataException`
**When it is thrown:** When the query structure is correct, but the data values cause a database error (e.g., numeric overflow, JSON parsing error).
**Context / Properties:** `details` (Detailed database message).

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
**When it is thrown:** Errors during SQL parsing, planning, or execution (e.g., syntax errors, missing tables, ambiguous columns).
**Context / Properties:** `position` (The exact 1-based character position of the error in the SQL string).

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
**When it is thrown:** When the driver fails to establish a connection or authenticate with the database server.

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
**When it is thrown:** When a physical network error disrupts communication (e.g., timeout, server crash, broken pipe).

| Reason (`NetworkExceptionReason`) | Description                                          |
|-----------------------------------|------------------------------------------------------|
| `CONNECTION_ERROR`                | General network error.                               |
| `CONNECTION_TIMEOUT`              | Network operation timed out.                         |
| `CONNECTION_CLOSED_BY_PEER`       | Server abruptly closed the connection.               |
| `CONNECTION_CLOSED`               | Operation attempted on an already closed connection. |
| `CONNECTION_ABORTED`              | Connection explicitly aborted by the client.         |

### 6. `TransactionException`
**When it is thrown:** When a transaction fails due to environment or concurrency issues.

| Reason (`TransactionExceptionReason`) | Description                                                |
|---------------------------------------|------------------------------------------------------------|
| `TIMEOUT`                             | Transaction or statement timed out.                        |
| `LOCK_NOT_AVAILABLE`                  | Required lock could not be obtained (`NOWAIT`).            |
| `DEADLOCK_DETECTED`                   | Transaction deadlock detected.                             |
| `SERIALIZATION_FAILURE`               | Transaction failed due to a serialization/isolation issue. |
| `UNKNOWN`                             | Unknown transaction exception.                             |

### 7. `RoutineExecutionException`
**When it is thrown:** When an error occurs inside a PL/pgSQL routine (e.g., explicit `RAISE EXCEPTION` or assertions).
**Context / Properties:** `dbDetail`, `hint`, `whereContext` (Stacktrace inside the procedure).

| Reason (`RoutineExecutionExceptionReason`) | Description                                                                  |
|--------------------------------------------|------------------------------------------------------------------------------|
| `RAISE_EXCEPTION`                          | User-defined exception raised by the procedure.                              |
| `NO_DATA_FOUND`                            | Strict row count expectations (single row) were not met (returned 0).        |
| `TOO_MANY_ROWS`                            | Strict row count expectations (single row) were not met (returned multiple). |
| `ASSERT_FAILURE`                           | Assertion failed during execution.                                           |
| `UNKNOWN`                                  | Unknown PL/pgSQL execution error.                                            |

### 8. `PermissionDeniedException`
**When it is thrown:** When the database user lacks privileges to execute an action or access an object.
**Context / Properties:** `schema`, `table`, `column`, `routine`, `datatype` (Identifies what object access was denied).

### 9. `InvalidOperationException`
**When it is thrown:** When the driver attempts an operation not allowed in the current state.

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
**When it is thrown:** When there are issues resolving PostgreSQL types or converting data.
**Context (CodecException):** `action` (`ENCODING` / `DECODING`), `value`, `pgType`, `kotlinClass`.

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
**When it is thrown:** When a type conversion or data mapping operation fails.
**Context / Properties:** `path` (The object tree path where the mapping failed).

| Reason (`MappingExceptionReason`) | Description                                                          |
|-----------------------------------|----------------------------------------------------------------------|
| `COLUMN_NOT_FOUND`                | The requested variable/property was not found in the SQL result set. |
| `REQUIRED_ATTRIBUTE_MISSING`      | The database returned `NULL` for a non-nullable Kotlin property.     |
| `NO_CONVERTER_FOUND`              | No converter found for the specified types.                          |
| `CONVERSION_ERROR`                | Error during type casting or conversion (e.g., `Int` to `String`).   |

### 12. `DatabaseSystemException`
**When it is thrown:** When a generic database system error occurs (e.g., out of memory, disk full).
**Context / Properties:** `errorMessage`.
