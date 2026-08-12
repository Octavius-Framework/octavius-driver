# Large Objects (LO)

PostgreSQL Large Objects (also known as `lo`) allow storing data that exceeds the size limit of standard `bytea` columns. While the standard `pgjdbc` driver hides its Large Object API behind vendor-specific connection unwrapping, `octavius-driver` makes it a first-class citizen, exposing a native Kotlin interface directly through `LargeObjectManager`.

You can access the manager via `session.largeObjects`.

> [!IMPORTANT]
> All operations on Large Objects must be performed within a transaction block (`BEGIN` / `COMMIT`).

## Creating a Large Object

```kotlin
session.transaction.required {
    // Creates a new empty Large Object and returns its OID
    val oid: Int = session.largeObjects.create()
    println("Created Large Object with OID: $oid")
}
```
*Note: In the JVM, PostgreSQL OIDs (unsigned 32-bit integers) are represented as signed `Int`. It's perfectly safe to use them as-is even if they appear negative.*

## Writing to a Large Object

```kotlin
session.transaction.required {
    val oid = session.largeObjects.create()
    
    // Open for writing
    val lo = session.largeObjects.open(oid, LargeObjectMode.WRITE)
    
    // You can write bytes directly
    val data = "In the name of the Senate and People of Rome".toByteArray()
    lo.write(data)
    
    // Or use an OutputStream for streaming
    lo.outputStream().use { stream ->
        stream.write("...".toByteArray())
    }
    
    // The Large Object must be closed when done
    lo.close()
}
```

## Reading from a Large Object

```kotlin
session.transaction.required {
    val oid = 12345 // OID of existing Large Object
    
    // Open for reading
    val lo = session.largeObjects.open(oid, LargeObjectMode.READ)
    
    // Read bytes directly
    val buffer = ByteArray(1024)
    val bytesRead = lo.read(buffer)
    
    // Or use an InputStream for streaming
    lo.inputStream().use { stream ->
        val content = stream.readAllBytes()
    }
    
    lo.close()
}
```

## Deleting a Large Object

```kotlin
session.transaction.required {
    val oid = 12345
    session.largeObjects.unlink(oid)
}
```

## Moving the cursor (Seek)

The `LargeObject` API provides methods to navigate the object's contents without reading everything into memory:
```kotlin
val lo = session.largeObjects.open(oid, LargeObjectMode.READ)

// Seek to 100 bytes from the beginning
lo.seek(100, LargeObjectSeekWhence.SEEK_SET)

// Seek 50 bytes forward from current position
lo.seek(50, LargeObjectSeekWhence.SEEK_CUR)

// Get current position
val pos = lo.tell()
```
