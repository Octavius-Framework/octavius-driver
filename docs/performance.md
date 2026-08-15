# Performance

JMH benchmarks comparing Octavius against the official PostgreSQL JDBC driver (`pgjdbc`).

Every figure below carries JMH's ± confidence interval over 5 measurement iterations, and that number is the point of this page: **a difference smaller than the intervals it sits between is not a result.** Several rows here are ties for exactly that reason, and saying so is more useful than reporting a 2% win.

> [!NOTE]
> **Environment.** One developer laptop, JDK 25, JMH 1.37, PostgreSQL 18.4 over a local connection, 3 warmup and 5 measurement iterations in a single fork, declared on the benchmark classes themselves. Both drivers do identical work in the same JVM, and every figure on this page comes from one run of the whole suite. Absolute throughput will not reproduce on your hardware — the ratios between the two columns are what travels.
>
> Every benchmark also fixes one row shape, named in the table below, and both drivers carry that identical shape. That is what makes the two columns comparable, and what stops any single figure from being a per-row constant: wider rows, longer strings and nested structures move all of these numbers, the ratios included.

## What is measured

| Benchmark                  | Mode         | Work per operation                                                                                             |
|:---------------------------|:-------------|:---------------------------------------------------------------------------------------------------------------|
| `SimpleTypeBenchmark`      | Throughput   | Read 10 000 rows of `int4`, `text`, `boolean`, `float8` as raw values.                                         |
| `SimpleDataBenchmark`      | Throughput   | The same 10 000 rows, mapped onto objects.                                                                     |
| `ArrayTypeBenchmark`       | Throughput   | Read 10 000 rows, each with an `int4[]` and a `text[]`.                                                        |
| `InsertBenchmark`          | Average time | Insert 10 000 rows inside one transaction, by several strategies.                                              |
| `CompositeInsertBenchmark` | Average time | Insert 10 000 `(int, text)` rows as an array of composites, reflectively and through a hand-written converter. |

Run them yourself with `./gradlew :benchmarks:jmh`, or narrow to one class with `-Pjmh="InsertBenchmark"`.

## Reading

Operations per millisecond — higher is better.

| Benchmark                  | Octavius      | pgjdbc        | Verdict                     |
|:---------------------------|:--------------|:--------------|:----------------------------|
| **Mapped to objects**      | 0.220 ± 0.004 | 0.216 ± 0.029 | Tie — the intervals overlap |
| **Raw values, no mapping** | 0.171 ± 0.010 | 0.220 ± 0.007 | pgjdbc ~29% ahead           |
| **Arrays**                 | 0.084 ± 0.005 | 0.106 ± 0.006 | pgjdbc ~26% ahead           |

The first row is the one most applications live on, and it is a genuine dead heat: Octavius is nominally 2% ahead, but pgjdbc's own spread is ±13%, so the two are indistinguishable at this sample size.

The other two rows are real — the intervals do not come close to touching. Both are worth understanding rather than just noting, because they are not the same kind of gap.

Arrays are slower by construction. Octavius decodes every element through the same conversion machinery that maps composites, ranges and nested arrays, which is precisely what makes a `List<Tribute>` of composites work at all. A driver that treats `int4[]` as a special case can be quicker at `int4[]`; the price of that is having no answer for the general one. This is a trade rather than a defect, and closing it would mean adding a specialized fast path for primitive element types alongside the general one — not fixing something broken.

## Writing

Milliseconds per operation, each operation being 10 000 rows in one transaction — lower is better.

| Strategy                    | Octavius     | pgjdbc       |
|:----------------------------|:-------------|:-------------|
| **Single inserts**          | 327.0 ± 28.8 | 266.3 ± 8.5  |
| **`UNNEST` bulk insert**    | 7.57 ± 1.70  | 6.05 ± 0.54  |
| **JDBC batching**           | n/a          | 26.63 ± 0.83 |
| **JDBC batching + rewrite** | n/a          | 8.00 ± 0.67  |

**Strategy dominates the driver.** Row-at-a-time insertion costs ~327 ms against ~266 ms, but the same 10 000 rows go in **43× faster** through `UNNEST` in either driver. If you take one thing from this page, take that one.

**Octavius's `UNNEST` matches pgjdbc's fastest batching.** 7.57 ± 1.70 against `reWriteBatchedInserts=true` at 8.00 ± 0.67 — overlapping intervals, so a tie — and **3.5× faster** than plain JDBC batching at 26.63 ms. That last gap is far outside the noise.

**Against pgjdbc doing `UNNEST` too, Octavius is nominally behind**: 7.57 against 6.05. Read that one carefully, though — Octavius's interval here is ±1.70, wide enough to reach into pgjdbc's, so this run does not separate them the way an earlier one did. The direction has been consistent across runs and matches where the read benchmarks point, which is per-value serialization; the size of it is not something this measurement pins down.

