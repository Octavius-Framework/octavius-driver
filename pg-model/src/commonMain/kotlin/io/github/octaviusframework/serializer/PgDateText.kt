package io.github.octaviusframework.serializer

/**
 * Translates the year between the way ISO-8601 writes it and the way PostgreSQL does.
 *
 * The two disagree outside `0001`..`9999`, and they disagree in a way no single string satisfies:
 *
 * | Year        | ISO-8601 / kotlinx | PostgreSQL      |
 * |-------------|--------------------|-----------------|
 * | 2024        | `2024-01-02`       | the same        |
 * | 10000       | `+10000-01-02`     | `10000-01-02`   |
 * | 1 BC        | `0000-01-02`       | `0001-01-02 BC` |
 * | 2 BC        | `-0001-01-02`      | `0002-01-02 BC` |
 *
 * ISO requires the sign on any year past four digits and PostgreSQL refuses it - it reads the sign as the
 * start of a timezone offset, so `+10000-01-02` and `-0001-01-02` are both rejected outright, and so is
 * `4713-01-02 BC`'s ISO spelling even though that is PostgreSQL's own minimum. ISO also has a year zero where
 * PostgreSQL has none, which is where the off-by-one comes from: 1 BC directly precedes 1 AD.
 *
 * So a payload written in ISO cannot be cast back to a `date` at all past year 9999 or before year 1. This
 * writes PostgreSQL's form instead, and reads **either** back - a payload built in SQL, or one written before
 * this existed, still decodes.
 *
 * None of this widens what a column holds: `date` reaches 4713 BC to 5874897 AD and `timestamp` 294276 AD,
 * and a value outside that is out of range whichever way it is spelled.
 */
internal object PgDateText {

    private const val BC = " BC"

    /**
     * Rewrites the leading year of an ISO-8601 date or timestamp into PostgreSQL's spelling.
     *
     * Everything after the year - the month and day, any time, any offset - is left exactly as it stood, and
     * `BC` goes at the very end, after an offset if there is one, which is where PostgreSQL wants it.
     *
     * @param iso The value as kotlinx.datetime rendered it.
     * @return The same instant in PostgreSQL's text form.
     */
    fun fromIso(iso: String): String {
        val signed = iso.startsWith('-') || iso.startsWith('+')
        val negative = iso.startsWith('-')
        val digitsFrom = if (signed) 1 else 0

        var end = digitsFrom
        while (end < iso.length && iso[end].isDigit()) end++
        if (end == digitsFrom) return iso

        val year = iso.substring(digitsFrom, end).toIntOrNull() ?: return iso
        val rest = iso.substring(end)

        return when {
            // ISO counts backwards through a year zero; PostgreSQL counts BC from one.
            negative -> "${(year + 1).toString().padStart(4, '0')}$rest$BC"
            year == 0 -> "0001$rest$BC"
            signed -> "$year$rest"
            else -> iso
        }
    }

    /**
     * Rewrites a year in PostgreSQL's spelling back into the one kotlinx.datetime parses.
     *
     * Lenient on purpose: a value already written the ISO way - by SQL, or by a version of this library that
     * predates the translation - is returned untouched rather than refused.
     *
     * @param text The value as it stood in the payload.
     * @return The same instant in ISO-8601.
     */
    fun toIso(text: String): String {
        val bc = text.length > BC.length &&
            text.regionMatches(text.length - BC.length, BC, 0, BC.length, ignoreCase = true)
        val body = if (bc) text.substring(0, text.length - BC.length) else text

        // A sign is ISO's, not PostgreSQL's, so there is nothing here to undo.
        if (body.startsWith('-') || body.startsWith('+')) return body

        var end = 0
        while (end < body.length && body[end].isDigit()) end++
        if (end == 0) return body

        val year = body.substring(0, end).toIntOrNull() ?: return body
        val rest = body.substring(end)

        return when {
            bc -> {
                val iso = 1 - year
                if (iso == 0) "0000$rest" else "-${(-iso).toString().padStart(4, '0')}$rest"
            }
            // ISO writes a sign on everything past four digits, and refuses to parse it back without one.
            end > 4 -> "+$body"
            else -> body
        }
    }
}
