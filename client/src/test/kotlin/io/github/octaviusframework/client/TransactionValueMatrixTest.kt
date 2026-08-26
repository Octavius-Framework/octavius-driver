package io.github.octaviusframework.client

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.github.octaviusframework.client.transaction.StepHandle
import io.github.octaviusframework.client.transaction.TransactionPlan
import io.github.octaviusframework.client.transaction.TransactionValue
import io.github.octaviusframework.client.transaction.map
import io.github.octaviusframework.client.transaction.toTransactionValue
import io.github.octaviusframework.driver.exception.InvalidOperationException
import io.github.octaviusframework.driver.exception.InvalidOperationExceptionReason
import io.github.octaviusframework.driver.exception.MappingException
import io.github.octaviusframework.driver.exception.OctaviusException
import io.github.octaviusframework.driver.row.Row
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Every kind of [TransactionValue] against every shape of result a step terminal can produce.
 *
 * The two axes do not multiply cleanly, and that is the point of writing them out. `value()` hands on whatever
 * the terminal produced, so whether it can be used at all depends on whether the driver can bind that as a
 * parameter; `field`, `column` and `row` need a result that has columns, which only the `fetchRow*` family
 * produces. What each combination does - work, or fail and how - is what this pins down.
 */
class TransactionValueMatrixTest {

    data class Probe(val id: Int, val name: String, val amount: Int)

    /** Registered against `public.tv_rank`, which is what makes a converter claim that column. */
    enum class Rank { Legatus, Tribunus }

    companion object {
        private lateinit var dataSource: HikariDataSource
        private lateinit var db: OctaviusClient

        @BeforeAll
        @JvmStatic
        fun setUp() {
            dataSource = HikariDataSource(HikariConfig().apply {
                jdbcUrl = "jdbc:octavius://localhost:5432/octavius_test"
                username = "postgres"
                password = "1234"
                maximumPoolSize = 2
            })
            db = OctaviusClient.fromDataSource(dataSource)
            db.rawQuery(
                """
                CREATE TABLE IF NOT EXISTS tv_probe (
                    id     SERIAL PRIMARY KEY,
                    name   TEXT NOT NULL,
                    amount INT  NOT NULL
                );
                CREATE TABLE IF NOT EXISTS tv_sink (
                    id     SERIAL PRIMARY KEY,
                    name   TEXT,
                    amount INT
                )
                """.trimIndent()
            ).execute()
            db.rawQuery(
                "DROP TYPE IF EXISTS public.tv_probe_t CASCADE; " +
                    "CREATE TYPE public.tv_probe_t AS (id int, name text, amount int); " +
                    "DROP TYPE IF EXISTS public.tv_rank CASCADE; " +
                    "CREATE TYPE public.tv_rank AS ENUM ('LEGATUS', 'TRIBUNUS'); " +
                    "DROP TABLE IF EXISTS tv_shapes; " +
                    "CREATE TABLE tv_shapes (id SERIAL PRIMARY KEY, rank public.tv_rank, doc jsonb)"
            ).execute()
            db.execute {
                reloadTypes()
                typeManager.registerEnum<Rank>("tv_rank", "public")
            }
        }

        @AfterAll
        @JvmStatic
        fun tearDown() {
            db.rawQuery("DROP TABLE IF EXISTS tv_sink; DROP TABLE IF EXISTS tv_probe; DROP TABLE IF EXISTS tv_shapes; DROP TYPE IF EXISTS public.tv_probe_t; DROP TYPE IF EXISTS public.tv_rank").execute()
            db.close()
            dataSource.close()
        }
    }

    @BeforeEach
    fun seed() {
        db.rawQuery("TRUNCATE tv_sink, tv_probe, tv_shapes RESTART IDENTITY").execute()
        db.insertInto("tv_probe").values(listOf("name", "amount"))
            .update("name" to "Gallia", "amount" to 300)
        db.insertInto("tv_probe").values(listOf("name", "amount"))
            .update("name" to "Hispania", "amount" to 250)
        db.insertInto("tv_probe").values(listOf("name", "amount"))
            .update("name" to "Britannia", "amount" to 100)
    }

    /** A step over all three probe rows, for the terminals that return many. */
    private fun probeAll() = db.select("id", "name", "amount").from("tv_probe").orderBy("id").asStep()

