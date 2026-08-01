package io.github.octaviusframework.driver.benchmarks

import io.github.octaviusframework.driver.jdbc.getOctaviusSession
import io.github.octaviusframework.driver.session.OctaviusSession
import io.github.octaviusframework.driver.query.NativeQuery
import io.github.octaviusframework.driver.row.get
import org.openjdk.jmh.annotations.*
import java.sql.Connection
import java.sql.DriverManager
import java.sql.PreparedStatement
import java.util.concurrent.TimeUnit
import java.util.Properties

@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@BenchmarkMode(Mode.Throughput)
open class SimpleTypeBenchmark {

    private lateinit var pgConnection: Connection
    private lateinit var pgStatement: PreparedStatement

    private lateinit var octaviusSession: OctaviusSession
    private lateinit var octaviusQuery: NativeQuery

    @Setup(Level.Trial)
    fun setup() {
        Class.forName("org.postgresql.Driver")
        Class.forName("io.github.octaviusframework.driver.jdbc.OctaviusDriver")

        val props = Properties()
        props["user"] = "postgres"
        props["password"] = "1234"

        pgConnection = DriverManager.getConnection("jdbc:postgresql://localhost:5432/octavius_test", props)
        val query = "SELECT i::int4, 'hello world ' || i::text, (i % 2 = 0)::boolean, (i * 3.14)::float8 FROM generate_series(1, 10000) AS i"
        pgStatement = pgConnection.prepareStatement(query)

        octaviusSession = getOctaviusSession("jdbc:octavius://localhost:5432/octavius_test", "postgres", "1234")
        octaviusQuery = octaviusSession.createNativeQuery(query)
    }

    @TearDown(Level.Trial)
    fun tearDown() {
        pgStatement.close()
        pgConnection.close()
        octaviusSession.close()
    }

    @Benchmark
    fun pgjdbc_simpleTypes(): Int {
        var count = 0
        pgStatement.executeQuery().use { rs ->
            while (rs.next()) {
                val i = rs.getInt(1)
                val s = rs.getString(2)
                val b = rs.getBoolean(3)
                val d = rs.getDouble(4)
                count += i + (if(b) 1 else 0) + s.length + d.toInt()
            }
        }
        return count
    }

    @Benchmark
    fun octavius_simpleTypes(): Int {
        var count = 0
        val rows = octaviusQuery.fetchAll()
        for (row in rows) {
            val i = row.get<Int>(0)
            val s = row.get<String>(1)
            val b = row.get<Boolean>(2)
            val d = row.get<Double>(3)
            count += i + (if(b) 1 else 0) + s.length + d.toInt()
        }
        return count
    }
}
