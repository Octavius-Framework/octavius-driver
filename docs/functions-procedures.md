# Functions and Procedures

Calling PostgreSQL functions and procedures through Octavius follows the same query API you already know from `NativeQuery` and `NamedParameterQuery` — the only real gymnastics involved
are around `OUT` parameters and the `void` type. This document walks through both, borrowing a small slice of the Roman civil service for its examples.

## Calling a Procedure

Stored procedures are invoked with `CALL`. For a procedure without `OUT` parameters, `update()` is the method you want — it tells Octavius "I don't expect a row back, just tell me it ran."

```kotlin
// CREATE PROCEDURE enroll_legionary(cohort_id int, recruit_name text)
session.createNativeQuery("CALL enroll_legionary($1, $2)")
    .update(10, "Marcus Aurelius")
```

### Procedures with OUT Parameters

Procedures with `OUT` parameters are a bit different: PostgreSQL still expects a placeholder for every position in the call, including the outbound ones. You have two options — write the placeholder directly into the SQL and simply skip it on the Kotlin side, or pass `null` explicitly and let it occupy that position.

Either way, a procedure with `OUT` parameters behaves like a query returning a single row, so fetch it with `fetchRow`, `fetchField`, or `fetchObject` — never `update()`.

```kotlin
// CREATE PROCEDURE province_census(uid int, OUT population int)

// Option A: leave the OUT placeholder in the SQL, pass only the real argument
val population1 = session.createNativeQuery("CALL province_census($1, NULL)")
    .fetchField<Int>(7)

// Option B: pass null explicitly, occupying the second position
val population2 = session.createNativeQuery("CALL province_census($1, $2)")
    .fetchField<Int>(7, null)
```

### Passing a Typed NULL with `withPgType`

When a procedure or function is overloaded, a bare `null` can leave PostgreSQL unsure which variant you mean. `withPgType` removes the ambiguity by declaring the exact PostgreSQL type the `null` should carry:

```kotlin
import io.github.octaviusframework.driver.type.withPgType

// CREATE PROCEDURE appoint_consul(candidate_id int, title text)
session.createNativeQuery("CALL appoint_consul($1, $2)")
    .update(12, null.withPgType("text"))
```

## Calling a Function

Functions are called the ordinary way, through `SELECT`.

```kotlin
// CREATE FUNCTION calculate_tributum(amount numeric) RETURNS numeric
val tribute = session.createNativeQuery("SELECT calculate_tributum($1)")
    .fetchField<Double>(1500.0)
```

### Functions Returning Tables

Table-returning functions (`RETURNS TABLE` / `RETURNS SETOF`) are queried exactly like a regular table — reach for `fetchRows()`, `fetchObjects()`, or `fetchFields()`.

```kotlin
// CREATE FUNCTION active_legions() RETURNS TABLE(id int, name text)
val legions = session.createNativeQuery("SELECT * FROM active_legions()")
    .fetchObjects<Legion>()
```

### Void Functions

A `void`-returning function in PostgreSQL still returns a single row carrying one void value — it doesn't just vanish. Octavius maps this to Kotlin's `Unit`, retrievable via `fetchField<Unit>()`.

```kotlin
// CREATE FUNCTION restore_pax_romana()
session.createNativeQuery("SELECT restore_pax_romana()")
    .fetchField<Unit>()
```

## Functions vs Procedures in Octavius

| Feature                      | Function             | Procedure                          |
|:-----------------------------|:---------------------|:-----------------------------------|
| **SQL command**              | `SELECT`             | `CALL`                             |
| **No return / no OUT**       | `fetchField<Unit>()` | `update()`                         |
| **Single value / OUT param** | `fetchField<T>()`    | `fetchField<T>()`                  |
| **Placeholders for OUT**     | Not applicable       | Required — explicit `$n` or `null` |