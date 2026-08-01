# Functions and Procedures

Calling functions and procedures in PostgreSQL through Octavius is straightforward. However, there are a few important things to keep in mind regarding, `OUT` parameters, and `void`.

## Calling a Procedure

Stored procedures are called using the `CALL` statement. When you want to call a procedure (that doesn't have `OUT` parameters), you should use `update()`.

```kotlin
// Assuming we have a procedure: CREATE PROCEDURE my_proc(a int, b text)
session.createNativeQuery("CALL my_proc($1, $2)")
    .update(10, "Hello")
```

### Procedures with OUT Parameters

If your procedure has `OUT` parameters, you must provide placeholders for them in the query string. You can either type the placeholder manually in your query and skip providing it in Kotlin, or you can pass `null` as the parameter value.
A procedure with `OUT` parameters simply returns a row, so you retrieve the result just like you would with a function (using `fetchRow`, `fetchField` or `fetchObject`).

```kotlin
// Assuming: CREATE PROCEDURE get_stats(uid int, OUT count int)

// Using manual placeholder (only 1 parameter passed from Kotlin)
val stats1 = session.createNativeQuery("CALL get_stats($1, NULL)")
    .fetchField<Int>(123)

// Passing null (2 parameters passed from Kotlin)
val stats2 = session.createNativeQuery("CALL get_stats($1, $2)")
    .fetchField<Int>(123, null)
```

### Passing NULL explicitly with `withPgType`

Sometimes PostgreSQL might complain about ambiguous types when you pass `null` to a procedure or function that is overloaded. In such cases, you can use the `withPgType` wrapper to explicitly declare the type of the null parameter:

```kotlin
import io.github.octaviusframework.driver.type.withPgType

// We pass a typed null for a specific PostgreSQL type
session.createNativeQuery("CALL my_proc($1, $2)")
    .update(10, null.withPgType("text"))
```

## Calling a Function

Functions in PostgreSQL are typically called using a `SELECT` statement.

```kotlin
// Assuming a function: CREATE FUNCTION calculate_tax(amount numeric) RETURNS numeric
val tax = session.createNativeQuery("SELECT calculate_tax($1)")
    .fetchField<Double>(100.0)
```

### Functions Returning Tables

Functions can also return multiple rows (e.g., using `RETURNS TABLE` or `RETURNS SETOF`). In this case, you should query it like a regular table and use `fetchRows()`, `fetchObjects()` or `fetchFields()` to retrieve the data.

```kotlin
// Assuming: CREATE FUNCTION get_active_users() RETURNS TABLE(id int, name text)
val activeUsers = session.createNativeQuery("SELECT * FROM get_active_users()")
    .fetchObjects<User>()
```

### Void Functions

In PostgreSQL, a function returning `void` actually **does** return a single row containing a single void value. It is by default mapped to Kotlin's `Unit` in result and you can retrive it using `fetchField<Unit>()`.

```kotlin
// Fetching the void result as Kotlin's Unit
session.createNativeQuery("SELECT perform_maintenance()")
    .fetchField<Unit>()
```

## Functions vs Procedures in Octavius

| Feature                           | Function                               | Procedure                                   |
|:----------------------------------|:---------------------------------------|:--------------------------------------------|
| **SQL Command**                   | `SELECT`                               | `CALL`                                      |
| **No return / No OUT**            | `fetchField<Unit>()`                   | `update()`                                  |
| **Returns single value / OUT**    | `fetchField<T>()`                      | `fetchField<T>()`                           |
| **Placeholders for OUT**          | Not needed in the call signature       | Required (use explicit `$n` or pass `null`) |
