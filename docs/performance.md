# Performance

JMH benchmarks comparing Octavius against the official PostgreSQL JDBC driver (`pgjdbc`).

Every figure below carries JMH's ± confidence interval over 5 measurement iterations, and that number is the point of this page: **a difference smaller than the intervals it sits between is not a result.** Two of the rows here are ties for exactly that reason, and saying so is more useful than reporting a 3% win.

> [!NOTE]
> **Environment.** One developer machine, JDK 25, JMH 1.37, PostgreSQL 18.4 over a local connection, 3 warmup and 5 measurement iterations in a single fork. Both drivers do identical work in the same JVM. Absolute throughput will not reproduce on your hardware — the ratios between the two columns are what travels.

## What is measured

| Benchmark             | Mode         | Work per operation                                                     |
|:----------------------|:-------------|:-----------------------------------------------------------------------|
| `SimpleTypeBenchmark` | Throughput   | Read 10 000 rows of `int4`, `text`, `boolean`, `float8` as raw values. |
| `SimpleDataBenchmark` | Throughput   | The same 10 000 rows, mapped onto objects.                             |
| `ArrayTypeBenchmark`  | Throughput   | Read 10 000 rows, each with an `int4[]` and a `text[]`.                |
| `InsertBenchmark`     | Average time | Insert 10 000 rows inside one transaction, by several strategies.      |

Run them yourself with `./gradlew :benchmarks:jmh`, or narrow to one class with `-Pjmh="InsertBenchmark"`.

## Reading

Operations per millisecond — higher is better.

| Benchmark                  | Octavius      | pgjdbc        | Verdict                     |
|:---------------------------|:--------------|:--------------|:----------------------------|
| **Mapped to objects**      | 0.206 ± 0.027 | 0.199 ± 0.008 | Tie — the intervals overlap |
| **Raw values, no mapping** | 0.179 ± 0.008 | 0.212 ± 0.022 | pgjdbc ~18% ahead           |
| **Arrays**                 | 0.083 ± 0.002 | 0.101 ± 0.007 | pgjdbc ~22% ahead           |

The first row is the one most applications live on, and it is a genuine dead heat: Octavius is nominally 3% ahead, but its own spread is ±13%, so the honest answer is that the two are indistinguishable at this sample size.

The other two rows are real — the intervals do not come close to touching. Both are worth understanding rather than just noting, because they are not the same kind of gap.

Arrays are slower by construction. Octavius decodes every element through the same conversion machinery that maps composites, ranges and nested arrays, which is precisely what makes a `List<Tribute>` of composites work at all. A driver that treats `int4[]` as a special case can be quicker at `int4[]`; the price of that is having no answer for the general one. This is a trade rather than a defect, and closing it would mean adding a specialized fast path for primitive element types alongside the general one — not fixing something broken.

## Writing

Milliseconds per operation, each operation being 10 000 rows in one transaction — lower is better.

| Strategy                    | Octavius    | pgjdbc      |
|:----------------------------|:------------|:------------|
| **Single inserts**          | 315.5 ± 2.6 | 239.5 ± 3.7 |
| **`UNNEST` bulk insert**    | 7.99 ± 0.62 | 5.95 ± 0.55 |
| **JDBC batching**           | n/a         | 25.9 ± 0.2  |
| **JDBC batching + rewrite** | n/a         | 7.72 ± 1.10 |

**Strategy dominates the driver.** Row-at-a-time insertion costs ~315 ms against ~240 ms, but the same 10 000 rows go in **39× faster** through `UNNEST` in either driver. If you take one thing from this page, take that one.

**Octavius's `UNNEST` matches pgjdbc's fastest batching.** 7.99 ± 0.62 against `reWriteBatchedInserts=true` at 7.72 ± 1.10 — overlapping intervals, so a tie — and **3.2× faster** than plain JDBC batching at 25.9 ms. That last gap is far outside the noise.

**Against pgjdbc doing `UNNEST` too, Octavius is behind**: 7.99 vs 5.95, intervals well apart. That is the fair like-for-like comparison, and it puts the gap in per-value serialization rather than in the strategy — the same place the read benchmarks point.

Worth knowing about `reWriteBatchedInserts`: it only rewrites `INSERT`, so bulk `UPDATE` and `DELETE` fall back to ordinary batching and its worse figures, while `UNNEST` applies unchanged to all three. For loads beyond this scale, neither column is the answer — use [`COPY`](copy.md).

## Memory

Bytes allocated per operation, from JMH's `gc` profiler.

| Benchmark               | Octavius | pgjdbc  |
|:------------------------|:---------|:--------|
| Mapped to objects       | 2.57 MB  | 2.33 MB |
| Raw values, no mapping  | 2.25 MB  | 2.33 MB |
| Arrays                  | 11.62 MB | 8.89 MB |
| Single inserts          | 7.20 MB  | 2.59 MB |
| `UNNEST` bulk insert    | 0.93 MB  | 0.80 MB |
| JDBC batching           | n/a      | 2.94 MB |
| JDBC batching + rewrite | n/a      | 2.44 MB |

One row here is worth pausing on. Reading raw values is ~18% slower in Octavius while allocating **less** than pgjdbc (2.25 MB against 2.33 MB), so whatever costs the time on that path, it is not garbage — and looking for it among allocations would be looking in the wrong place.

Arrays allocate 31% more, which is the per-element conversion described above doing its work; the generality has a memory cost as well as a time one. Single-row inserts allocate nearly 3× more, matching their throughput gap.

## Summary

* **Object mapping ties with `pgjdbc`** — the path most applications spend their time on.
* **`UNNEST` bulk writes are 3.2× faster than classic JDBC batching** and tie with pgjdbc's rewrite optimization, while remaining usable for `UPDATE` and `DELETE`, where that optimization does not apply.
* **Array decoding costs ~22% for being general** — every element goes through the machinery that also maps composites and nested structures. Expect it to stay that way; it is what buys you `List<YourDataClass>`.
* **Raw value decoding is slower without allocating more** — a genuinely different problem from the array one, and not one to look for among allocations.
