# Queries

Every query in Octavius is built from a session and executed in one call. There is no statement object to close, no `ResultSet` to walk, and no lifecycle to get wrong — you choose how parameters go in, and what shape you want back.

Contents:
* [Two ways to pass parameters](#two-ways-to-pass-parameters)
* [Choosing a fetch method](#choosing-a-fetch-method)
* [Nullability and the Strict variants](#nullability-and-the-strict-variants)
* [Streaming large results](#streaming-large-results)
* [Do not re-enter the session while rows are being read](#do-not-re-enter-the-session-while-rows-are-being-read)
* [Statements that return nothing](#statements-that-return-nothing)
* [Per-query converters](#per-query-converters)

## Two ways to pass parameters

### Positional — `createNativeQuery`

PostgreSQL's native placeholders, `$1`, `$2`, `$3`, passed as a `vararg`:

```kotlin
session.createNativeQuery("SELECT id, cognomen FROM senators WHERE province_id = $1 AND active = $2")
    .fetchRows(7, true)
```

### Named — `createNamedQuery`

Named placeholders read better past a couple of arguments, and they remove the classic mistake of passing values in the wrong order. Values go in as `Pair`s or as a `Map`:

```kotlin
session.createNamedQuery("SELECT id, cognomen FROM senators WHERE province_id = @province AND active = @active")
    .fetchRows("province" to 7, "active" to true)

session.createNamedQuery("... WHERE province_id = @province")
    .fetchRows(mapOf("province" to 7))
```

Names are rewritten to `$n` before the query is sent, and a name repeated in the statement collapses to one placeholder: `WHERE created > @date OR updated > @date` sends a single parameter and you supply `date` once. Positional queries can do the same by repeating `$1` yourself — this is where both differ from JDBC's `?`, which needs the value passed again for every occurrence.

The rewriter understands SQL well enough to leave the rest of your statement alone — an `@` inside a string literal, a quoted identifier, a line or block comment, or a `$$ … $$` block is not a parameter. PostgreSQL's `@`-operators are safe too, since a parameter name cannot start with an operator character:

```kotlin
// Both @-operators survive; only @filter and @q are parameters
session.createNamedQuery("SELECT * FROM t WHERE data @> @filter AND tsv @@ to_tsquery(@q)")
```

> [!NOTE]
> Parameters are sent in binary under a concrete type, which is what makes a bare `null` ambiguous and can make PostgreSQL pick the wrong overload of a function. [Functions and Procedures](functions-procedures.md#argument-types-decide-which-routine-runs) covers that in detail, and `withPgType` is the way out.

## Choosing a fetch method

Reach for a `fetch*` method whenever the statement produces rows — a `SELECT`, or an `INSERT` / `UPDATE` / `DELETE` with `RETURNING`. Three families, differing only in what they hand back:

| You want                 | All rows            | One row                | Exactly one row             | Streamed          |
|:-------------------------|:--------------------|:-----------------------|:----------------------------|:------------------|
| **Raw columns** (`Row`)  | `fetchRows()`       | `fetchRow(): Row?`     | `fetchRowStrict(): Row`     | `forEachRow()`    |
| **Mapped to your class** | `fetchObjects<T>()` | `fetchObject<T>(): T?` | `fetchObjectStrict<T>(): T` | `forEachObject()` |
| **First column only**    | `fetchFields<T>()`  | `fetchField<T>(): T?`  | `fetchFieldStrict<T>(): T`  | `forEachField()`  |

```kotlin
// Raw columns - pull what you need out of the Row
val row = session.createNativeQuery("SELECT id, cognomen FROM senators WHERE id = $1").fetchRowStrict(1)
val cognomen: String = row.get("cognomen")

// Mapped straight onto a Kotlin class
val senators: List<Senator> = session.createNativeQuery("SELECT * FROM senators").fetchObjects()

// A scalar - counting the legions
val total: Long = session.createNativeQuery("SELECT count(*) FROM legions").fetchFieldStrict()
```

Object mapping goes through the internal `ResultMapper`, which is also where [custom converters](#per-query-converters) hook in; how columns find their way onto constructor parameters is described in [Type System](type-system.md).

## Nullability and the Strict variants

Two independent things can go missing here, and they are handled by different halves of the API. **How many rows came back** is what the `Strict` suffix governs; **whether the value itself is SQL `NULL`** is governed by how you type `T`. Confusing the two is the usual source of surprise, so here is the whole matrix for the field family:

| Situation                                  | `fetchField<T>()`                              | `fetchFieldStrict<T>()`                        |
|:-------------------------------------------|:-----------------------------------------------|:-----------------------------------------------|
| No rows                                    | `null`                                         | `StatementException(INCORRECT_RESULT_SIZE)`    |
| More than one row                          | `StatementException(INCORRECT_RESULT_SIZE)`    | `StatementException(INCORRECT_RESULT_SIZE)`    |
| One row, value is `NULL`, `T` nullable     | `null`                                         | `null`                                         |
| One row, value is `NULL`, `T` not nullable | `MappingException(REQUIRED_ATTRIBUTE_MISSING)` | `MappingException(REQUIRED_ATTRIBUTE_MISSING)` |

Two rows of that table are worth reading twice. **A missing row never throws in the plain variant** — even `fetchField<String>()` with a non-nullable `T` quietly returns `null` when nothing matched, because the value was never produced to convert. And **a `NULL` value with a non-nullable `T` throws in both variants**, `Strict` or not: that is a mapping failure, not a result-size one. If the column is nullable, say so — `fetchField<String?>()` — and the same goes for the list form, `fetchFields<String?>()`.

`fetchRow` and `fetchObject` follow the first two rows of the table identically. All of them ask the server for at most two rows, so a single-result query that accidentally matches a million does not drag them across the wire before failing.

## Streaming large results

`fetchRows()` and friends materialize everything into memory. For a result set that will not comfortably fit — an audit of every citizen in the census — use the `forEach*` methods, which pull rows in batches and hand them to your block one at a time. `fetchSize` is the batch size and has no default, so you always state it:

```kotlin
session.createNativeQuery("SELECT * FROM citizens WHERE province_id = $1")
    .forEachObject<Citizen>(7, fetchSize = 500) { citizen ->
        writeToExportFile(citizen)
    }
```

Memory stays flat: only `fetchSize` rows are held at a time, regardless of how many the query returns. Three things about that loop are worth knowing before you rely on it.

**The whole iteration is one statement, for as long as it takes.** The driver keeps the result open across batches without closing the exchange, so the server treats it as a single running statement from the first batch to the last. Whatever your block does — writing files, calling services — happens inside that statement's lifetime.

> [!WARNING]
> **`statement_timeout` covers the entire loop, including time spent in your block.** With `statement_timeout = 1s` and a block taking 200 ms per row, an iteration was cancelled after 10 rows and surfaced as `ExecutionAbortedException(QUERY_CANCELED)`. The session recovers and stays usable, but the rest of the rows are gone. If a slow block is unavoidable, either raise the timeout for that session or collect the rows first and do the slow work afterwards.

**The session is busy for the duration.** Your block runs while the driver is still reading the result, so it must not touch that same session — see [below](#do-not-re-enter-the-session-while-rows-are-being-read).

**Exceptions from your block do not escape unchanged.** Anything that is not an `OctaviusException` is wrapped in `MappingException(CONVERSION_ERROR)` with your exception as its `cause`, because the driver has to finish draining the result before it can rethrow. See [Error Handling](exceptions.md#errors-raised-during-row-mapping) if you need your own exception type to survive.

## Do not re-enter the session while rows are being read

Most `fetch*` methods convert each value as it arrives, before the exchange with the server is finished. Any code of yours that runs at that moment — a `forEach*` block, or a custom `ResultConverter` — is therefore executing *inside* an unfinished exchange, and issuing a query on that same session from there collides with it.

This has nothing to do with streaming. A plain `fetchFields<T>()` whose converter queries the session fails exactly like the streaming version does; what matters is only whether your code runs during the read:

| Where your code runs                                 | Querying the same session from there |
|:-----------------------------------------------------|:-------------------------------------|
| `forEach*` block                                     | Collides                             |
| `ResultConverter` under any mapping fetch            | Collides                             |
| After `fetchRows()`, calling `row.get<T>()` yourself | Fine — the exchange is already over  |

The driver refuses such a call outright, **before anything reaches the wire**: you get `InvalidOperationException(EXECUTION_IN_PROGRESS)`, or that same exception as the `cause` of a `MappingException(CONVERSION_ERROR)` when it came from a converter — the wrapper is what carries the `path` to the column being mapped, so read both. Nothing is corrupted, the connection stays healthy, and the next statement works normally — only the operation you interrupted is lost. The same guard covers a `COPY` started from that position.

If code in that position needs the database, give it a second session.

## Statements that return nothing

- **`update()`** — DML that changes rows (`INSERT`, `UPDATE`, `DELETE`). Runs through the Extended Query Protocol with full parameter binding and returns the affected row count as a `Long`.
- **`execute()`** — raw execution with no result and no count: DDL, `SET`, administrative commands. It uses the Simple Query Protocol, so it **cannot bind parameters** and cannot return rows.

```kotlin
val promoted = session.createNativeQuery("UPDATE senators SET rank = $1 WHERE province_id = $2")
    .update("consul", 7)

session.createNativeQuery("CREATE INDEX idx_senators_province ON senators (province_id)").execute()
```

Handing `execute()` a row-returning statement is an error, not a silent discard: it throws `InvalidOperationException(UNEXPECTED_RESULT)`.

> [!IMPORTANT]
> An `INSERT` or `UPDATE` with a `RETURNING` clause returns rows, so it belongs to the `fetch*` family, not `update()`. Getting a generated id back is `fetchFieldStrict<Long>()`:
>
> ```kotlin
> val newId: Long = session.createNativeQuery("INSERT INTO senators (cognomen) VALUES ($1) RETURNING id")
>     .fetchFieldStrict("Cato")
> ```

## Per-query converters

Conversion normally goes through the session's type manager. When one query needs different treatment — a column parsed into a domain type, a DTO serialized a particular way — register a converter on that query instance alone:

- `registerResultConverter(converter)` — database value to Kotlin object.
- `registerParameterConverter(converter)` — Kotlin object to query parameter.

Both return the query, so they chain onto the call:

```kotlin
session.createNativeQuery("SELECT * FROM tabulae WHERE id = $1")
    .registerResultConverter(RomanNumeralConverter())
    .fetchObjectStrict<Tabula>(1)
```

Each query gets its own registry layered over the session's, so a converter registered here is visible to this query and invisible everywhere else. [Type System](type-system.md) covers the layering and how to register converters globally instead.