    /** A step over one probe row, for the terminals that insist on exactly one. */
    private fun probeOne() = db.select("id", "name", "amount").from("tv_probe").where("id = 1").asStep()

    /**
     * Adds a producing step, then a step that binds [use] of it and hands the bound value straight back.
     *
     * Passing the value through the server and reading it again is what says it really resolved *and* that
     * the driver could send it - a resolution nothing can bind is not a usable one.
     */
    private inline fun <reified R> throughParameter(
        producer: (TransactionPlan) -> StepHandle<*>,
        sql: String = "SELECT @v AS v",
        use: (StepHandle<*>) -> Any?
    ): R {
        val plan = TransactionPlan()
        val source = producer(plan)
        val sink = plan.add(db.rawQuery(sql).asStep().fetchFieldStrict<R>("v" to use(source)))
        return db.executeTransactionPlan(plan)[sink]
    }

    // --- value(), by what the terminal produced ----------------------------------------------------

    @Test
    fun `value carries a scalar from fetchFieldStrict`() {
        val out: Int = throughParameter(
            producer = { it.add(db.select("amount").from("tv_probe").where("id = 1").asStep().fetchFieldStrict<Int>()) },
            use = { it.value() }
        )
        assertEquals(300, out)
    }

    @Test
    fun `value carries the count from update`() {
        val out: Long = throughParameter(
            producer = {
                it.add(
                    db.update("tv_probe").setValues(listOf("amount")).where("true")
                        .asStep().update("amount" to 1)
                )
            },
            use = { it.value() }
        )
        assertEquals(3L, out)
    }

    @Test
    fun `value carries a list from fetchFields, which binds as an array`() {
        val out: Long = throughParameter(
            producer = { it.add(db.select("amount").from("tv_probe").orderBy("id").asStep().fetchFields<Int>()) },
            sql = "SELECT count(*) FROM unnest(@v) AS x",
            use = { it.value() }
        )
        assertEquals(3L, out)
    }

    @Test
    fun `value on a row-shaped step resolves to a Row, which nothing can bind`() {
        val thrown = assertFailsWith<OctaviusException> {
            throughParameter<Any?>(
                producer = { it.add(probeOne().fetchRowStrict()) },
                use = { it.value() }
            )
        }
        assertTrue(
            thrown.getDetailedMessage()!!.contains("Row"),
            "the failure should name what it could not send: ${thrown.message}"
        )
    }

    @Test
    fun `value on an object step needs the class to be a type the registry knows`() {
        val thrown = assertFailsWith<OctaviusException> {
            throughParameter<Any?>(
                producer = { it.add(probeOne().fetchObjectStrict<Probe>()) },
                use = { it.value() }
            )
        }
        assertTrue(thrown.getDetailedMessage()!!.contains("Probe"), thrown.getDetailedMessage()!!)
    }

    @Test
    fun `value on an object step binds once that class is a registered composite`() {
        // The other half of the test above, and the reason its name says "the registry knows" rather than
        // "nothing can bind": what stops an object being sent is having no composite to be sent as, not being
        // an object. Register one and value() carries the whole thing to the next step.
        db.execute { typeManager.registerAutoComposite<Probe>("tv_probe_t", "public") }

        val out: String = throughParameter(
            producer = { it.add(probeOne().fetchObjectStrict<Probe>()) },
            sql = "SELECT (@v).name AS v",
            use = { it.value() }
        )
        assertEquals("Gallia", out)
    }

    @Test
    fun `map is how an object result becomes something bindable`() {
        // Which is what Transformed is for: value() reaches the object, map() takes the part that can be sent.
        val out: String = throughParameter(
            producer = { it.add(probeOne().fetchObjectStrict<Probe>()) },
            use = { handle ->
                @Suppress("UNCHECKED_CAST")
                (handle.value() as TransactionValue<Probe>).map { probe -> probe.name }
            }
        )
        assertEquals("Gallia", out)
    }

    // --- field(), which needs columns --------------------------------------------------------------

    @Test
    fun `field takes a column of a fetchRowStrict result`() {
        val out: String = throughParameter(
            producer = { it.add(probeOne().fetchRowStrict()) },
            use = { it.field("name") }
        )
        assertEquals("Gallia", out)
    }

