package pl.benchmark

import io.github.octaviusframework.driver.registry.TypeRegistry
import io.github.octaviusframework.driver.type.PgType
import io.github.octaviusframework.driver.codec.dynamic.ContainerCodec
import org.openjdk.jmh.annotations.*
import java.util.concurrent.TimeUnit

@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.NANOSECONDS) // Zmieniamy na nanosekundy!
@BenchmarkMode(Mode.AverageTime)
open class CodecIsolationBenchmark {

    lateinit var typeRegistry: TypeRegistry
    lateinit var rawPayload: ByteArray
    var targetOid: Int = 0

    @Setup(Level.Trial)
    fun setup() {
        typeRegistry = TypeRegistry()

        // 1. Definiujemy OIDy (możesz użyć dowolnych liczb, byleby zgadzały się z payloadem)
        val intOid = 23
        val textOid = 25
        val boolOid = 16
        val threadCompositeOid = 45987
        val threadArrayOid = 45986

        // 2. Budujemy mapę typów, którą normalnie sterownik czyta z pg_type
        val fakeTypes = mapOf(
            intOid to PgType.Base(intOid, "int4", "pg_catalog"),
            textOid to PgType.Base(textOid, "text", "pg_catalog"),
            boolOid to PgType.Base(boolOid, "bool", "pg_catalog"),
            
            // Kompozyt qds_thread (odwzorowanie struktury z bazy)
            threadCompositeOid to PgType.Composite(
                oid = threadCompositeOid,
                name = "qds_thread",
                schema = "public",
                attributes = linkedMapOf(
                    "id" to intOid,
                    "scenario_id" to intOid,
                    "title" to textOid,
                    "description" to textOid,
                    "is_global" to boolOid
                )
            ),
            
            // Tablica kompozytów (bo zwracasz ARRAY_AGG(t))
            threadArrayOid to PgType.Array(
                oid = threadArrayOid,
                name = "_qds_thread",
                schema = "public",
                elementOid = threadCompositeOid
            )
        )

        // 3. Wstrzykujemy fake'owe typy do rejestru
        typeRegistry.updateTypes(fakeTypes)

        // 4. Ustawiamy co będziemy parsować (Tablicę)
        targetOid = threadArrayOid

        // 5. TUTAJ WKLEJ ZAZRZUTOWANE BAJTY
        // Zastąp to tablicą wyplutą przez println(data.contentToString())
        rawPayload = byteArrayOf(0, 0, 0, 1, 0, 0, 0, 0, 0, 0, -77, -93, 0, 0, 0, 5, 0, 0, 0, 1, 0, 0, 0, 83, 0, 0, 0, 5, 0, 0, 0, 23, 0, 0, 0, 4, 0, 0, 0, 1, 0, 0, 0, 16, 0, 0, 0, 1, 1, 0, 0, 0, 23, 0, 0, 0, 4, 0, 0, 0, 1, 0, 0, 0, 25, 0, 0, 0, 15, 87, -60, -123, 116, 101, 107, 32, 103, 108, 111, 98, 97, 108, 110, 121, 0, 0, 0, 25, 0, 0, 0, 15, 87, -60, -123, 116, 101, 107, 32, 103, 108, 111, 98, 97, 108, 110, 121, 0, 0, 0, 79, 0, 0, 0, 5, 0, 0, 0, 23, 0, 0, 0, 4, 0, 0, 0, 2, 0, 0, 0, 16, 0, 0, 0, 1, 0, 0, 0, 0, 23, 0, 0, 0, 4, 0, 0, 0, 1, 0, 0, 0, 25, 0, 0, 0, 17, 80, 114, 122, 121, 98, 121, 99, 105, 101, 32, 115, 116, 114, 97, -59, -68, 121, 0, 0, 0, 25, 0, 0, 0, 9, 80, 114, 122, 121, 98, 121, 99, 105, 101, 0, 0, 0, 79, 0, 0, 0, 5, 0, 0, 0, 23, 0, 0, 0, 4, 0, 0, 0, 3, 0, 0, 0, 16, 0, 0, 0, 1, 0, 0, 0, 0, 23, 0, 0, 0, 4, 0, 0, 0, 1, 0, 0, 0, 25, 0, 0, 0, 17, 69, 119, 97, 107, 117, 97, 99, 106, 97, 32, 98, 117, 100, 121, 110, 107, 117, 0, 0, 0, 25, 0, 0, 0, 9, 69, 119, 97, 107, 117, 97, 99, 106, 97, 0, 0, 0, 69, 0, 0, 0, 5, 0, 0, 0, 23, 0, 0, 0, 4, 0, 0, 0, 4, 0, 0, 0, 16, 0, 0, 0, 1, 0, 0, 0, 0, 23, 0, 0, 0, 4, 0, 0, 0, 1, 0, 0, 0, 25, 0, 0, 0, 8, 71, 97, 115, 122, 101, 110, 105, 101, 0, 0, 0, 25, 0, 0, 0, 8, 71, 97, 115, 122, 101, 110, 105, 101, 0, 0, 0, 83, 0, 0, 0, 5, 0, 0, 0, 23, 0, 0, 0, 4, 0, 0, 0, 5, 0, 0, 0, 16, 0, 0, 0, 1, 0, 0, 0, 0, 23, 0, 0, 0, 4, 0, 0, 0, 1, 0, 0, 0, 25, 0, 0, 0, 15, 75, 111, 110, 105, 101, 99, 32, 103, 97, 115, 122, 101, 110, 105, 97, 0, 0, 0, 25, 0, 0, 0, 15, 75, 111, 110, 105, 101, 99, 32, 103, 97, 115, 122, 101, 110, 105, 97)
    }

    @Benchmark
    fun benchmarkContainerDecoding(): Any {
        // Czysta kalkulacja CPU - zero sieci, zero I/O!
        // Upewnij się, że targetOid pokrywa się z głównym typem bajtów.
        return ContainerCodec.parseContainer(rawPayload, 0,  targetOid, typeRegistry)
    }
}
