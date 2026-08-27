# `dynamic_dto`

*A titulus was carried in front of the thing it named — the placard borne ahead of a captive in a triumph, hung
on a statue, nailed above a condemned man. What was on it was not a description of the class of object. It said
what **this one** was, and it travelled with it.*

One column holding whichever of several unrelated shapes a row happens to carry. A `COMPOSITE` fixes the shape
at schema level; this fixes it per row, and the discriminator is stored in the data beside the payload.

## The Case a Composite Cannot Cover

```sql
CREATE TABLE veterans (
    id      SERIAL PRIMARY KEY,
    name    TEXT NOT NULL,
    benefit public.dynamic_dto      -- a land grant, a pension, a citation…
);
```

```kotlin
sealed interface Benefit

@Serializable data class LandGrant(val province: String, val iugera: Int) : Benefit
@Serializable data class MilitaryPension(val legion: String, val annual: Int) : Benefit

db.dynamicTypes.register<LandGrant>("land_grant")
db.dynamicTypes.register<MilitaryPension>("military_pension")

db.insertInto("veterans").values(listOf("name", "benefit"))
    .update("name" to "Marcus", "benefit" to LandGrant("Gallia Narbonensis", 120))

val benefits: List<Benefit> = db.select("benefit").from("veterans").fetchFields()
```

The column comes back as whatever supertype you ask for, each row decoded as the class its own `type_name`
names. That is what a composite cannot do: its shape is decided once, in the schema, for every row at once.

Where the shape *is* fixed, use a composite. It is cheaper in every direction — no JSON to encode, no
discriminator to keep honest — and the driver maps one onto a data class reflectively. See
[Composites and Reflection](../driver/composites-reflection.md).

## Creating the Type

The type and its constructor functions are **not** created behind your back:

```sql
CREATE TYPE public.dynamic_dto AS (type_name text, data_payload jsonb);
```

`DYNAMIC_DTO_DDL` is that, plus a `dynamic_dto(text, jsonb)` constructor function and two `to_dynamic_dto`
overloads, written so that running it twice is harmless. PostgreSQL creates no constructor function for a
composite on its own, which is why the function is there and why SQL can say `dynamic_dto('land_grant', …)` at
all.

Put it in a migration, which is the reading this library prefers, or call `install()` where a migration would be
ceremony:

```kotlin
db.dynamicTypes.install()
```

`install()` also reloads the driver's type catalogue. That catalogue is loaded **once per database**, on the
first connection opened to it, and every session after that shares it — so a type created later is one the
driver has never heard of, and opening a fresh connection does not help, which is the whole reason a reload
exists at all. A column of such a type comes back as an unknown OID rather than as anything. A type that was
already there before anything connected needs no reload.

## Registering a Class

```kotlin
db.dynamicTypes.register<LandGrant>("land_grant")     // states the name
db.dynamicTypes.register<LandGrant>()                 // reads @DynamicallyMappable
db.dynamicTypes.register(kClass, "land_grant")        // for a scanner that found the class
```

The class has to be `@Serializable`. The name is **stated**, not derived from the class — the discriminator is
stored in the data, so tying it to a Kotlin identifier means a rename silently orphans every row written before
it, at runtime, on whichever query reaches one first. `@DynamicallyMappable` is a declaration rather than a
derivation and moves with the class instead of tracking it; stating the name at the call still works and wins
where both are present.

Registration is **global to the database**, the driver's type registry being keyed that way, so it belongs at
startup and not per request. Two names for one class, or one name for two classes, is refused.

## Reading

| Ask for              | Get                                                     |
|----------------------|---------------------------------------------------------|
| the registered class | that class                                              |
| a supertype          | whichever registered class the row's `type_name` names  |
| `Any`                | the same                                                |
| `DynamicDto`         | the raw form: the discriminator and the payload as text |
| `Map<String, Any?>`  | the composite's own two attributes                      |

