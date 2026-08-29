# Transaction Plans

*A Roman will was written to be carried out by somebody who was not in the room when it was written. The
clauses were read in order, and a later one leaned on an earlier one having taken effect: an heir had to accept
before the legacies charged on him meant anything. The testator did not execute it. He wrote down what was to
happen, and handed that over.*

A `transaction { }` block is a lambda. A plan is a **value**: it can be built up, counted,
[inspected](#reading-one-that-went-wrong), merged, handed on, and run somewhere that knows nothing about what
went into it.

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

A handle reaches one thing, `value()`: the step's result, whole, as its terminal produced it. The type comes
with it, so reaching *into* a result is ordinary Kotlin, written in `map { }`:

```kotlin
"edict_id" to edict.value().map { it.get<Int>("id") }                      // one column of a fetchRowStrict
"name"     to items.value().map { rows -> rows[2].get<String>("name") }    // one column of one row of many
"ids"      to items.value().map { rows -> rows.map { it.get<Int>("id") } } // one column of every row
```

The third asks for the column at `Int`, so what comes out is a `List<Int>`, which binds as an array. Where
only one column is wanted at all, the terminal says so — `fetchFields<Int>()` and `value()`, with no lambda in
it.

Anything that is not a `TransactionValue` is passed through untouched, so an ordinary parameter map needs no
wrapping: only the values that depend on an earlier step do.

## What Binds and What Does Not

`value()` hands on whatever the terminal produced. Whether the next step can then *bind* it is a separate
question, answered by whether the driver can send that class as a parameter.

| The step's terminal                       | `value()` binds                             |
|-------------------------------------------|---------------------------------------------|
| `fetchField`, `fetchFieldStrict`          | ✅                                           |
| `fetchFields`, `fetchObjects`             | ✅ — a list of scalars binds as an array     |
| `update`                                  | ✅ — the affected-row count                  |
| `fetchObject`, `fetchObjectStrict`        | only if the class is a registered composite |
| `fetchObject*<Map<String, Any?>>`         | ❌ — nothing sends a Map; `spread()` does    |
| `fetchRow`, `fetchRowStrict`, `fetchRows` | ❌ — a `Row` is not something to send        |
| `fetchRow` that matched nothing           | ✅ — binds as `NULL`                         |

A `value()` nothing can send fails where the parameter is encoded, naming the class. `map` is the way across:
it reaches the object and takes the part that can be sent.

## `map` and the Spread

`map { }` applies a function once the value resolves, so a handle on a row can become the one column of it the
next step wants, and a list can become its size, without adding a step just to compute it:

```kotlin
plan.add(
    db.insertInto("audit").values(listOf("summary"))
        .asStep().update("summary" to items.value().map { "granted to ${it.size} provinces" })
)
```

**A transformation is the one place a plan runs code you wrote.** Anything it throws arrives as a
`MappingException` naming the step, the parameter and which `map` of the chain it was, with what was actually
thrown as its cause — a bare `NumberFormatException` out of `map { it.toInt() }` would otherwise travel as
itself, past `dbResult` and `transactionResult`, which catch `OctaviusException` and nothing else:

```
Details: Step 1 of the plan, parameter 'amount': map #2 over step 0.map(#1) threw
         NumberFormatException: For input string: "Gallia"
```

`#2` is the second `map` written on that parameter. A lambda has no name to report and every `map` in a chain
shares the parameter it is on, so the number is the whole of what tells them apart.

An `OctaviusException` raised in there is passed through as it is, which is what a `row.get` for a column the
row has not got raises. That one picks up the same three on its `path` — the breadcrumb the driver's own
layers write to as they unwind, and the only thing that can be added to an exception without replacing it:

```
Details: Column not found: tribute
Path: step 1 -> parameter 'name' -> map #1
```

### The spread

`spread()` marks a value to become **parameters of its own** rather than one parameter. Its entries arrive
under their own keys, and the name it was filed under is dropped:

```kotlin
val original = plan.add(
    db.select("title", "tribute", "province").from("edicts").where("id = @id")
        .asStep().fetchObjectStrict<Map<String, Any?>>("id" to id)
)

plan.add(
    db.insertInto("edict_archive").values(listOf("title", "tribute", "province"))
        .asStep().update("anything" to original.value().spread())
)
```

`@title`, `@tribute` and `@province` are bound; `@anything` is not, and nothing binds it. That name is a
placeholder — a map of parameters needs a key — and it is the one parameter name in a step that means nothing.
This is what makes copying a row with a change or two a single step rather than one parameter per column.

**The map comes from a `fetchObject*` terminal**, which treats a row as a record like any other. That is
where the columns' type is chosen: `Map<String, Any?>` asks the result converters what each column is, and
anything narrower asks them for that instead. `fetchObject` returns `Map<String, Any?>?`, which does not
spread — what an absent row contributes is said in a `map` first, `map { it ?: emptyMap() }`.

The mark is on the parameter slot rather than on the value in it, so everything ordinary Kotlin does to a map
can still be done first:

```kotlin
"anything" to original.value().map { it - "id" + ("archived_at" to Instant.now()) }.spread()
```

`spread()` is the last thing written: what it returns is not a `TransactionValue`, so `map` cannot follow
it.

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

## Reading One That Went Wrong

A failure names its step. `describe()` is what turns that number back into a step:

```kotlin
println(plan.describe())
```

```
TransactionPlan, 2 steps

step 0
  SELECT id, name, amount
  FROM tv_probe
  WHERE id = 1

step 1
  INSERT INTO tv_sink (name, amount)
  VALUES (@name, @amount)
  @name   <- literal
  @amount <- step 0.map(#1).map(#2)
```

This is the piece a plan needs and a block does not. A block is read where it is written; a plan is assembled
by one layer and run by another, so the code holding it when it fails is usually not the code that decided
what went in — and a plan built in a loop has the same SQL and the same parameter names in all twenty of its
steps, which is why the query context on the exception cannot separate the third iteration from the
seventeenth and the step number can.

**What a literal *is* is deliberately not shown.** The wiring is the part that cannot be read off the code
that assembled the plan; a bound value can, and printing one would put a `bytea` parameter or a column of
personal data into whatever the description was written to. The values a step actually ran with are on the
`queryContext` of what it threw, which is bounded for the purpose.

A step whose query cannot be rendered says so in place of its SQL rather than throwing — a plan holding one
is among the things worth describing, and the other nineteen steps still describe.

### Which step is step 2

A step's number, in a description and in a failure alike, is **where it sits in the plan being run**. A
handle's own `toString` is not: it carries the index the handle was *created* at, and after `addPlan` that is
no longer where its step is.

```kotlin
val source = tail.add(…)    // step 0 of tail
head.addPlan(tail)          // head had two steps of its own, so that step is now step 2

source.toString()           // StepHandle(step 0)        - the index it was created at
head.describe()             // @amount <- step 2.map(#1) - where it runs
```

So read the number off `describe()` or off the failure, and treat a handle's own as saying which plan it came
from rather than where it will run. That distinction is also why a handle from another plan is reported the
way it is: there, naming the plan it was created in is the whole point.

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