Worth knowing about `reWriteBatchedInserts`: it only rewrites `INSERT`, so bulk `UPDATE` and `DELETE` fall back to ordinary batching and its worse figures, while `UNNEST` applies unchanged to all three. For loads beyond this scale, neither column is the answer — use [`COPY`](copy.md).

## Reflection or a hand-written converter

Both directions of the type system can be driven two ways: reflectively, through `registerAutoComposite` and the reflective row mapper, or through a `ResultConverter` / `ParameterConverter` you write yourself. The reflective path is what makes the driver pleasant; this is what it costs.

**Writing** — 10 000 rows through `UNNEST`, milliseconds per operation and bytes allocated:

| Building the parameter                           | Time        | Allocated |
|:-------------------------------------------------|:------------|:----------|
| Two parallel scalar arrays (no composite at all) | 7.88 ± 0.84 | 0.93 MB   |
| One `composite[]`, hand-written converter        | 7.28 ± 0.66 | 2.05 MB   |
| One `composite[]`, `registerAutoComposite`       | 9.89 ± 1.28 | 4.93 MB   |

**Reading** — the same 10 000 rows mapped onto a data class, operations per millisecond:

| Mapping the row        | Throughput    | Allocated |
|:-----------------------|:--------------|:----------|
| Hand-written converter | 0.220 ± 0.004 | 2.57 MB   |
| Reflective row mapper  | 0.196 ± 0.024 | 6.65 MB   |

Three things come out of this.

**Composites themselves are close to free.** Sending one array of composites costs the same time as sending two parallel scalar arrays — 7.28 against 7.88, comfortably overlapping — for 2.2× the allocation. If a composite type is the shape your data already has, use it.

**Reflection costs about a third on the write path.** 9.89 against 7.28 is a 36% difference with the intervals well clear of each other. On the read path it is smaller and shakier: 0.196 against 0.220 is 12%, and the intervals touch at their edges, so treat it as suggestive rather than measured.

**The allocation difference is the sturdy one — against the clock, not against your schema.** Reflection allocated 2.4× the hand-written converter when writing and 2.6× when reading, and unlike the timings those ratios came back to within a fraction of a percent on every run and on either power profile. What they do *not* survive is a change of shape. Everything measured here is a two-field row — an `int` and a short `text` — and a class with fifteen fields, nested composites or `numeric` columns will land somewhere else entirely, in both columns of the comparison. Take the direction from this and measure the size of it on your own data.

Where the time goes is visible in JMH's `stack` profiler, and it explains why the timings are the softer measurement here. On the composite insert, ~27% of RUNNABLE samples sit in `sun.nio.ch.Net.poll` — waiting for the server — while the reflective path adds ~2.2% in `ParameterConverterRegistry.convert` and ~0.4% in `invokeExact`. The work reflection adds is real, but it is competing with a socket, which is what buries it in the noise on a local connection and would bury it further on a real network.

So: reach for a hand-written converter on the paths that move the most rows, and let reflection handle everything else. Rewriting a mapping that runs once per request buys nothing worth the code.

## Memory

Bytes allocated per operation, from JMH's `gc` profiler.

| Benchmark                       | Octavius | pgjdbc  |
|:--------------------------------|:---------|:--------|
| Mapped to objects               | 2.57 MB  | 2.33 MB |
| Mapped to objects, reflectively | 6.65 MB  | n/a     |
| Raw values, no mapping          | 2.25 MB  | 2.33 MB |
| Arrays                          | 11.62 MB | 7.77 MB |
| Single inserts                  | 7.21 MB  | 2.61 MB |
| `UNNEST` bulk insert            | 0.93 MB  | 0.80 MB |
| JDBC batching                   | n/a      | 2.94 MB |
| JDBC batching + rewrite         | n/a      | 2.44 MB |

One row here is worth pausing on. Reading raw values is ~29% slower in Octavius while allocating **less** than pgjdbc (2.25 MB against 2.33 MB), so whatever costs the time on that path, it is not garbage — and looking for it among allocations would be looking in the wrong place.

Arrays allocate 50% more, which is the per-element conversion described above doing its work; the generality has a memory cost as well as a time one. Single-row inserts allocate nearly 3× more, matching their throughput gap.

## Summary

* **Object mapping ties with `pgjdbc`** — the path most applications spend their time on.
* **`UNNEST` bulk writes are 3.5× faster than classic JDBC batching** and tie with pgjdbc's rewrite optimization, while remaining usable for `UPDATE` and `DELETE`, where that optimization does not apply.
* **Array decoding costs ~26% for being general** — every element goes through the machinery that also maps composites and nested structures. Expect it to stay that way; it is what buys you `List<YourDataClass>`.
* **Raw value decoding is slower without allocating more** — a genuinely different problem from the array one, and not one to look for among allocations.
* **Reflection cost ~2.5× the allocation of a hand-written converter** on a two-field row, in both directions, and around a third of the time on the write path. The direction generalizes; the multiplier is an example from one shape, not a constant. Worth replacing on your hottest path, not everywhere.