Nothing about the read path needs the value to have come from Kotlin. SQL naming the type and building the
payload is the whole contract, which is what makes an ad-hoc projection work without a schema change:

```kotlin
db.rawQuery("SELECT dynamic_dto('land_grant', jsonb_build_object('province', @p, 'iugera', @i))")
    .fetchFieldStrict<Benefit>("p" to "Britannia", "i" to 75)
```

A name nothing is registered under, and a payload that does not fit the class, are both `MappingException`s
naming what they found rather than half-filling an object.

`DynamicDto.dataPayload` is JSON **text**, not a `JsonElement`: `jsonb`'s codec encodes and decodes a `String`,
so text is the form both directions already pass through, and building a tree would be one materialisation each
way that nothing asked for. `Json.parseToJsonElement` is one call away.

## Writing

An instance of a registered class is written as a `dynamic_dto` **without being wrapped**. When that happens is
`DynamicWriteStrategy`, given to the client when it is built:

```kotlin
OctaviusClient.fromDataSource(dataSource, dynamicWriteStrategy = DynamicWriteStrategy.EXPLICIT_ONLY)
```

| Mode                         | A registered class, unwrapped                                                                            |
|------------------------------|----------------------------------------------------------------------------------------------------------|
| `AUTOMATIC_WHEN_UNAMBIGUOUS` | Written as a `dynamic_dto`, unless the class is a registered composite too — then the composite takes it |
| `PREFER_DYNAMIC_DTO`         | Written as a `dynamic_dto` either way                                                                    |
| `EXPLICIT_ONLY`              | Refused by name, pointing at the wrapper                                                                 |

`AUTOMATIC_WHEN_UNAMBIGUOUS` is the default, and under it a class registered one way only — which is nearly all
of them — needs no wrapping anywhere.

**`toDynamicDto(value)` overrides all three**, and is what settles the one case they disagree about:

```kotlin
db.insertInto("veterans").values(listOf("name", "benefit"))
    .update("name" to "Marcus", "benefit" to db.dynamicTypes.toDynamicDto(honour))
```

It is also the only way to make a `DynamicDto`, which is what keeps the discriminator honest: the name is
checked against the registry there and nowhere else. The column is a plain `(text, jsonb)` and the server has
never had the registry mentioned to it, so a value built with a name nothing is registered under would store
cleanly and fail on whichever read reached it first, in some other process, much later.

> A mode is given to a client, but what enforces it is a converter on the driver's type registry — which is
> global to the database. Two clients on one database do not hold a mode each: the one that registered a
> dynamic type last is the one whose mode applies to both. Where an application builds a second client against
> the same database, give it the same mode.

## A Different `Json` for One Query

The client's `Json` is strict by default: a payload carrying a field the class does not declare is an error
rather than something dropped. A payload built in SQL with `jsonb_build_object` is named the way SQL names
things, against classes whose properties are not — so for that one query, hand over a different one:

```kotlin
val snakeCase = Json { namingStrategy = JsonNamingStrategy.SnakeCase }

db.rawQuery("SELECT dynamic_dto('stipend', jsonb_build_object('province_name', @p, 'annual_amount', @a))")
    .registerResultConverter(db.dynamicTypes.resultConverter(snakeCase))
    .fetchFieldStrict<Stipend>("p" to "Aegyptus", "a" to 500)
```

`parameterConverter(json)` is the write-side mirror, and `toDynamicDto(value, json)` takes one directly for a
value wrapped by hand. Query registries sit ahead of the session's and are discarded with the query, so the
rest of the application goes on reading the way it did — see
[Per-Query Converters](queries.md#per-query-converters).

The client-wide default is `dynamicJson` on `fromDataSource` and `fromSessionProvider`.

## Next

- [Annotation Scanning](scanner.md) — registering these by annotation instead of by hand
- [Queries](queries.md) — the per-query converter mechanism this is built on
