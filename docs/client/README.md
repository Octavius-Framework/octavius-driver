# Octavius Client Documentation

*A magistrate's authority was his own, and none of it belonged to the scriba at his elbow. What the clerk did
was the part that repeated: the standing phrases, the order they had to go in, the clauses struck out when they
did not apply. Nobody mistook him for the magistrate, and no case came out differently because he was there.*

The client adds no power over the database. Every clause you give a builder is SQL and is passed through
unread; the terminal methods are the driver's own, under the driver's names, meaning what they mean there. What
it does is the part that repeats — and the one thing the driver genuinely leaves open, which is *which session*
an operation runs on.

These pages assume the driver's. Where something is the driver's behaviour they point at it rather than saying
it again.

## Guides

| Document                                              | Description                                                                                   |
|-------------------------------------------------------|-----------------------------------------------------------------------------------------------|
| [Quickstart](quickstart.md)                           | From a pool to a row, and the one line that stops appearing in your signatures                |
| [Queries](queries.md)                                 | The builders, clauses that disappear, `QueryFragment`, `toSql`, raw SQL, per-query converters |
| [Transactions and Failures](transactions-failures.md) | Propagation, isolation, timeouts, `SessionProvider`, and when a failure is a value            |
| [Transaction Plans](plans.md)                         | A sequence as data: handles, `TransactionValue`, merging, and what is checked before it runs  |
| [`dynamic_dto`](dynamic-dto.md)                       | One column, several unrelated shapes, and the three ways a value gets written as one          |
| [Annotation Scanning](scanner.md)                     | `client-scanner`: finding annotated types and registering them, and what it reports           |

## Quick Links

### Getting Started
- [Add the Dependency](quickstart.md#1-add-the-dependency) — Gradle coordinates, and what comes transitively
- [Build the Client](quickstart.md#2-build-the-client) — Over a pool, or over a provider of your own
- [Run Something](quickstart.md#3-run-something) — A query, a transaction, and where the session went

### Queries
- [Every Clause Is SQL](queries.md#every-clause-is-sql) — What the builder does and what it refuses to parse
- [Clauses That Disappear](queries.md#clauses-that-disappear) — `where(null)`, and the filter assembled at runtime
- [`QueryFragment`](queries.md#queryfragment) — A condition and the parameters it names, kept together
- [A Query Is a Value](queries.md#a-query-is-a-value) — `toSql()`, `copy()`, and embedding one in another
- [Raw SQL](queries.md#raw-sql) — `rawQuery`, and the one terminal only it has
- [Per-Query Converters](queries.md#per-query-converters) — A mapping for one call and nothing else

### Transactions and Failures
- [Which Session Am I On](transactions-failures.md#which-session-am-i-on) — The question the client exists to answer
- [Propagation](transactions-failures.md#propagation) — `REQUIRED`, `REQUIRES_NEW`, `NESTED`
- [Isolation, Read-Only and Timeouts](transactions-failures.md#isolation-read-only-and-timeouts) — What applies where, and why
- [Thrown or Returned](transactions-failures.md#thrown-or-returned) — The split, and that it reads only the exception's type
- [Three Doors, Three Widths](transactions-failures.md#three-doors-three-widths) — `asResult`, `dbResult`, `transactionResult`
- [The Combination That Misleads](transactions-failures.md#the-combination-that-misleads) — `dbResult` inside a plain `transaction`
- [`SessionProvider`](transactions-failures.md#sessionprovider) — The seam, and Spring in under thirty lines

### Transaction Plans
- [When a Block Is Not Enough](plans.md#when-a-block-is-not-enough) — The sequence as data
- [Handles and What They Reach](plans.md#handles-and-what-they-reach) — `value`, `field`, `column`, `row`
- [What Fits What](plans.md#what-fits-what) — The matrix, and why the two axes do not multiply
- [`map` and the Spread](plans.md#map-and-the-spread) — Transforming a value, and the one thing that takes away
- [Merging Plans](plans.md#merging-plans) — `addPlan`, and what it refuses
- [Checked Before It Runs](plans.md#checked-before-it-runs) — And what is deliberately not checked
- [Running One Twice](plans.md#running-one-twice) — Retrying a serialization failure as a plain loop

### `dynamic_dto`
- [The Case a Composite Cannot Cover](dynamic-dto.md#the-case-a-composite-cannot-cover) — Shape per row, not per schema
- [Creating the Type](dynamic-dto.md#creating-the-type) — `DYNAMIC_DTO_DDL`, a migration, or `install()`
- [Registering a Class](dynamic-dto.md#registering-a-class) — And why the name is stated rather than derived
- [Reading](dynamic-dto.md#reading) — As the class, as a supertype, as the raw form, as a map
- [Writing](dynamic-dto.md#writing) — `DynamicWriteStrategy`, and when wrapping is still required
- [A Different `Json` for One Query](dynamic-dto.md#a-different-json-for-one-query) — Payloads named the way SQL names things

### Annotation Scanning
- [Why It Is a Module of Its Own](scanner.md#why-it-is-a-module-of-its-own) — One dependency, kept off everyone else
- [The Annotations](scanner.md#the-annotations) — What each one registers, and where they live
- [What a Scan Reports](scanner.md#what-a-scan-reports) — `ScanReport`, and why `unresolved` is not a refusal

## API Reference

- [API Reference](https://octavius-framework.github.io/octavius-driver/) — `client`, `client-scanner`
