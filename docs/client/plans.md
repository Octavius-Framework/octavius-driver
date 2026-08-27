# Transaction Plans

*A Roman will was written to be carried out by somebody who was not in the room when it was written. The
clauses were read in order, and a later one leaned on an earlier one having taken effect: an heir had to accept
before the legacies charged on him meant anything. The testator did not execute it. He wrote down what was to
happen, and handed that over.*

A `transaction { }` block is a lambda. A plan is a **value**: it can be built up, counted, inspected, merged,
handed on, and run somewhere that knows nothing about what went into it.

## When a Block Is Not Enough

Reach for a plan when the sequence is decided somewhere other than where it runs — a screen with a variable
number of rows on it, a service turning a request into operations. The block form would mean passing a lambda
that closes over everything it touched.

```kotlin
val plan = TransactionPlan()

val edictId = plan.add(
    db.insertInto("edicts").values(listOf("title", "tribute")).returning("id")
        .asStep().fetchFieldStrict<Int>("title" to edict.title, "tribute" to edict.tribute)
)

for (item in levy) {
    plan.add(
        db.insertInto("edict_items").values(listOf("edict_id", "province", "amount"))
            .asStep().update(
                "edict_id" to edictId.value(),
                "province" to item.province,
                "amount" to item.amount
            )
    )
}

val results = db.executeTransactionPlan(plan)
val id = results[edictId]
```

Where the sequence is fixed and written out in one place, a block says the same thing with fewer moving parts
and the values in plain Kotlin locals. Use a plan when the sequence is not known where it is executed.

`asStep()` turns any query into a step builder. Its terminals are the `fetch*` family and `update` — the
`forEach*` family is absent on purpose, a plan keeping every result so that later steps can use it, and a walk
over rows too large to hold having nothing to keep. So is `RawQuery.execute()`, which speaks a protocol that
binds nothing and could therefore take no reference to an earlier step.