    @Test
    fun `field takes a column of a fetchRow result`() {
        val out: String = throughParameter(
            producer = { it.add(probeOne().fetchRow()) },
            use = { it.field("name") }
        )
        assertEquals("Gallia", out)
    }

    @Test
    fun `field indexes into a fetchRows result`() {
        val out: String = throughParameter(
            producer = { it.add(probeAll().fetchRows()) },
            use = { it.field("name", rowIndex = 2) }
        )
        assertEquals("Britannia", out)
    }

    @Test
    fun `field on a scalar result says it has no columns`() {
        val thrown = assertFailsWith<OctaviusException> {
            throughParameter<Any?>(
                producer = { it.add(probeOne().fetchFieldStrict<Int>()) },
                use = { it.field("name") }
            )
        }
        assertTrue(thrown.getDetailedMessage()!!.contains("no columns"), thrown.getDetailedMessage()!!)
    }

    @Test
    fun `field on an object result says it has no columns`() {
        val thrown = assertFailsWith<OctaviusException> {
            throughParameter<Any?>(
                producer = { it.add(probeOne().fetchObjectStrict<Probe>()) },
                use = { it.field("name") }
            )
        }
        assertTrue(thrown.getDetailedMessage()!!.contains("no columns"), thrown.getDetailedMessage()!!)
    }

    @Test
    fun `field on a list of non-rows says it has no columns`() {
        val thrown = assertFailsWith<OctaviusException> {
            throughParameter<Any?>(
                producer = { it.add(probeAll().fetchFields<Int>()) },
                use = { it.field("name") }
            )
        }
        assertTrue(thrown.getDetailedMessage()!!.contains("no columns"), thrown.getDetailedMessage()!!)
    }

    @Test
    fun `field on a null fetchRow result says it has no columns`() {
        val thrown = assertFailsWith<OctaviusException> {
            throughParameter<Any?>(
                producer = {
                    it.add(db.select("id").from("tv_probe").where("false").asStep().fetchRow())
                },
                use = { it.field("id") }
            )
        }
        assertTrue(thrown.getDetailedMessage()!!.contains("no columns"), thrown.getDetailedMessage()!!)
    }

    @Test
    fun `field naming a column the rows do not have lists the ones they do`() {
        val thrown = assertFailsWith<OctaviusException> {
            throughParameter<Any?>(
                producer = { it.add(probeOne().fetchRowStrict()) },
                use = { it.field("tribute") }
            )
        }
        assertTrue(thrown.getDetailedMessage()!!.contains("tribute"), thrown.getDetailedMessage()!!)
        assertTrue(thrown.getDetailedMessage()!!.contains("name"), thrown.getDetailedMessage()!!)
    }

    @Test
    fun `field past the last row says how many there were`() {
        val thrown = assertFailsWith<OctaviusException> {
            throughParameter<Any?>(
                producer = { it.add(probeAll().fetchRows()) },
                use = { it.field("name", rowIndex = 9) }
            )
        }
        assertTrue(thrown.getDetailedMessage()!!.contains("3 row"), thrown.getDetailedMessage()!!)
    }

    // --- column(), one column of every row ---------------------------------------------------------

    @Test
    fun `column gathers a column of a fetchRows result`() {
        val out: Long = throughParameter(
            producer = { it.add(probeAll().fetchRows()) },
            sql = "SELECT count(*) FROM tv_probe WHERE id = ANY(@v)",
            use = { it.column("id") }
        )
        assertEquals(3L, out)
    }

    @Test
    fun `column of a single-row result is a list of one`() {
        val out: Long = throughParameter(
            producer = { it.add(probeOne().fetchRowStrict()) },
            sql = "SELECT count(*) FROM tv_probe WHERE id = ANY(@v)",
            use = { it.column("id") }
        )
        assertEquals(1L, out)
    }

    @Test
    fun `column on a scalar result says it has no columns`() {
        val thrown = assertFailsWith<OctaviusException> {
            throughParameter<Any?>(
                producer = { it.add(probeOne().fetchFieldStrict<Int>()) },
                use = { it.column("id") }
            )
        }
        assertTrue(thrown.getDetailedMessage()!!.contains("no columns"), thrown.getDetailedMessage()!!)
    }

