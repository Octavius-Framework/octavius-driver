# Large Objects (LO)

PostgreSQL Large Objects store data that would be awkward in a `bytea` column — `bytea` tops out at 1 GB and is read and written whole, while a Large Object goes to 4 TB and can be seeked through and updated in place. Where `pgjdbc` hides its Large Object API behind vendor-specific connection unwrapping, Octavius makes it a first-class citizen through `LargeObjectManager`, reachable at `session.largeObjects`.

Reach for one when you need random access into a big blob, or when the data genuinely exceeds what `bytea` can hold. For anything that comfortably fits and is always read in full, a `bytea` column mapped to `ByteArray` is simpler and needs no transaction ceremony.

> [!IMPORTANT]
> **Every Large Object operation must happen inside a transaction.** This is PostgreSQL's rule, not the driver's: the descriptor returned by `open` is only valid for the transaction that created it. Outside one, each statement commits on its own and the descriptor is dead by the time you use it — which surfaces as `StatementException(UNDEFINED_OBJECT)`, PostgreSQL's "invalid large-object descriptor", on the first read or write rather than at `open`.

## Creating and writing

```kotlin
session.transaction.required {
    val oid: Int = session.largeObjects.create()

    session.largeObjects.open(oid, LargeObjectMode.WRITE).use { lo ->
        lo.write("In the name of the Senate and People of Rome".toByteArray())

        // Or stream into it
        lo.outputStream().use { stream ->
            stream.write(" - and of the Legions".toByteArray())
        }
    }
}
```

`LargeObject` is `AutoCloseable`, so `use { }` closes the descriptor for you. `open` takes `LargeObjectMode.READ`, `.WRITE` or `.READ_WRITE`, defaulting to `READ_WRITE`.

*Note: PostgreSQL OIDs are unsigned 32-bit integers, represented on the JVM as a signed `Int`. Use them as they come, even when they look negative.*

## Reading

```kotlin
session.transaction.required {
    session.largeObjects.open(oid, LargeObjectMode.READ).use { lo ->
        // A fixed number of bytes from the current position
        val head: ByteArray = lo.read(64)

        // Or into a buffer you own; returns the count read, -1 at the end
        val buffer = ByteArray(8192)
        val bytesRead = lo.read(buffer)

        // Or as a stream, for the whole thing
        val content = lo.inputStream().readAllBytes()
    }
}
```

## Moving around and resizing

Random access is the reason to use a Large Object in the first place:

```kotlin
session.transaction.required {
    session.largeObjects.open(oid).use { lo ->
        lo.seek(100L, SeekWhence.SET)   // 100 bytes from the start
        lo.seek(50L, SeekWhence.CUR)    // 50 bytes further on
        lo.seek(-10L, SeekWhence.END)   // 10 bytes before the end

        val position: Long = lo.tell()

        lo.truncate(1024L)              // cut to 1 KB, or pad with zero bytes if shorter
    }
}
```

`seek` takes a `Long`, so write `100L` rather than `100`. Positions are `Long` throughout, which is what lets a Large Object exceed the 2 GB an `Int` would cap it at.

## Deleting

```kotlin
session.transaction.required {
    session.largeObjects.unlink(oid)
}
```

An `unlink` is permanent and takes effect with the surrounding transaction, so rolling back keeps the object. Note that deleting a *row* holding an OID does not delete the object it points at — Large Objects are independent of any table referencing them, which is a common way to accumulate orphans. If you store OIDs in a table, either unlink explicitly wherever you delete the row, or use PostgreSQL's `lo` extension, whose trigger does it for you.
