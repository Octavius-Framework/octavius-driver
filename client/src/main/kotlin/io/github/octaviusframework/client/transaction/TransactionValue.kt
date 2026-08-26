package io.github.octaviusframework.client.transaction

/**
 * A parameter value in a [TransactionPlan] step, which may not exist yet.
 *
 * A plan is assembled before any of it runs, so a step that needs the id of a row a previous step will insert
 * cannot be handed that id - there is none. It is handed one of these instead, and the executor replaces it
 * with the real value at the moment the step runs.
 *
 * Anything that is not one of these is passed through untouched, so an ordinary parameter map needs no
 * wrapping: only the values that depend on an earlier step do.
 *
 * @param T The type the value will have once resolved.
 */
sealed class TransactionValue<out T> {

    /** A value that was known all along. Rarely written by hand; [toTransactionValue] is shorter. */
    data class Value<out T>(val value: T) : TransactionValue<T>()

    /** A value taken from the result of an earlier step. */
    sealed class FromStep<out T> : TransactionValue<T>() {

        /** Which step to take it from. */
        abstract val handle: StepHandle<*>

        /**
         * The earlier step's result, whole and as its terminal produced it.
         *
         * Named for the result rather than for a field because a step's terminal decides what it produced,
         * and it is not always a field: `fetchFieldStrict<Int>` gives an `Int`, `fetchObjectStrict<Senator>`
         * a `Senator`, `fetchRows` a list. [Field] is for reaching into one of those that is row-shaped.
         *
         * Resolving is not the whole of it: what comes out still has to be something the driver can send. A
         * scalar can, and a list of scalars goes as an array, but a `Row`, a `List<Row>` or a class the type
         * registry has never heard of is refused where the parameter is encoded, naming the class.
         * [Transformed] is the way across.
         */
        data class Whole(override val handle: StepHandle<*>) : FromStep<Any?>()

        /**
         * One column of one row of the earlier step's result.
         *
         * The result has to be row-shaped for this to mean anything - a `Row` or a `List<Row>`, which is what
         * `fetchRow*` and `fetchRows` produce.
         */
        data class Field(
            override val handle: StepHandle<*>,
            val columnName: String,
            val rowIndex: Int = 0
        ) : FromStep<Any?>()

        /**
         * One column of every row of the earlier step's result, as a list.
         *
         * For passing a set of ids on to the next step - `WHERE id = ANY(@ids)`, or an `UNNEST` insert.
         */
        data class Column(
            override val handle: StepHandle<*>,
            val columnName: String
        ) : FromStep<List<Any?>>()

        /**
         * A whole row of the earlier step's result, as a map of column name to value.
         *
         * Used as a parameter value this one is **spread**: its entries become parameters in their own right,
         * under their column names, and the name it was filed under is dropped - it is a placeholder and
         * nothing binds it. That is what makes copying a row with a change or two a single step rather than
         * one parameter per column. The spread belongs to this type rather than to the value inside it, so a
         * [Transformed] wrapping one of these is assigned under its name like anything else.
         */
        data class Row(
            override val handle: StepHandle<*>,
            val rowIndex: Int = 0
        ) : FromStep<Map<String, Any?>>()
    }

    /**
     * Another value with a function applied to it, run when that value is resolved.
     *
     * @param source Where the input comes from.
     * @param transform What to do to it.
     */
    class Transformed<IN, out OUT>(
        val source: TransactionValue<IN>,
        val transform: (IN) -> OUT
    ) : TransactionValue<OUT>()
}

/**
 * Applies [transformation] to this value once it resolves.
 *
 * The step that uses the result gets what the function returned, so a handle on an id can become a formatted
 * reference, a list can become its size, and no step has to be added just to compute it.
 *
 * One thing it takes away, and the only case where it does: a [StepHandle.row] used as it comes is **spread**
 * into parameters under the row's own column names, with the name it was filed under discarded. Transformed,
 * it is no longer that value but a value computed from it, so it is assigned like any other - under the name
 * it was filed under, which stops being a placeholder and starts being the parameter the SQL has to name.
 *
 * @param transformation What to do to the resolved value.
 * @return The transformed value, still unresolved.
 */
fun <IN, OUT> TransactionValue<IN>.map(transformation: (IN) -> OUT): TransactionValue<OUT> =
    TransactionValue.Transformed(this, transformation)

/**
 * Wraps a value that is already known, for the occasional parameter map that mixes the two.
 *
 * @return The value, as something a step will accept alongside its unresolved siblings.
 */
fun <T> T.toTransactionValue(): TransactionValue.Value<T> = TransactionValue.Value(this)

/**
 * Names a step in a [TransactionPlan], so that later steps can refer to what it will produce.
 *
 * Handed out by [TransactionPlan.add] and useful only inside the plan it came from. Identity is the whole of
 * it: two handles are the same handle or they are not, and nothing else about one is inspectable.
 *
 * @param T What the step this names will produce.
 */
class StepHandle<out T> internal constructor(private val position: Int) {

    /**
     * The step's result, whole and as its terminal produced it - the one that comes up.
     *
     * After `fetchFieldStrict<Int>` this is the `Int`, after `fetchObjectStrict<Senator>` the `Senator`,
     * after `fetchRows` the list.
     *
     * Whether the step that uses it can then **bind** it is a separate question, and the answer is whatever
     * the driver can send: a scalar goes, a list of scalars goes as an array, and a `Row`, a `List<Row>` or a
     * class the type registry has never heard of does not - it is refused where the parameter is encoded,
     * naming the class. [map] is the way across, taking the part that can be sent; [field] reaches into a
     * row-shaped result without coming through here at all.
     */
    fun value(): TransactionValue<T> {
        @Suppress("UNCHECKED_CAST")
        return TransactionValue.FromStep.Whole(this) as TransactionValue<T>
    }

    /**
     * One column of one row of the step's result, which has to be row-shaped.
     *
     * @param columnName The column to take.
     * @param rowIndex Which row, counted from zero.
     */
    fun field(columnName: String, rowIndex: Int = 0): TransactionValue<Any?> =
        TransactionValue.FromStep.Field(this, columnName, rowIndex)

    /**
     * One column of every row of the step's result, as a list.
     *
     * @param columnName The column to take.
     */
    fun column(columnName: String): TransactionValue<List<Any?>> =
        TransactionValue.FromStep.Column(this, columnName)

    /**
     * A whole row of the step's result, as a map - **spread** into parameters of its own when used as one.
     *
     * Spread means its entries become parameters under their own column names and the name this value was
     * filed under is thrown away: `"anything" to handle.row()` binds `@id`, `@name` and whatever else the row
     * has, and binds no `@anything` at all. That name is a placeholder, there because a map of parameters
     * needs a key, and it is the one parameter name in a step that means nothing. Wrapping this in [map] takes
     * the spread away and gives the name its meaning back - see there.
     *
     * @param rowIndex Which row, counted from zero.
     */
    fun row(rowIndex: Int = 0): TransactionValue<Map<String, Any?>> =
        TransactionValue.FromStep.Row(this, rowIndex)

    override fun toString(): String = "StepHandle(step $position)"
}