    // --- row(), which is spread rather than assigned -----------------------------------------------

    @Test
    fun `row spreads its columns into parameters of their own`() {
        val plan = TransactionPlan()
        val source = plan.add(probeOne().fetchRowStrict())
        plan.add(
            db.insertInto("tv_sink").values(listOf("name", "amount"))
                .asStep().update("ignored" to source.row())
        )
        db.executeTransactionPlan(plan)

        assertEquals(
            Probe(1, "Gallia", 300),
            db.select("id", "name", "amount").from("tv_sink").fetchObjectStrict<Probe>()
        )
    }

    @Test
    fun `row indexes into a fetchRows result`() {
        val plan = TransactionPlan()
        val source = plan.add(probeAll().fetchRows())
        plan.add(
            db.insertInto("tv_sink").values(listOf("name", "amount"))
                .asStep().update("ignored" to source.row(rowIndex = 2))
        )
        db.executeTransactionPlan(plan)

        assertEquals("Britannia", db.select("name").from("tv_sink").fetchFieldStrict<String>())
    }

    @Test
    fun `row on a scalar result says it has no columns`() {
        val plan = TransactionPlan()
        val source = plan.add(probeOne().fetchFieldStrict<Int>())
        plan.add(
            db.insertInto("tv_sink").values(listOf("name", "amount"))
                .asStep().update("ignored" to source.row())
        )

        val thrown = assertFailsWith<OctaviusException> { db.executeTransactionPlan(plan) }
        assertTrue(thrown.getDetailedMessage()!!.contains("no columns"), thrown.getDetailedMessage()!!)
    }

    // --- Value and Transformed ---------------------------------------------------------------------

    @Test
    fun `a plain value passes through untouched`() {
        val plan = TransactionPlan()
        val sink = plan.add(db.rawQuery("SELECT @v AS v").asStep().fetchFieldStrict<Int>("v" to 42))
        assertEquals(42, db.executeTransactionPlan(plan)[sink])
    }

    @Test
    fun `a wrapped known value resolves to itself`() {
        val plan = TransactionPlan()
        val sink = plan.add(
            db.rawQuery("SELECT @v AS v").asStep().fetchFieldStrict<Int>("v" to 42.toTransactionValue())
        )
        assertEquals(42, db.executeTransactionPlan(plan)[sink])
    }

    @Test
    fun `map runs over a field`() {
        val out: String = throughParameter(
            producer = { it.add(probeOne().fetchRowStrict()) },
            use = { it.field("name").map { name -> "Provincia $name" } }
        )
        assertEquals("Provincia Gallia", out)
    }

    @Test
    fun `map runs over a column, and can collapse it to a scalar`() {
        val out: Int = throughParameter(
            producer = { it.add(probeAll().fetchRows()) },
            use = { it.column("id").map { ids -> ids.size } }
        )
        assertEquals(3, out)
    }

    @Test
    fun `map nests`() {
        val out: Int = throughParameter(
            producer = { it.add(probeAll().fetchRows()) },
            use = { it.column("id").map { ids -> ids.size }.map { size -> size * 10 } }
        )
        assertEquals(30, out)
    }

    @Test
    fun `map over a row sees the map and is assigned rather than spread`() {
        // The spread is a property of FromStep.Row itself, so wrapping it in a Transformed takes that away:
        // what comes out is one ordinary parameter again.
        val out: Int = throughParameter(
            producer = { it.add(probeOne().fetchRowStrict()) },
            use = { handle -> handle.row().map { row -> row.size } }
        )
        assertEquals(3, out)
    }

    // --- What a value looks like when it is carried between steps -----------------------------------

