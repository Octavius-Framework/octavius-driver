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

## What JSON Does Not Carry

The payload is JSON, and a few kinds of value mean less there than they do in a column of their own. The
driver maps every one of them correctly in a `numeric`, a `date`, a `timestamptz` or an enum column; put the
same value in a `jsonb` payload and the default serializer writes something else.

| Type              | What the default serializer writes    | What that costs                                                     |
|:------------------|:--------------------------------------|:--------------------------------------------------------------------|
| `BigDecimal`      | nothing — it has no serializer        | the class does not encode at all                                    |
| `LocalDate`       | `+999999999-12-31` for `infinity`     | not `infinity` any more, and `(payload->>'until')::date` refuses it |
| `LocalDateTime`   | `+999999999-12-31T23:59:59.999999999` | the same                                                            |
| `Instant`         | `+100000-01-01T00:00:00Z`             | the same                                                            |
| any date at all   | ISO-8601                              | outside years `0001`..`9999`, PostgreSQL will not read it back      |
| a registered enum | the Kotlin constant's own name        | `Praetor` in the payload where the enum column holds `PRAETOR`      |

The last row is the one that is not about markers. ISO-8601 and PostgreSQL spell a year differently as soon
as it leaves `0001`..`9999`, and no single string satisfies both:

| Year  | ISO-8601 / kotlinx | PostgreSQL      |
|:------|:-------------------|:----------------|
| 2024  | `2024-01-02`       | the same        |
| 10000 | `+10000-01-02`     | `10000-01-02`   |
| 1 BC  | `0000-01-02`       | `0001-01-02 BC` |
| 2 BC  | `-0001-01-02`      | `0002-01-02 BC` |

ISO requires a sign past four digits and PostgreSQL reads that sign as the start of a timezone offset, so it
refuses `+10000-01-02` — and `-0001-01-02`, and even `+5874897-12-31`, which is a year a `date` holds
perfectly well. ISO also counts through a year zero where PostgreSQL counts BC from one, which is where the
off-by-one comes from. The serializers write PostgreSQL's spelling and read **either** back, so a payload
built in SQL still decodes.

None of that widens what a column holds: `date` reaches 4713 BC to 5874897 AD and `timestamp`/`timestamptz`
294276 AD, and a value past that is out of range however it is spelled — which is what the markers are, at
year 999999999.

Every one of them is answered already, and the whole of what a class has to say is one annotation on the
property:

```kotlin
@PgEnumType                               // labels are SNAKE_CASE_UPPER in the database
enum class Magistrature { Quaestor, Aedile, Praetor, Consul }

@Serializable
@DynamicallyMappable("land_grant")
data class LandGrant(
    val province: String,
    @Contextual val iugera: BigDecimal,      // a bare JSON number, every digit kept
    @Contextual val until: LocalDate,        // "infinity"
    @Contextual val awardedBy: Magistrature  // "PRAETOR", as the enum column holds it
)
```

`@Contextual` is what selects a contextual serializer; without it the property keeps the default and nothing
above applies. That is deliberate — nothing changes that you have not asked to change.

The first four come from `octaviusSerializersModule`, which the client's `dynamicJson` carries. The enum comes
from somewhere else, because its labels are not a fixed rule: they are whatever that enum was **registered**
under. The client reads them straight off the driver's registry, so the enum named at
`typeManager.registerEnum` and the one a scan found by `@PgEnumType` are covered alike — and the enum itself
needs no `@Serializable` and no serializer of its own. Registration is what turns it on; an enum the driver
has never been told about keeps the default.

> Registration happens **after** the client is built — that is the only order there is — so the enum
> serializers are resolved per conversion rather than composed once. An enum registered between two queries
> applies to the second.

`BigDecimal` here is `io.github.octaviusframework.type.BigDecimal` from `pg-model`, which on the JVM is a
`typealias` for `java.math.BigDecimal` — the same class the driver decodes a `numeric` into, so backend code
can go on writing either name. It exists so that a class in `commonMain` can declare the property at all: on
JS it holds the decimal's text, because a `Number` there is a 64-bit float and would round what `numeric` was
chosen to keep.

A `Json` you build yourself — for an HTTP layer, or for the frontend reading the same classes — needs both
modules put on it, since they are the client's default and not a global:

```kotlin
val json = Json {
    serializersModule = octaviusSerializersModule + db.dynamicTypes.enumSerializers + myAppModule
    ignoreUnknownKeys = true
}
```

`octaviusJson` is the first of those on a stock `Json` and nothing else, for the case where nothing else is
being configured. `enumSerializers` answers for the enums registered at the moment it is read, so build that
`Json` after startup registration rather than during it.

The frontend has no driver and therefore no registry. There, write the enum's labels out once — the same two
conventions the registration states — and share the class:

```kotlin
// commonMain, next to the enum itself
@Serializable(with = MagistratureSerializer::class)
@PgEnumType
enum class Magistrature { Quaestor, Aedile, Praetor, Consul }

object MagistratureSerializer : EnumWithCaseConventionSerializer<Magistrature>(
    enumName = "Magistrature",
    entries = Magistrature.entries
)
```

Its defaults are `registerEnum`'s, so an enum that took them there takes them here. `@Serializable(with = …)`
binds tighter than a contextual module, so the backend uses this one too and both ends agree by construction —
which is the point of writing it rather than letting each side derive its own.

## A Different `Json` for One Query

The client's `Json` is `octaviusJson`: strict, so a payload carrying a field the class does not declare is an
error rather than something dropped. A payload built in SQL with `jsonb_build_object` is named the way SQL names
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

A `Json` built here replaces the client's rather than adding to it, so put `octaviusSerializersModule` on it
too where the class has a `@Contextual` property — otherwise that one query reads the payload differently from
every other.

The client-wide default is `dynamicJson` on `fromDataSource` and `fromSessionProvider`.

## Next

- [Annotation Scanning](scanner.md) — registering these by annotation instead of by hand
- [Queries](queries.md) — the per-query converter mechanism this is built on
