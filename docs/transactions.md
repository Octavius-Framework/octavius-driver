# Transactions

Octavius gives you flexible transaction management, suited both to simple block-based operations and to advanced, manual, fine-grained control.

## Auto-Commit Behavior
By default, the driver runs in **auto-commit mode** (`session.autoCommit = true`) — every query you run is its own self-contained transaction, committed automatically the moment it succeeds.

To group several operations into one transaction, either use the lambda (block-based) API or turn `autoCommit` off manually.

## 1. Lambda Model (Block-based API)
This is the recommended approach for everyday transaction management. It wraps your code safely — committing on success, rolling back automatically the moment something throws.
Available via `session.transaction`.

### `required { ... }`
Runs a block of code inside a transaction.
- If nothing's active (`autoCommit = true`), it opens a new transaction, commits on success, rolls back on exception, and restores the previous `autoCommit` state afterward.
- If a transaction is already active, the block simply runs inside it.

```kotlin
session.transaction.required {
    createNativeQuery("INSERT INTO senators (name) VALUES ($1)").update("Cato")
    createNativeQuery("INSERT INTO senate_logs (event) VALUES ($1)").update("New senator inducted")
}
```

### `nested { ... }`
Runs a block inside a nested transaction scope, backed by **Savepoints**.
- With a transaction already active, it opens a new savepoint — released on success, rolled back to (without touching the outer transaction) on failure.
- With nothing active, it behaves exactly like `required`.

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

> [!IMPORTANT]
> Inside the lambda blocks (`required` and `nested`), the receiver is scoped to `OctaviusSessionOperations`. This deliberately hides manual transaction methods (`commit()`, `autoCommit`) so nothing outside the block can interfere with its automated lifecycle.

## 2. Direct Model (Manual Control)
For low-level, manual control — integrating with legacy systems, building a custom transactional wrapper for a framework, or deciding commit/rollback far from where the query actually runs — use the direct methods on `OctaviusSession`.

Start a manual transaction by disabling auto-commit:
```kotlin
session.autoCommit = false
```

From there, you control the boundaries yourself:
- `session.commit()` — commits the current transaction.
- `session.rollback()` — rolls back the current transaction.

### Savepoints
Savepoints let you undo part of a transaction without aborting the whole thing. Fully supported in manual mode:
- `val sp = session.setSavepoint()` — creates a new savepoint.
- `session.rollback(sp)` — undoes everything done since that savepoint.
- `session.releaseSavepoint(sp)` — releases the savepoint; it can no longer be rolled back to.

```kotlin
session.autoCommit = false
try {
    // Move funds out of one province's treasury
    session.createNativeQuery("UPDATE aerarium SET balance = balance - 100 WHERE province_id = 1").update()

    val sp = session.setSavepoint()
    try {
        // ...and into another's
        session.createNativeQuery("UPDATE aerarium SET balance = balance + 100 WHERE province_id = 2").update()
    } catch (e: Exception) {
        session.rollback(sp) // Revert only the second transfer
    }

    session.commit()
} catch (e: Exception) {
    session.rollback()
} finally {
    session.autoCommit = true
}
```