    @Test
    fun `an enum and a jsonb column carry between steps, type and all`() {
        // These are the two that would not, were the codec's own output carried: an enum's label and a jsonb
        // document are both Strings, and a parameter's type is declared from its Kotlin class, so both would
        // go back out as `text` and be refused by the column they came from. Asking the row for Any? runs the
        // result converters instead, and what they produce - the enum, a JsonElement - has a parameter
        // converter that names its own PostgreSQL type.
        db.rawQuery("INSERT INTO tv_shapes (rank, doc) VALUES ('LEGATUS', '{\"a\": 1}')").execute()

        val plan = TransactionPlan()
        val source = plan.add(db.select("rank", "doc").from("tv_shapes").asStep().fetchRowStrict())
        plan.add(
            db.insertInto("tv_shapes").values(listOf("rank", "doc"))
                .asStep().update("rank" to source.field("rank"), "doc" to source.field("doc"))
        )
        db.executeTransactionPlan(plan)

        assertEquals(
            2L,
            db.rawQuery("SELECT count(*) FROM tv_shapes WHERE rank = 'LEGATUS' AND doc = '{\"a\": 1}'")
                .fetchFieldStrict<Long>()
        )
        assertEquals(
            listOf(Rank.Legatus, Rank.Legatus),
            db.select("rank").from("tv_shapes").orderBy("id").fetchFields<Rank>()
        )
    }

    @Test
    fun `a jsonb column arrives as a JsonElement rather than as its text`() {
        db.rawQuery("INSERT INTO tv_shapes (rank, doc) VALUES ('LEGATUS', '{\"a\": 1}')").execute()

        val plan = TransactionPlan()
        val source = plan.add(db.select("doc").from("tv_shapes").asStep().fetchRowStrict())
        val kind = plan.add(
            db.rawQuery("SELECT @v AS v").asStep()
                .fetchFieldStrict<String>("v" to source.field("doc").map { it!!::class.simpleName })
        )

        assertTrue(
            db.executeTransactionPlan(plan)[kind].contains("Json"),
            "a converter ran, so this is a JsonElement and not a String"
        )
    }

    @Test
    fun `an int column carries between steps, its codec class being its own type`() {
        // The other side of the limitation above: where the codec's class maps back to the same PostgreSQL
        // type, which is most of them, carrying a value between steps round-trips as it should.
        val plan = TransactionPlan()
        val source = plan.add(probeOne().fetchRowStrict())
        val sink = plan.add(
            db.rawQuery("SELECT @v AS v").asStep().fetchFieldStrict<Int>("v" to source.field("amount"))
        )
        assertEquals(300, db.executeTransactionPlan(plan)[sink])
    }

    // --- What the caller's own code does ------------------------------------------------------------

    @Test
    fun `a transformation that throws arrives as a MappingException naming the parameter`() {
        val plan = TransactionPlan()
        val source = plan.add(probeOne().fetchRowStrict())
        plan.add(
            db.insertInto("tv_sink").values(listOf("amount"))
                .asStep().update("amount" to source.field("name").map { it as Int })
        )

        val thrown = assertFailsWith<MappingException> { db.executeTransactionPlan(plan) }

        assertTrue(thrown.details.contains("'amount'"), thrown.details)
        assertIs<ClassCastException>(thrown.cause, "what was actually thrown has to survive as the cause")
        assertEquals(0L, db.rawQuery("SELECT count(*) FROM tv_sink").fetchFieldStrict<Long>())
    }

    @Test
    fun `a transformation that throws is a failure the result style can see`() {
        // The reason wrapping is worth doing at all: dbResult catches OctaviusException and nothing else, so
        // a bare ClassCastException would travel straight through the boundary instead of being classified.
        val plan = TransactionPlan()
        val source = plan.add(probeOne().fetchRowStrict())
        plan.add(
            db.insertInto("tv_sink").values(listOf("amount"))
                .asStep().update("amount" to source.field("name").map { it as Int })
        )

        // MappingException is a caller bug, so the boundary rethrows it rather than returning it - which is
        // the point: it reaches the classification at all, instead of going round it.
        assertFailsWith<MappingException> { dbResult { db.executeTransactionPlan(plan) } }
    }

    @Test
    fun `a transformation throwing an OctaviusException is passed through as it is`() {
        val plan = TransactionPlan()
        val source = plan.add(probeOne().fetchRowStrict())
        plan.add(
            db.insertInto("tv_sink").values(listOf("amount")).asStep().update(
                "amount" to source.field("name").map<Any?, Any?> {
                    throw InvalidOperationException(
                        InvalidOperationExceptionReason.INVALID_ARGUMENT,
                        details = "raised by the caller's own code"
                    )
                }
            )
        )

        val thrown = assertFailsWith<InvalidOperationException> { db.executeTransactionPlan(plan) }
        assertEquals("raised by the caller's own code", thrown.details)
    }

