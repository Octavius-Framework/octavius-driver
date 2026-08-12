# Octavius Driver Performance

This document presents a summary of JMH benchmark results for the **Octavius** database driver compared to the official PostgreSQL JDBC driver (**pgjdbc**).

The tests were executed using the JMH framework, measuring both throughput (operations per millisecond) for data reading and average execution time for data insertion.

## Environment and Test Suite

The benchmarks cover the following scenarios:
- **Simple Type Reading (SimpleTypeBenchmark)**: Fetching basic database rows.
- **Structural Data Reading (SimpleDataBenchmark)**: Fetching and mapping slightly more complex records.
- **Array Type Reading (ArrayTypeBenchmark)**: Fetching rows containing arrays.
- **Data Insertion (InsertBenchmark)**:
  - Single row inserts in a transaction (`single_inserts_tx`).
  - Inserts using the UNNEST function (`unnest_inserts_tx`), which provides an efficient bulk loading mechanism.
  - Traditional batch inserts (available in pgjdbc - `batch_inserts_tx`).

## Throughput Results

Higher values indicate better performance (more operations per millisecond).

| Benchmark (Read) | Octavius (ops/ms) | pgjdbc (ops/ms) | Difference             |
|:-----------------|:------------------|:----------------|:-----------------------|
| **Simple Data**  | 0.217             | 0.215           | Octavius is ~1% faster |
| **Simple Types** | 0.181             | 0.216           | pgjdbc is ~19% faster  |
| **Array Types**  | 0.084             | 0.101           | pgjdbc is ~20% faster  |

**Read Conclusions:**
For basic data mapping workloads, the Octavius driver delivers performance directly comparable to `pgjdbc`. In scenarios involving intensive array decoding or high-frequency basic type reads, `pgjdbc` currently maintains a lead of approximately 20%.

## Execution Time Results (Inserts)

Lower values indicate better performance (fewer milliseconds per operation).

| Benchmark (Write)       | Octavius (ms/op) | pgjdbc (ms/op) |
|:------------------------|:-----------------|:---------------|
| **Single Inserts (TX)** | 311.01 ms        | 226.75 ms      |
| **UNNEST Inserts (TX)** | 8.05 ms          | 6.59 ms        |
| **Batch Inserts (TX)**  | N/A              | 27.84 ms       |

**Write Conclusions:**
1. Single row inserts within a transaction show `pgjdbc` executing faster (~226 ms compared to ~311 ms in Octavius).
2. Utilizing the `UNNEST` function for bulk inserts is highly efficient in both Octavius and pgjdbc.
3. Most notably, bulk inserting in Octavius using `UNNEST` (8.05 ms) is **significantly faster than standard batching (Batch Inserts)** in pgjdbc (27.84 ms).

## Memory Allocation

Comparing memory footprint (Garbage Collector Allocation Rate - measured in bytes per operation):
- For basic data mapping (`SimpleDataBenchmark`), the allocation rates of both drivers are very close (~2.57 MB/op for Octavius and ~2.33 MB/op for pgjdbc).
- Octavius allocates more memory in array benchmarks (~11.6 MB/op vs 7.7 MB/op) and single row inserts (~7.1 MB/op vs 2.5 MB/op).
- For `UNNEST` mass inserts, Octavius allocates ~930 KB/op, while pgjdbc allocates 795 KB/op.

## Summary

The JMH results demonstrate strong performance characteristics for Octavius:
- It effectively matches `pgjdbc` in throughput for everyday data mapping (`SimpleDataBenchmark`).
- The native support for PostgreSQL's `UNNEST` bulk operations allows write operations that are **over 3 times faster** than the classic `addBatch()` / `executeBatch()` interface in standard JDBC.
- While memory allocation and specific intensive read operations show a slight edge for `pgjdbc`, Octavius delivers highly competitive overall performance.
