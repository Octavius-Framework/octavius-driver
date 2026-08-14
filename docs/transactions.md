# Transactions

Octavius offers two ways to manage transactions: scoped blocks that handle commit and rollback for you, and direct control over the boundaries yourself. The block API is what you want almost always; the direct model exists for the cases where the decision to commit is made far away from the code that runs the queries.

Contents:
* [Auto-commit, the default](#auto-commit-the-default)
* [Block API](#block-api)
* [Manual control](#manual-control)
* [Savepoints](#savepoints)
* [Transaction state](#transaction-state)
* [Isolation levels and read-only mode](#isolation-levels-and-read-only-mode)

## Auto-commit, the default

A fresh session runs in **auto-commit mode** (`session.autoCommit == true`): every statement is its own transaction, committed the moment it succeeds. Nothing is grouped, nothing is held open.

Grouping statements means leaving that mode — either through a block, or by setting `autoCommit = false` yourself.

## Block API

Available through `session.transaction`. Both blocks commit on success, roll back on any `Throwable`, and restore the previous auto-commit state on the way out.

### `required { ... }`

Runs the block inside a transaction, joining one if it is already open.

- **Nothing active:** opens a transaction, commits on success, rolls back on exception, restores `autoCommit` afterwards.
- **Transaction already active:** the block simply runs inside it — no new boundary, no savepoint. An exception propagates and rolls back the *whole* outer transaction.

```kotlin
session.transaction.required {
    createNativeQuery("INSERT INTO senators (name) VALUES ($1)").update("Cato")
    createNativeQuery("INSERT INTO senate_logs (event) VALUES ($1)").update("New senator inducted")
}
```

### `nested { ... }`

Runs the block inside a savepoint, so its failure can be absorbed without losing the surrounding work.

- **Transaction already active:** takes a savepoint, releases it on success, rolls back to it on exception — the outer transaction survives and stays usable.
- **Nothing active:** there is nothing to nest inside, so it behaves exactly like `required`.

```kotlin
session.transaction.required {
    createNativeQuery("INSERT INTO curia_journal (msg) VALUES ('session opened')").update()

    try {
        session.transaction.nested {
            createNativeQuery("INSERT INTO auspicia (reading) VALUES ('unfavorable')").update()
        }
    } catch (e: Exception) {
        println("The omens were bad, but the journal entry still stands!")
    }
}
```

Note the `try` around the nested block: `nested` rolls back to its savepoint and then **rethrows**. Rolling back is not the same as swallowing — if you want the outer transaction to carry on, you have to catch it, exactly as above. Without the `catch`, the exception would keep travelling and take the outer transaction down with it.

> [!IMPORTANT]
> Inside both blocks the receiver is `OctaviusSessionOperations`, which deliberately hides `commit()`, `rollback()` and `autoCommit`. The block owns the transaction lifecycle; nothing inside it can quietly commit half of it.

Both blocks return whatever the block returns, so they compose with normal Kotlin code:

```kotlin
val newSenatorId: Long = session.transaction.required {
    createNativeQuery("INSERT INTO senators (name) VALUES ($1) RETURNING id")
        .fetchFieldStrict<Long>("Cato")
}
```

## Manual control

For integrating with an existing transaction manager, or when commit and rollback are decided elsewhere, drive the boundaries through `OctaviusSession` directly. A few behaviours here are easy to be surprised by, so they are worth stating plainly.

**Leaving auto-commit opens a transaction immediately.** `session.autoCommit = false` sends a `BEGIN` right away — the session is `IN_TRANSACTION` from that line on, not from the first query.

**`commit()` and `rollback()` start the next transaction.** Both end the current transaction and immediately open a fresh one, so the session stays `IN_TRANSACTION` until you turn auto-commit back on. There is no gap where the session is idle, and no need to "begin" anything before the next statement.

**The server sees an open transaction the whole time.** Since the `BEGIN` is eager rather than deferred to the first statement, the backend reports `idle in transaction` from the moment you leave auto-commit until you return to it — including right after a `commit()`, where the chained `BEGIN` has already opened the next one. Drivers that defer the `BEGIN` until the first statement never show this, so a connection that looked inert under another driver does not look inert here.

**That open transaction holds almost nothing.** `idle in transaction` reads alarming in a monitoring dashboard, so it is worth being precise about what it is costing you before the first statement runs:

| Worry                        | Before the first statement                                                                                                                                |
|:-----------------------------|:----------------------------------------------------------------------------------------------------------------------------------------------------------|
| Is anything locked?          | No table, no row. The one entry in `pg_locks` is the transaction's lock on its own *virtual* id — a marker every transaction holds, and nothing waits on. |
| Is `VACUUM` held back?       | No. That needs a snapshot, and none is taken until a statement runs — `backend_xmin` stays empty.                                                         |
| Is a transaction id used up? | No. A real transaction id (`xid`) is assigned on the first *write*, so `backend_xid` is empty too.                                                        |

So the eager `BEGIN` costs one extra round trip and a backend that looks busier than it is. Only one consequence has teeth: **`idle_in_transaction_session_timeout`** drops a session left sitting in that window, and it reaches you as `ExecutionAbortedException(TRANSACTION_TIMEOUT)`.

Once statements start running, the ordinary rules apply again — a write consumes a transaction id, and under `REPEATABLE READ` or `SERIALIZABLE` the snapshot lasts the whole transaction, which *does* hold back cleanup.

> [!WARNING]
> **A pool configured with `auto-commit=false` parks its idle connections inside a transaction.** Pools apply the auto-commit setting while preparing a connection, not while you use it, so with HikariCP's `isAutoCommit = false` every connection in the pool reports `idle in transaction` for as long as it sits there — measured on a pool of three with a single borrow, all three did, including the two nobody had touched. Where the server sets `idle_in_transaction_session_timeout`, it will drop those connections while they are doing nothing and the pool will keep replacing them. Leave the pool on the default `auto-commit=true` and open transactions where you actually need them, or make sure that timeout is not set aggressively.

> [!WARNING]
> **Setting `autoCommit = true` commits whatever is open — it does not discard it.** Returning to auto-commit mode issues a `COMMIT`, so work you never explicitly committed is made permanent rather than thrown away. If you meant to abandon it, call `rollback()` *before* flipping the flag. This is the JDBC contract for `setAutoCommit` rather than an Octavius decision, but it catches people out often enough to be worth spelling out.

```kotlin
session.autoCommit = false      // BEGIN
try {
    session.createNativeQuery("UPDATE aerarium SET balance = balance - 100 WHERE province_id = $1").update(1)
    session.createNativeQuery("UPDATE aerarium SET balance = balance + 100 WHERE province_id = $1").update(2)
    session.commit()            // COMMIT, and a new transaction begins
} catch (e: Exception) {
    session.rollback()          // ROLLBACK, and a new transaction begins
    throw e
} finally {
    session.autoCommit = true   // commits the (empty) transaction still open here
}
```

Calling `commit()` or `rollback()` while auto-commit is on throws `InvalidOperationException(AUTO_COMMIT_VIOLATION)` — there is no transaction of yours to end.

## Savepoints

Savepoints undo part of a transaction without abandoning all of it. `nested { }` is the ergonomic wrapper; these are the primitives underneath, and they all require auto-commit to be off:

- `session.setSavepoint()` — an unnamed savepoint, `octavius_savepoint_1`, `_2`, … on the server.
- `session.setSavepoint(name)` — a named one. The name is quoted as a PostgreSQL identifier, so spaces and odd characters are safe.
- `session.rollback(savepoint)` — undoes everything since that savepoint, leaving the transaction alive.
- `session.releaseSavepoint(savepoint)` — drops the savepoint; it can no longer be rolled back to.

```kotlin
session.autoCommit = false
try {
    session.createNativeQuery("UPDATE aerarium SET balance = balance - 100 WHERE province_id = 1").update()

    val sp = session.setSavepoint()
    try {
        session.createNativeQuery("UPDATE aerarium SET balance = balance + 100 WHERE province_id = 2").update()
    } catch (e: Exception) {
        session.rollback(sp) // revert only the second transfer, keep the first
    }

    session.commit()
} catch (e: Exception) {
    session.rollback()
} finally {
    session.autoCommit = true
}
```

An unnamed savepoint answers `getSavepointId()` and throws on `getSavepointName()`; a named one does the reverse, both with `InvalidOperationException(INVALID_SAVEPOINT)`.

## Transaction state

`session.transactionState` reports what the *server* thinks, taken from the status flag PostgreSQL attaches to every completed exchange — not from a client-side guess:

| State            | Meaning                                                                       |
|:-----------------|:------------------------------------------------------------------------------|
| `IDLE`           | No transaction open. Every statement commits on its own.                      |
| `IN_TRANSACTION` | A transaction is open and healthy.                                            |
| `FAILED`         | A statement inside the transaction failed; PostgreSQL is awaiting a rollback. |

The `FAILED` state is the one worth knowing about. Once a statement inside a transaction errors, PostgreSQL refuses everything that follows until the transaction is unwound — so the second failure you see is not a new bug:

```kotlin
session.autoCommit = false
session.createNativeQuery("INSERT INTO senators (id) VALUES (1)").update()
runCatching { session.createNativeQuery("INSERT INTO senators (id) VALUES (1)").update() } // duplicate key

session.transactionState                                  // FAILED
session.createNativeQuery("SELECT 1").fetchFieldStrict<Int>()
// throws StatementException(INVALID_TRANSACTION_STATE) - the transaction is aborted

session.rollback()
session.transactionState                                  // IN_TRANSACTION again, and usable
```

Two ways out: `rollback()`, which discards the whole transaction and opens a fresh one, or — if you saw it coming — a `nested { }` block around the risky part, whose savepoint rollback clears the failure while keeping everything before it. That is the practical reason to reach for `nested` rather than `required`.

## Isolation levels and read-only mode

```kotlin
import io.github.octaviusframework.driver.session.TransactionIsolationLevel

session.transactionIsolationLevel = TransactionIsolationLevel.SERIALIZABLE
session.readOnly = true
```

`TransactionIsolationLevel` offers `READ_UNCOMMITTED`, `READ_COMMITTED` (PostgreSQL's default), `REPEATABLE_READ` and `SERIALIZABLE`. PostgreSQL treats `READ_UNCOMMITTED` as `READ_COMMITTED` — it has no dirty reads to offer. Anything outside the four, such as JDBC's `TRANSACTION_NONE`, is rejected with `InvalidOperationException(INVALID_ARGUMENT)`.

> [!NOTE]
> Both settings change the **session default**, not just the current transaction: each issues a `SET SESSION CHARACTERISTICS AS TRANSACTION ...`, plus a `SET TRANSACTION ...` when a transaction is already open. They therefore apply to every later transaction on that connection — but on a pooled connection the pool cleans up after you. HikariCP tracks `transactionIsolation` and `readOnly` and restores its own defaults when the connection returns, so a change made through these properties does not follow the connection to its next borrower.

> [!WARNING]
> Setting the same thing by running the SQL yourself — `session.createNativeQuery("SET SESSION CHARACTERISTICS AS TRANSACTION ISOLATION LEVEL SERIALIZABLE").execute()` — escapes that tracking completely. The pool has no idea anything changed, so the next borrower inherits it: verified on a pool of one, where the isolation level set that way was still `serializable` on the following borrow, while the same change made through `transactionIsolationLevel` was reset to `read committed`. Prefer the properties over hand-written SQL, and remember this applies to every other session-level setting you change with a statement — `search_path`, `statement_timeout` and the rest. Those belong in the connection's [startup parameters](initialization.md#startup-parameters), where every connection in the pool gets them.

Serializable transactions can fail at commit even though every statement succeeded. That arrives as a `ConcurrencyException` — `SERIALIZATION_FAILURE` for `40001`, `DEADLOCK_DETECTED` for `40P01` — and both are worth retrying, unlike most failures. See [Error Handling](exceptions.md) for the full picture.
