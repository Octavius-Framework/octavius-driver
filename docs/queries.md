# Queries

This document covers the query API in the Octavius Driver. Queries fall into two dimensions: how parameters get passed in, and what shape of result you expect back out.

## Query Types (Parameterization)

### Native Query (Positional Parameters)
Native PostgreSQL positional parameters — `$1`, `$2`, `$3`, and so on. Parameters are passed as a `vararg` of type `Any?`.

### Named Parameter Query
Named parameters (`@id`, `@cognomen`, ...) read much better in anything beyond a trivial query, and they remove the classic mistake of passing arguments in the wrong order.
- Values go in either as a `Map<String, Any?>` or as `Pair<String, Any?>` entries via `vararg`.

## Retrieving Results (`fetch*` Methods)

Whenever a query returns rows — a `SELECT`, or an `INSERT` / `UPDATE` / `DELETE` with `RETURNING` — reach for one of the `fetch*` methods rather than `update()`. They split into three families depending on the shape you want back:

### 1. Row-based Methods
The lowest-level option. These return `Row` objects, from which you pull individual columns yourself.
- `fetchRows()` — all matching rows as `List<Row>`; an empty list if there's nothing.
- `fetchRow()` — exactly one row as `Row?`; `null` if the query returns nothing, and a `StatementException` if it returns more than one.
- `fetchRowStrict()` — the non-nullable cousin of `fetchRow()`; throws unless exactly one row comes back.
- `forEachRow(fetchSize: Int, block: (Row) -> Unit)` — streams rows one at a time. Worth using for anything that could return a large result set — say, an audit of every citizen in the census.

### 2. Object Mapping Methods
These map a row straight onto your own Kotlin classes on the fly, via the internal `ResultMapper`.
- `fetchObjects<T>()` — every row, mapped, as `List<T>`.
- `fetchObject<T>()` — a single object, nullable (`T?`); `null` if there's no match.
- `fetchObjectStrict<T>()` — exactly one object, guaranteed non-null; throws otherwise.
- `forEachObject<T>(fetchSize: Int, block: (T) -> Unit)` — the streaming, object-mapped equivalent.

### 3. Single Column Methods
Ideal for scalar or aggregate queries — a `count(*)` over your legions, say — or when you only care about the first column.
- `fetchFields<T>()` — the first column only, as `List<T>`. If that column can be `null`, type `T` as nullable.
- `fetchField<T>()` — the field as `T?`; `null` either because there were no rows, or because the value itself was `null`.
- `fetchFieldStrict<T>()` — throws if the result isn't exactly one row, or if a non-nullable `T` would have to hold `null`. Returns `T`.
- `forEachField<T>(fetchSize: Int, block: (T) -> Unit)` — streaming, first-column-only.

---

### Nullability
Pay close attention to the **Strict** variants.
The standard methods (`fetchRow()`, `fetchObject()`, `fetchField()`) return optional types — reflecting the ordinary reality that an empty result set is a `null`, not an error.

The `fetch*Strict` methods guarantee that exactly one row is returned. If there's no data at all (or more than one row), you'll get a `StatementException` (`StatementExceptionReason.INCORRECT_RESULT_SIZE`).

For `fetchRowStrict()` and `fetchObjectStrict()`, this means they always return a genuinely non-null result. However, for `fetchFieldStrict<T>()`, the returned value can still be `null` if the row exists but the column's value is `null` (provided your type `T` is nullable, e.g., `String?`).

## Data Modification (`update` & `execute`)
For queries that don't return rows, and you don't expect them to:
- `update()` — for DML that changes the database (`UPDATE`, `INSERT`, `DELETE`). Runs through the Extended Query Protocol and returns a `Long` with the affected row count.
- `execute()` — for raw command execution with no result and no row count, such as DDL (creating a table) or administrative commands. **Note:** `execute()` uses PostgreSQL's Simple Query Protocol, so it does not support parameter binding (`$1`) or returning tabular data.

**A note on `RETURNING`:**
If an `INSERT` or `UPDATE` carries a `RETURNING` clause — a common way to get back a newly generated ID after enrolling a new senator, say — use `fetch*`, not `update()`. `fetchFieldStrict<Long>()` is a natural fit here. Internally, a query with `RETURNING` is handled exactly like a `SELECT`.

## Custom Converters
Throughout execution — especially in `fetchObject*` calls — queries convert data behind the scenes through a dedicated type manager and `ResultMapper`.
You can register your own converters and apply them to a specific query instance:
- `registerResultConverter(converter: ResultConverter<*, *>)` — for mapping database results onto Kotlin objects.
- `registerParameterConverter(converter: ParameterConverter<*>)` — for formatting Kotlin objects into query parameters before they head to the database.

That gives the driver a clean way to handle specialized structures out of the box — enums, custom JSON serializers in your DTOs, unusual time formats, and the like.