    @Test
    fun `the parameter is named however deep the transformation that threw`() {
        val plan = TransactionPlan()
        val source = plan.add(probeOne().fetchRowStrict())
        plan.add(
            db.insertInto("tv_sink").values(listOf("amount")).asStep().update(
                "amount" to source.field("amount").map { it as Int }.map { listOf<Int>()[it] }
            )
        )

        val thrown = assertFailsWith<MappingException> { db.executeTransactionPlan(plan) }
        assertTrue(thrown.details.contains("'amount'"), thrown.details)
        assertIs<IndexOutOfBoundsException>(thrown.cause)
    }

    // --- Handles across plans ----------------------------------------------------------------------

    @Test
    fun `a handle from another plan is refused before the transaction is opened`() {
        val other = TransactionPlan()
        val stranger = other.add(probeOne().fetchRowStrict())

        val plan = TransactionPlan()
        plan.add(
            db.insertInto("tv_sink").values(listOf("name"))
                .asStep().update("name" to stranger.field("name"))
        )

        val thrown = assertFailsWith<OctaviusException> { db.executeTransactionPlan(plan) }
        assertTrue(thrown.getDetailedMessage()!!.contains("not a step of"), thrown.getDetailedMessage()!!)
        // Nothing ran: the row this step would have written is not there, and it is not there because the
        // plan was refused rather than because a transaction rolled back.
        assertEquals(0L, db.rawQuery("SELECT count(*) FROM tv_sink").fetchFieldStrict<Long>())
    }

    @Test
    fun `a foreign handle is found however many transformations wrap it`() {
        val other = TransactionPlan()
        val stranger = other.add(probeOne().fetchRowStrict())

        val plan = TransactionPlan()
        plan.add(
            db.insertInto("tv_sink").values(listOf("name"))
                .asStep().update("name" to stranger.field("name").map { it.toString() }.map { it.uppercase() })
        )

        val thrown = assertFailsWith<OctaviusException> { db.executeTransactionPlan(plan) }
        assertTrue(thrown.getDetailedMessage()!!.contains("not a step of"), thrown.getDetailedMessage()!!)
    }

    @Test
    fun `a step whose query cannot render is refused before anything runs, and says which step`() {
        val plan = TransactionPlan()
        plan.add(db.insertInto("tv_sink").values(listOf("name")).asStep().update("name" to "Gallia"))
        // An UPDATE with no WHERE never renders, and until now that surfaced only once step 1 had inserted.
        plan.add(db.update("tv_sink").setValues(listOf("name")).asStep().update("name" to "Roma"))

        val thrown = assertFailsWith<OctaviusException> { db.executeTransactionPlan(plan) }
        assertTrue(thrown.getDetailedMessage()!!.contains("Step 1"), thrown.getDetailedMessage()!!)
        assertEquals(0L, db.rawQuery("SELECT count(*) FROM tv_sink").fetchFieldStrict<Long>())
    }

    @Test
    fun `an empty plan produces an empty result rather than a failure`() {
        // The short-circuit that skips the transaction is not observable from here - what this pins is the
        // contract either way round: no steps, no result, no exception.
        val results = db.executeTransactionPlan(TransactionPlan())
        assertEquals(0, results.size)
    }

    @Test
    fun `rows carried between steps keep their decoded values`() {
        // getRaw hands on what the driver decoded, so what the next step binds is a Kotlin Int and a String,
        // not text that happens to look like them.
        val plan = TransactionPlan()
        val source = plan.add(probeOne().fetchRowStrict())
        val sink = plan.add(
            db.rawQuery("SELECT @amount + 1 AS v").asStep().fetchFieldStrict<Int>("amount" to source.field("amount"))
        )
        assertEquals(301, db.executeTransactionPlan(plan)[sink])
    }

    @Test
    fun `a row-shaped step still answers value when nothing needs to bind it`() {
        // value() on a fetchRowStrict is only unusable as a *parameter*; as the plan's own result it is fine.
        val plan = TransactionPlan()
        val source = plan.add(probeOne().fetchRowStrict())
        val results = db.executeTransactionPlan(plan)

        val row: Row = results[source]
        assertEquals("Gallia", row.get<String>("name"))
    }
}
