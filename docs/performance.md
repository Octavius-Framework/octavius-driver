# Performance

*The aediles kept the official weights in the temple and checked the market's scales against them. A pan that sat a
grain low was not yet fraud — the scales themselves were not that fine, and everyone in the market knew it. Every figure
below is reported with its tolerance for the same reason, and where two drivers sit closer together than that tolerance,
this page says so instead of declaring a winner.*

JMH benchmarks comparing Octavius against the official PostgreSQL JDBC driver (`pgjdbc`).

Every figure below carries JMH's ± confidence interval over 15 measurement iterations, and that number is the point of
this page: **a difference smaller than the intervals it sits between is not a result.** Several rows here are ties for
exactly that reason, and saying so is more useful than reporting a 2% win.

> [!NOTE]
> **Environment.** One developer laptop, JDK 25, JMH 1.37, PostgreSQL 18.4 over a local connection, 3 warmup and 5
> measurement iterations in each of three forks — 15 samples behind every figure — declared on the benchmark classes
> themselves. Both drivers do identical work in the same JVM, and the figures in any one table below come from a single
> run, which is what makes the columns within that table comparable.
> Absolute throughput will not reproduce on your hardware — the ratios between the two columns are what travels.
>
> Every benchmark also fixes one row shape, named in the table below, and both drivers carry that identical shape. That
> is what makes the two columns comparable, and what stops any single figure from being a per-row constant: wider rows,
> longer strings and nested structures move all of these numbers, the ratios included.

## What is measured

| Benchmark                  | Mode         | Work per operation                                                                                             |
|:---------------------------|:-------------|:---------------------------------------------------------------------------------------------------------------|
| `SimpleTypeBenchmark`      | Throughput   | Read 10 000 rows of `int4`, `text`, `boolean`, `float8` as raw values.                                         |
| `SimpleDataBenchmark`      | Throughput   | The same 10 000 rows, mapped onto objects.                                                                     |
| `ArrayTypeBenchmark`       | Throughput   | Read 10 000 rows, each with an `int4[]` and a `text[]`.                                                        |
| `InsertBenchmark`          | Average time | Insert 10 000 rows inside one transaction, by several strategies.                                              |
| `CompositeInsertBenchmark` | Average time | Insert 10 000 `(int, text)` rows as an array of composites, reflectively and through a hand-written converter. |
| `PointLookupBenchmark`     | Average time | One row by primary key, three ways: Octavius, and pgjdbc with server-side prepare on and off.                  |

Run them yourself with `./gradlew :benchmarks:jmh`, or narrow to one class with `-Pjmh="InsertBenchmark"`.

## Reading

Operations per millisecond — higher is better.

| Benchmark                  | Octavius      | pgjdbc        | Verdict                     |
|:---------------------------|:--------------|:--------------|:----------------------------|
| **Mapped to objects**      | 0.227 ± 0.008 | 0.223 ± 0.009 | Tie — the intervals overlap |
| **Raw values, no mapping** | 0.185 ± 0.011 | 0.222 ± 0.010 | pgjdbc ~20% ahead           |
| **Arrays**                 | 0.086 ± 0.004 | 0.110 ± 0.008 | pgjdbc ~28% ahead           |

The first row is the one most applications live on, and it is a genuine dead heat: Octavius is nominally 2% ahead, and each driver's own interval is twice that wide.

The other two rows are real — the intervals do not come close to touching. Both are worth understanding rather than just noting, because they are not the same kind of gap.

Arrays are slower by construction. Octavius decodes every element through the same conversion machinery that maps composites, ranges and nested arrays, which is precisely what makes a `List<Tribute>` of composites work at all. A driver that treats `int4[]` as a special case can be quicker at `int4[]`; the price of that is having no answer for the general one. This is a trade rather than a defect, and closing it would mean adding a specialized fast path for primitive element types alongside the general one — not fixing something broken.

## What not preparing costs

