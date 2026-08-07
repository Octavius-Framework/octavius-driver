# Queries

This document describes the query API available in the Octavius Driver. Queries are categorized based on how parameters are passed and the type of results expected from the database.

## Query Types (Parameterization)

### Native Query (Positional Parameters)
These queries use native PostgreSQL positional parameters, utilizing `$1`, `$2`, `$3`, etc. as placeholders.
Parameters are passed as a variable number of arguments (`vararg`) of type `Any?`.

### Named Parameter Query
This type of query allows the use of named parameters (e.g., `@id`, `@firstName`). It significantly improves the readability of complex SQL queries and reduces the risk of passing parameters in the wrong order.
- Parameter values can be passed as a dictionary `Map<String, Any?>` or as pairs (`Pair<String, Any?>`) via `vararg`.

## Retrieving Results (`fetch*` Methods)

When you execute an SQL query that returns results (such as a `SELECT` statement or modifying statements like `INSERT` / `UPDATE` / `DELETE` with a `RETURNING` clause), **always use the `fetch*` family of methods**. They are divided into three groups depending on the data format you expect:

### 1. Row-based Methods
The lowest level of data retrieval. These methods return `Row` objects, from which individual columns can be extracted.
- `fetchRows()` – fetches all matching rows as `List<Row>`. If there are no results, it returns an empty list.
- `fetchRow()` – fetches exactly one row as an optional type `Row?`. It returns `null` if the query yields no data. It throws a `StatementException` if the query returns more than 1 result.
- `fetchRowStrict()` – fetches exactly one row without nullability (returns a non-null `Row` object). It throws an exception if the number of received rows is not exactly 1 (0 or more than 1).
- `forEachRow(fetchSize: Int, block: (Row) -> Unit)` – allows iterative, streaming processing of rows. Highly recommended for queries returning large datasets to avoid high memory consumption.

### 2. Object Mapping Methods
These methods can automatically map a database row directly to your custom Kotlin objects/classes on the fly (using the internal `ResultMapper`).
- `fetchObjects<T>()` – maps all rows and returns `List<T>`.
- `fetchObject<T>()` – returns a single object while maintaining nullability (`T?`). Returns `null` if there are no results.
- `fetchObjectStrict<T>()` – returns exactly 1 object (always an instance of class `T`). It expects exactly one row, otherwise, it throws an exception.
- `forEachObject<T>(fetchSize: Int, block: (T) -> Unit)` – iterative processing with automatic object mapping.

### 3. Single Column Methods
Perfect for scalar/aggregation queries (e.g., `SELECT count(*) FROM table`) or when you are only interested in extracting the value from the first column of the results (e.g., `SELECT id`).
- `fetchFields<T>()` – fetches only the first column as a list of values `List<T>`. Note that if the database column can contain nulls, you must specify `T` as a nullable type (e.g., `String?`).
- `fetchField<T>()` – returns the field as an optional variant (`T?`). This method can return `null` either if the query returns no rows, or if the column value itself is `null`.
- `fetchFieldStrict<T>()` – returns the field and throws an exception if there were zero or more than one result, or if the returned value is unexpectedly `null` when `T` is a non-nullable type. Returns type `T`.
- `forEachField<T>(fetchSize: Int, block: (T) -> Unit)` – iterative processing of the first column.

---

### Nullability 
Pay special attention to the **Strict** methods.
Standard variants (e.g., `fetchRow()`, `fetchObject()`, `fetchField()`) return optional types (with a `?` symbol). They handle the natural behavior of databases where empty datasets simply return no result (result = `null`).

Using `fetch*Strict` methods provides validation: they strictly guarantee that a non-null object is returned to Kotlin. However, if there is no data (0 results), a `StatementException` (`StatementExceptionReason.INCORRECT_RESULT_SIZE`) will be thrown.

## Data Modification (`update` & `execute`)
For queries that do not return any rows (and you don't expect them to):
- `update()` – used for DML queries that modify the database (e.g., `UPDATE`, `INSERT`, `DELETE`). It uses the Extended Query Protocol under the hood and returns a numerical value (`Long`) indicating how many rows were affected.
- `execute()` – used for raw execution of commands where no results or modification counts are expected, such as DDL statements (table creation) or administrative queries. **Note:** `execute()` uses PostgreSQL's Simple Query Protocol, which means it does not support parameter binding (`$1`) or returning tabular data.

**Important (Using the RETURNING clause):** 
Keep in mind that if you use the `RETURNING` clause with `INSERT` or `UPDATE` queries (a common use case for obtaining newly generated record IDs), you should use the `fetch*` mechanism instead of `update()`. For example, use `fetchFieldStrict<Long>()`. A query returning values via `RETURNING` is handled internally exactly like a standard `SELECT`.

## Custom Converters
Throughout the execution cycle (especially during `fetchObject*` calls), queries convert data under the hood using a dedicated type manager and `ResultMapper`.
This system provides the flexibility to register your own custom converters. You can apply them directly to a specific query instance using:
- `registerResultConverter(converter: ResultConverter<*, *>)` – registers a converter for mapping database results to Kotlin objects.
- `registerParameterConverter(converter: ParameterConverter<*>)` – registers a converter for formatting Kotlin objects into query parameters before they are sent to the database.

This allows the driver to easily handle specific structures such as Enums, custom JSON serializers in DTO objects, or complex time formats out-of-the-box.
