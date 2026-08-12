# COPY Protocol

The PostgreSQL `COPY` command is the fastest way to move large amounts of data in and out of the database. `octavius-driver` provides native support for `COPY IN` and `COPY OUT` through the `CopyManager`.

You can access the manager via `session.copy`.

## COPY IN (Import)

To stream data into a table (e.g., from a CSV or raw stream), use the `copyIn` method. It accepts standard `InputStream` or allows you to write chunks manually.

```kotlin
// Example: Streaming a file directly to the database
val fileStream = FileInputStream(File("senators.csv"))
val rowsImported = session.copy.copyIn("COPY senators(id, cognomen) FROM STDIN WITH (FORMAT csv)", fileStream)
println("Successfully imported $rowsImported senators.")
```

You can also use the lower-level API to stream data chunk by chunk dynamically:
```kotlin
val copyIn = session.copy.copyIn("COPY log_table FROM STDIN")
try {
    val chunk = "1\tInfo\n".toByteArray()
    copyIn.writeToCopy(chunk, 0, chunk.size)
    
    val rows = copyIn.endCopy()
} catch (e: Exception) {
    copyIn.cancelCopy()
    throw e
}
```

## COPY OUT (Export)

To export data from the database, use the `copyOut` method. It can write directly to any `OutputStream`.

```kotlin
// Example: Exporting data directly to a file
val outputFile = FileOutputStream(File("senators_export.csv"))
val bytesExported = session.copy.copyOut("COPY senators TO STDOUT WITH (FORMAT csv)", outputFile)
println("Successfully exported $bytesExported bytes of data.")
```

For manual chunk reading:
```kotlin
val copyOut = session.copy.copyOut("COPY senators TO STDOUT")
try {
    while (true) {
        val chunk: ByteArray? = copyOut.readFromCopy()
        if (chunk == null) break // Reached end of data
        // Process chunk...
    }
} catch (e: Exception) {
    copyOut.cancelCopy()
    throw e
}
```