Octavius never promotes a statement to a named server-side one: every execution is a `Parse` into the unnamed statement, which is [a trade rather than an omission](octavius-vs-jdbc.md#nothing-is-prepared-server-side). `PointLookupBenchmark` puts a number on it, on the workload where that number is largest.

Microseconds per single-row primary-key lookup — lower is better.

| Path                                                     | Time       |
|:---------------------------------------------------------|:-----------|
| pgjdbc, server-prepared (`prepareThreshold=5`)           | 25.4 ± 0.9 |
| pgjdbc, re-parsed every execution (`prepareThreshold=0`) | 41.7 ± 0.7 |
| **Octavius**                                             | 40.5 ± 1.5 |

The two pgjdbc rows are the controlled half of it: one driver, one table, one method, and a single connection property between them. The distance between those two — **16.3 µs, a factor of 1.64** — is what a server-side prepared statement is worth with everything else held still. Octavius lands on the unprepared row with its interval overlapping, which is the other half of the answer: nothing else in the driver eats that difference or adds to it, so the whole gap is the feature.

**It is a ceiling rather than a typical case.** A primary-key lookup against a warm 10 000-row table is close to the cheapest statement PostgreSQL can be asked to run, so parsing and planning take the largest share of it they are ever going to take. What the feature saves is a fixed cost per statement, not a proportion of one: against a query doing 2 ms of real work it is under 1%, and against a database one network hop away — half a millisecond gone before the server has read anything — about 3%. What moves it back up is planning that is expensive in itself, many joins or a heavily partitioned table, where the planner's work grows and the executor's need not.

Reproduce with `./gradlew :benchmarks:jmh -Pjmh="PointLookupBenchmark"`. The three figures above come from one run of that class, which is what makes them comparable to each other.

## Writing

Milliseconds per operation, each operation being 10 000 rows in one transaction — lower is better.

| Strategy                    | Octavius     | pgjdbc       |
|:----------------------------|:-------------|:-------------|
| **Single inserts**          | 281.6 ± 4.1  | 221.2 ± 2.6  |
| **`UNNEST` bulk insert**    | 7.56 ± 0.41  | 5.96 ± 0.50  |
| **JDBC batching**           | n/a          | 25.55 ± 0.32 |
| **JDBC batching + rewrite** | n/a          | 7.35 ± 0.48  |

**Strategy dominates the driver.** Row-at-a-time insertion costs ~282 ms against ~221 ms, but the same 10 000 rows go in **37× faster** through `UNNEST` in either driver. If you take one thing from this page, take that one.

**Octavius's `UNNEST` matches pgjdbc's fastest batching.** 7.56 ± 0.41 against `reWriteBatchedInserts=true` at 7.35 ± 0.48 — overlapping intervals, so a tie — and **3.4× faster** than plain JDBC batching at 25.55 ms. That last gap is far outside the noise.

**Against pgjdbc doing `UNNEST` too, Octavius is behind by ~27%**: 7.56 ± 0.41 against 5.96 ± 0.50, intervals clear of each other. It points where the read benchmarks point — per-value serialization, the same machinery that costs the array row above.

Worth knowing about `reWriteBatchedInserts`: it only rewrites `INSERT`, so bulk `UPDATE` and `DELETE` fall back to ordinary batching and its worse figures, while `UNNEST` applies unchanged to all three. For loads beyond this scale, neither column is the answer — use [`COPY`](copy.md).

## Reflection or a hand-written converter

Both directions of the type system can be driven two ways: reflectively, through `registerAutoComposite` and the reflective row mapper, or through a `ResultConverter` / `ParameterConverter` you write yourself. The reflective path is what makes the driver pleasant; this is what it costs.

**Writing** — 10 000 rows through `UNNEST`, milliseconds per operation and bytes allocated:

| Building the parameter                           | Time        | Allocated |
|:-------------------------------------------------|:------------|:----------|
| Two parallel scalar arrays (no composite at all) | 7.10 ± 0.44 | 0.93 MB   |
| One `composite[]`, hand-written converter        | 7.01 ± 0.39 | 2.05 MB   |
| One `composite[]`, `registerAutoComposite`       | 8.65 ± 0.33 | 4.93 MB   |

**Reading** — the same 10 000 rows mapped onto a data class, operations per millisecond:

| Mapping the row        | Throughput    | Allocated |
|:-----------------------|:--------------|:----------|
| Hand-written converter | 0.227 ± 0.008 | 2.57 MB   |
| Reflective row mapper  | 0.209 ± 0.027 | 6.65 MB   |

Three things come out of this.

**Composites themselves are close to free.** Sending one array of composites costs the same time as sending two parallel
scalar arrays — 7.01 against 7.10, comfortably overlapping — for 2.2× the allocation. If a composite type is the shape
your data already has, use it.

**Reflection costs about a quarter on the write path.** 8.65 against 7.01 is a 23% difference with the intervals well
clear of each other. On the read path this run does not measure it at all: 0.209 against 0.227 is 8% nominally, and the
reflective interval of ±0.027 swallows that whole. Reading reflectively costs something — it allocates 2.6× as much, and
that part is not in doubt — but the clock cannot say how much.

**The allocation difference is the sturdy one — against the clock, not against your schema.** Reflection allocated 2.4×
the hand-written converter when writing and 2.6× when reading, and unlike the timings those ratios came back to within a
fraction of a percent on every run and on either power profile. What they do *not* survive is a change of shape.
Everything measured here is a two-field row — an `int` and a short `text` — and a class with fifteen fields, nested
composites or `numeric` columns will land somewhere else entirely, in both columns of the comparison. Take the direction
from this and measure the size of it on your own data.

Where the time goes is visible in JMH's `stack` profiler, and it explains why the timings are the softer measurement
here. On the composite insert, ~40% of RUNNABLE samples sit in `sun.nio.ch.Net.poll` — waiting for the server — while
the reflective path adds ~3% in `ParameterConverterRegistry.convert` and ~0.7% in `invokeExact`. The work reflection
adds is real, but it is competing with a socket, which is what buries it in the noise on a local connection and would
bury it further on a real network.

So: reach for a hand-written converter on the paths that move the most rows, and let reflection handle everything else.
Rewriting a mapping that runs once per request buys nothing worth the code.

## Memory

Bytes allocated per operation, from JMH's `gc` profiler.

| Benchmark                       | Octavius | pgjdbc  |
|:--------------------------------|:---------|:--------|
| Mapped to objects               | 2.57 MB  | 2.33 MB |
| Mapped to objects, reflectively | 6.65 MB  | n/a     |
| Raw values, no mapping          | 2.25 MB  | 2.33 MB |
| Arrays                          | 11.62 MB | 8.52 MB |
| Single inserts                  | 4.69 MB  | 2.58 MB |
| `UNNEST` bulk insert            | 0.93 MB  | 0.80 MB |
| JDBC batching                   | n/a      | 2.94 MB |
| JDBC batching + rewrite         | n/a      | 2.44 MB |

One row here is worth pausing on. Reading raw values is ~20% slower in Octavius while allocating **less** than pgjdbc (
2.25 MB against 2.33 MB), so whatever costs the time on that path, it is not garbage — and looking for it among
allocations would be looking in the wrong place.

Arrays allocate materially more, which is the per-element conversion described above doing its work; the generality has
a memory cost as well as a time one. The ratio is ~1.4×, and pgjdbc's side of it is the one allocation figure on this
page that will not sit still: ±0.58 MB across the three forks, against ±0.0004 MB on Octavius's 11.62 MB.

Single-row inserts allocate 1.8× more — 4.69 MB against 2.58 MB — a wider gap than the 27% they lose on the clock.

## Summary

* **Object mapping ties with `pgjdbc`** — the path most applications spend their time on.
* **Not preparing statements costs ~16 µs a statement** — measured with pgjdbc against itself, server-side prepare
  switched on and off, on a primary-key lookup chosen so that the figure would be the largest share of a query it can
  be. Under 1% of a statement that does 2 ms of work.
* **`UNNEST` bulk writes are 3.4× faster than classic JDBC batching** and tie with pgjdbc's rewrite optimization, while
  remaining usable for `UPDATE` and `DELETE`, where that optimization does not apply.
* **Array decoding costs ~28% for being general** — every element goes through the machinery that also maps composites
  and nested structures. Expect it to stay that way; it is what buys you `List<YourDataClass>`.
* **Raw value decoding is ~20% slower without allocating more** — a genuinely different problem from the array one, and
  not one to look for among allocations.
* **Reflection cost ~2.5× the allocation of a hand-written converter** on a two-field row, in both directions, and about
  a quarter of the time on the write path. The direction generalizes; the multiplier is an example from one shape, not a
  constant. Worth replacing on your hottest path, not everywhere.
