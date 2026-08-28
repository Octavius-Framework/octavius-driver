# Queries

*The praetor's edict published the formulae in advance: a fixed core naming the claim, and beside it the
clauses a case might or might not need — an exceptio if the defendant had a defence, a replicatio if the
claimant had an answer to it. The parties did not compose the words. They settled which clauses applied, and
the formula was assembled from that.*

The builders here do the assembling and nothing else. What goes in each clause is SQL, and it is passed through
unread.

> The terminal methods — `fetchRows`, `fetchObjects<T>`, `fetchField<T>`, `forEach*`, `update` — are the
> driver's own, under the driver's names, with the driver's meanings. This page does not describe them; see
> [Executing Queries](../driver/queries.md) for the whole family, nullability, the strict variants and
> streaming.

## Every Clause Is SQL

```kotlin
val senators = db.select("id", "cognomen", "count(*) OVER () AS total")
    .from("senate s JOIN provinces p ON p.id = s.province_id")
    .where("p.name = @province AND s.rank <> 'RETIRED'")
    .groupBy("s.id, s.cognomen")
    .having("count(*) > 1")
    .orderBy("s.cognomen DESC NULLS LAST")
    .fetchObjects<Senator>("province" to "Gallia")
```

Nothing there is parsed. `from` takes the join because that *is* the join; `select` takes a window function
because a column list is a list of expressions. There is no `.join()`, no `.eq()`, and no expression tree,
because a second dialect of SQL to learn buys nothing when the first one is already exact.

What the builder contributes is the mechanical part:

- the keywords and the order they have to go in,
- the column list paired with its own `@name` placeholders, so the two cannot drift apart,
- the clauses that disappear when they have nothing to say.

Every builder carries `with(name, query)` and `recursive()` for a `WITH` clause, so a CTE — recursive or not —
needs no raw SQL either.

### The four builders

| Builder                | Required before it renders                                     |
|------------------------|----------------------------------------------------------------|
| `db.select(…)`         | `from`, unless the projection needs no table                   |
| `db.insertInto(table)` | Something to insert: `value`, `values`, or `fromSelect`        |
| `db.update(table)`     | Something to set, **and a `WHERE`**                            |
| `db.deleteFrom(table)` | **A `WHERE`**                                                  |

**An `UPDATE` or `DELETE` built here requires a `WHERE`.** Emptying a table is a statement worth having to
mean, and a builder that lets it fall out of a `null` filter is how it happens by accident. Where it is meant,
`rawQuery` says so in the diff and in code review:

```kotlin
db.rawQuery("DELETE FROM staging_census").update()
```

