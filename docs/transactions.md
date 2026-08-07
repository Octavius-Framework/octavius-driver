# Transactions

Octavius Driver provides flexible transaction management designed to suit both simple block-based operations and advanced, manual, fine-grained control.

## Auto-Commit Behavior
By default, the driver operates in **auto-commit mode** (`session.autoCommit = true`). This means that every query you execute is treated as a separate, self-contained transaction that is automatically committed when it succeeds.

To group multiple operations into a single transaction, you need to either use the lambda (block-based) API or switch off `autoCommit` manually.

## 1. Lambda Model (Block-based API)
The block-based API is the recommended approach for standard transaction management. It safely wraps your code, ensuring that the transaction is committed upon success or automatically rolled back if an exception occurs.
This API is available via `session.transaction`.

### `required { ... }`
Executes a block of code within a transaction.
- If no transaction is active (`autoCommit = true`), it starts a new transaction. If the block completes successfully, it commits. If an exception is thrown, it rolls back. Afterward, it restores the `autoCommit` state.
- If a transaction is already active, the block is executed within the existing transaction context.

```kotlin
session.transaction.required {
    createNativeQuery("INSERT INTO users (name) VALUES ($1)").update("Alice")
    createNativeQuery("INSERT INTO logs (event) VALUES ($1)").update("User created")
}
```

### `nested { ... }`
Executes a block of code within a nested transaction scope using **Savepoints**.
- If a transaction is already active, it creates a new savepoint. If the block succeeds, the savepoint is released. If it fails, the transaction is safely rolled back to this savepoint without discarding the entire outer transaction.
- If no transaction is active, it acts exactly like `required`.

```kotlin
session.transaction.required {
    createNativeQuery("INSERT INTO audit (msg) VALUES ('start')").update()
    
    try {
        session.transaction.nested {
            createNativeQuery("INSERT INTO unsafe_table (data) VALUES ('risky')").update()
        }
    } catch (e: Exception) {
        println("Nested part failed, but the audit insert is still valid!")
    }
}
```

> [!IMPORTANT]
> Inside the lambda blocks (`required` and `nested`), the receiver is scoped to `OctaviusSessionOperations`. This intentionally hides manual transaction methods (like `commit()` or `autoCommit`) to prevent manual interference with the block's automated lifecycle.

## 2. Direct Model (Manual Control)
If you need low-level, manual control over transactions (for instance, when integrating with legacy systems, implementing custom transactional wrappers for frameworks, or when the commit/rollback decision must be made far from the execution scope), you can use the direct methods available on the `OctaviusSession`.

To start a manual transaction, disable auto-commit:
```kotlin
session.autoCommit = false
```

You can then manually control the transaction boundaries:
- `session.commit()` – Commits the current transaction.
- `session.rollback()` – Rolls back the current transaction.

### Savepoints
Savepoints allow you to roll back parts of a transaction without aborting the entire transaction. They are fully supported in the manual mode:
- `val sp = session.setSavepoint()` – Creates a new savepoint.
- `session.rollback(sp)` – Rolls back all changes made after the savepoint was created.
- `session.releaseSavepoint(sp)` – Releases the savepoint, destroying it (it can no longer be rolled back to).

```kotlin
session.autoCommit = false
try {
    session.createNativeQuery("UPDATE accounts SET balance = balance - 100 WHERE id = 1").update()
    
    val sp = session.setSavepoint()
    try {
        session.createNativeQuery("UPDATE accounts SET balance = balance + 100 WHERE id = 2").update()
    } catch (e: Exception) {
        session.rollback(sp) // Revert only the second update
    }
    
    session.commit()
} catch (e: Exception) {
    session.rollback()
} finally {
    session.autoCommit = true
}
```