`executeTransactionPlan` takes the same propagation, isolation, read-only and timeout arguments as
[`transaction`](transactions-failures.md#propagation).

## Handles and What They Reach

`plan.add` returns a `StepHandle`. It is identity and nothing else — two handles are the same handle or they
are not — and it is useful only inside the plan whose `add` returned it.

| From a handle           | Gives                                                        |
|-------------------------|--------------------------------------------------------------|
| `value()`               | The step's result, whole, as its terminal produced it        |
| `field(name, rowIndex)` | One column of one row                                        |
| `column(name)`          | One column of every row, as a list                           |
| `row(rowIndex)`         | A whole row as a map — **spread** into parameters of its own |

Anything that is not one of these is passed through untouched, so an ordinary parameter map needs no wrapping:
only the values that depend on an earlier step do.

## What Fits What

The two axes do not multiply cleanly, and this is the part worth reading before writing a plan. `field`,
`column` and `row` need a result that **has columns**, which only the `fetchRow*` family produces. `value()`
hands on whatever the terminal produced — and whether the next step can then *bind* it is a separate question,
answered by whether the driver can send that class as a parameter.

| The step's terminal                | `value()`                                   | `field` / `column` / `row` |
|------------------------------------|---------------------------------------------|----------------------------|
| `fetchRow`, `fetchRowStrict`       | resolves, but a `Row` cannot be bound       | ✅                          |
| `fetchRows`                        | resolves, but a `List<Row>` cannot be bound | ✅                          |
| `fetchObject`, `fetchObjectStrict` | only if the class is a registered composite | ❌ "no columns to take"     |
| `fetchField`, `fetchFieldStrict`   | ✅                                           | ❌                          |
| `fetchFields`, `fetchObjects`      | ✅ — a list of scalars binds as an array     | ❌                          |
| `update`                           | ✅ — the affected-row count                  | ❌                          |
| `fetchRow` that matched nothing    | ✅ — binds as `NULL`                         | ❌                          |

The failures say which is which: reaching for a column of something that has none names the class it got and
points at `value()`, and a `value()` nothing can send fails where the parameter is encoded, naming the class.

**A value carried between steps is what the result converters make of it**, not what its codec left behind —
the same thing `row.get<Any?>` gives anywhere else. An enum column arrives as the enum and a `jsonb` column as
a `JsonElement`, each of which names its own PostgreSQL type on the way back out. Carrying the codec's `String`
instead would declare it `text`, and the server would refuse it at the very column it had come from.

## `map` and the Spread

`map { }` applies a function once the value resolves, so a handle on an id can become a formatted reference and
a list can become its size, without adding a step just to compute it:

```kotlin
plan.add(
    db.insertInto("audit").values(listOf("summary"))
        .asStep().update("summary" to itemIds.column("id").map { "granted to ${it.size} provinces" })
)
```

It is also the way across for a result nothing can bind: `value()` reaches the object, `map` takes the part
that can be sent.

**A transformation is the one place a plan runs code you wrote.** Anything it throws arrives as a
`MappingException` naming the parameter, with what was actually thrown as its cause — a bare
`ClassCastException` out of `map { it as Int }` would otherwise travel as itself, past `dbResult` and
`transactionResult`, which catch `OctaviusException` and nothing else. An `OctaviusException` raised in there is
passed through as it is.

### The spread

`row()` is the one value that is **not** assigned under the name it was filed under. Its entries become
parameters in their own right, under the row's own column names:

```kotlin
val original = plan.add(db.select("*").from("edicts").where("id = @id").asStep().fetchRowStrict("id" to id))

plan.add(
    db.insertInto("edict_archive").values(listOf("title", "tribute", "province"))
        .asStep().update("anything" to original.row())
)
```

`@title`, `@tribute` and `@province` are bound; `@anything` is not, and nothing binds it. That name is a
placeholder — a map of parameters needs a key — and it is the one parameter name in a step that means nothing.
This is what makes copying a row with a change or two a single step rather than one parameter per column.

The spread belongs to `row()` itself, not to the map inside it, so putting a `map` around one takes it away:
what comes out is one ordinary parameter again, under the name it was filed under, which then starts mattering.

## Merging Plans

```kotlin
head.addPlan(tail)
```

One layer builds the plan for its part of the work, another for its part, and something above them runs the two
as one transaction without knowing what either put in. Handles the merged plan handed out keep working, a
result being filed under the handle itself rather than under a position — so where a step ends up in the merged
sequence changes nothing about how it is referred to.

`tail` is not consumed and not changed: it can still be run on its own, or merged elsewhere.

Merging the same plan twice is refused — directly, or through two plans that both hold it. Its steps would run
twice under one handle, and only the last result of each would be reachable.

## Checked Before It Runs

A plan is validated before its transaction is opened rather than partway through it:

- **Every step's SQL is rendered.** An `UPDATE` that never got its `WHERE` is refused naming the step, instead
  of surfacing once the steps before it have already done their work — which on a plan whose first eighteen
  steps are slow matters rather a lot.
- **Every parameter is walked for handles**, through however many `map { }` wrap them, so one belonging to
  another plan is caught before anything runs.

An empty plan returns an empty result without opening a transaction at all.

What is deliberately **not** checked is a step depending on a later one. A handle comes from `add` and nowhere
else, and `addPlan` appends whole plans in order, so a forward reference has no way to be written. Checking for
one would describe a hazard the design has closed.

The cost is one extra `toSql()` per step, rendering not being cached. Against a transaction's round trips that
is nothing.

## Running One Twice

Executing a plan does not consume it. The steps are copied out and the results kept in a map of the run's own,
with nothing written back:

```kotlin
repeat(3) { attempt ->
    try {
        return db.executeTransactionPlan(plan)
    } catch (e: ConcurrencyException) {
        if (e.reason == ConcurrencyExceptionReason.UNKNOWN || attempt == 2) throw e
    }
}
```

Retrying a serialization failure or a deadlock is a plain loop rather than a rebuild — and each run resolves its
handles against its own results, so the second run reads what the second run produced.

The loop belongs exactly here and not further in. A retry has to restart the **whole** transaction, only a new
one getting a new snapshot, so the wrapper goes around the frame that owns the boundary — and
`executeTransactionPlan` is that frame, since it is what opens the transaction. See
[Catching at the Right Altitude](../driver/exceptions.md#catching-at-the-right-altitude) for what the server
does to a doomed transaction in the meantime.

## Next

- [Transactions and Failures](transactions-failures.md) — the propagation and timeout arguments a plan takes
- [Queries](queries.md) — what `asStep()` is called on