The failure is an `InvalidOperationException` naming the table and pointing at `rawQuery`, and it is raised
when the SQL is rendered — which is when a terminal runs, or when
[a plan is validated](plans.md#checked-before-it-runs).

## Clauses That Disappear

This is the reason to reach for a builder at all:

```kotlin
db.select("id", "cognomen")
    .from("senate")
    .where(filter)          // null or blank: no WHERE at all
    .orderBy(sortColumn)    // null: no ORDER BY
    .limit(pageSize)        // null: no LIMIT
    .fetchObjects<Senator>(params)
```

A filter assembled at runtime is otherwise a string-concatenation problem with a dangling `AND` in it. Here the
clause either has something to say or is not rendered.

`offset` and `page` are the exception and take non-null values, an offset without a limit being a question
rather than a filter. `page(page, size)` is `limit(size).offset(page * size)`, counted from zero.

`forUpdate(of, mode)` adds row locking, `mode` being the driver's `LockWaitMode` — `NOWAIT` or `SKIP LOCKED`.

## `QueryFragment`

A condition and the parameters it names belong together; kept apart, one of them eventually goes missing.

```kotlin
val filters = listOfNotNull(
    request.province?.let { "p.name = @province" withParam ("province" to it) },
    request.minRank?.let { "s.rank >= @rank" withParam ("rank" to it) },
    request.cognomen?.let { "s.cognomen ILIKE @cognomen" withParam ("cognomen" to "%$it%") }
)

val where = filters.join(" AND ")

val senators = db.select("id", "cognomen").from("senate s JOIN provinces p ON p.id = s.province_id")
    .where(where.sql)
    .fetchObjects<Senator>(where.params)
```

`withParam` takes one parameter and `withParams` a map. `join` merges what survives:

- **Empty fragments are dropped**, so a `listOfNotNull` with three `null`s in it joins to an empty fragment,
  and `where("")` renders no `WHERE`. `prefix` and `postfix` go with it, so nothing can produce a bare
  `WHERE ()`.
- **Each fragment is parenthesised.** Not cosmetic: `"a = 1 OR b = 2"` joined to `"c = 3"` with `" AND "` is
  `(a = 1 OR b = 2) AND (c = 3)`, and without the parentheses `AND` binds tighter and quietly returns different
  rows. Turn it off with `addParenthesis = false` only where every fragment is a single term.
- **Two fragments naming one parameter with different values is refused.** One would replace the other, and
  which one would depend on the order the filters happened to be listed in.

`prefix` is for a hand-written query, where nothing supplies the keyword:

```kotlin
val where = filters.join(" AND ", prefix = "WHERE ")
db.rawQuery("SELECT id, cognomen FROM senate ${where.sql}").fetchObjects<Senator>(where.params)
```

The builders supply their own keyword, so leave it empty there.

## A Query Is a Value

Nothing is sent until a terminal is called, so a query can be built, passed around, rendered and copied.

**`toSql()`** renders what it would send. That makes it composable, because no query carries its own
parameters: the `@name` placeholders survive being embedded, and are bound by whoever runs the outer statement.

```kotlin
val recent = db.select("id", "province_id").from("edicts").where("issued_at > @since")

val counts = db.rawQuery(
    """
    WITH recent AS (${recent.toSql()})
    SELECT province_id, count(*) FROM recent GROUP BY province_id
    """
).fetchRows("since" to cutoff)
```

The same rendered query drops into a subquery or an arm of a `UNION` on the same terms. Rendering is not cached
and costs whatever assembling costs — nothing once per request, and not something to do per row.

**`copy()`** gives an independent builder with the same clauses, for variants off a shared base:

```kotlin
val base = db.select("id", "cognomen").from("senate")

val active   = base.copy().where("rank <> 'RETIRED'")
val retired  = base.copy().where("rank = 'RETIRED'")
```

`base` is untouched, and anything registered with `registerResultConverter` comes along with the copy.

## Raw SQL

`db.rawQuery(sql)` is a `RunnableQuery` like any other: same terminal family, same session handling, same
`toSql()`, and it can be a plan step.

The difference is not reach. Every builder clause is passed-through SQL, `recursive()` is on all four of them,
and a window function or a `DISTINCT ON` goes in `select` — there is not much the builders cannot say. Reach
for `rawQuery` when you would rather write the statement whole: because it already exists, because it came from
somewhere else, or because assembling it a clause at a time buys nothing.

It also has one terminal nothing else has:

```kotlin
db.rawQuery(
    """
    CREATE TABLE IF NOT EXISTS census (id SERIAL PRIMARY KEY, name TEXT);
    CREATE INDEX IF NOT EXISTS census_name ON census (name)
    """
).execute()
```

`execute()` speaks the **Simple Query Protocol**, which binds nothing: the SQL reaches the server exactly as
written, so an `@name` left in it arrives as literal text rather than as a parameter. That is why no builder
offers it — a builder always has values to bind — and why an `INSERT`, `UPDATE` or `DELETE` written by hand
belongs in `update()` instead. What it does accept is several statements separated by `;` in one round trip,
which PostgreSQL wraps in an implicit transaction. A statement that returned rows is refused, unless
`execute(ignoreRows = true)` says to drop them instead — which is what a script written elsewhere needs, a
`pg_dump` one emitting `SELECT pg_catalog.setval(...)` for every sequence. There is no reading those rows
from here either way.

## Per-Query Converters

The driver gives every query converter registries of its own, chained to the session's and thrown away with the
query. `registerResultConverter` and `registerParameterConverter` are how a builder reaches them:

```kotlin
val envelopes = db.select("payload").from("dispatches")
    .registerResultConverter(SealedEnvelopeConverter)
    .where("legion_id = @id")
    .fetchObjects<Envelope>("id" to 7)
```

A mapping that one report needs is registered for that report and nowhere else. Registering it on the type
manager instead would reach **every session pointing at that database**, this being a JVM-wide registry keyed
by the physical database — see [Type System](../driver/type-system.md#scope-a-session-handle-over-global-state).

Both return the builder's own type, so they can sit anywhere in the chain rather than having to come last, and
`copy()` carries them. Registered converters are consulted ahead of the session's, and a later registration
wins over an earlier one.

The most common use has its own shorthand: reading `dynamic_dto` payloads under a different `Json` for one
query. See [A Different `Json` for One Query](dynamic-dto.md#a-different-json-for-one-query).

## Next

- [Transactions and Failures](transactions-failures.md) — what happens around a query that goes wrong
- [Transaction Plans](plans.md) — when the sequence of queries is itself data
