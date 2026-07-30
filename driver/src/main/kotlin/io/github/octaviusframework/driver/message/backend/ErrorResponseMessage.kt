package io.github.octaviusframework.driver.message.backend

/**
 * Error response from server (Tag 'E').
 */
class ErrorResponseMessage(val fields: Map<Char, String>) : BackendMessage {

    companion object {
        private const val SEVERITY: Char = 'S'
        private const val MESSAGE: Char = 'M'
        private const val DETAIL: Char = 'D'
        private const val HINT: Char = 'H'
        private const val POSITION: Char = 'P'
        private const val WHERE: Char = 'W'
        private const val FILE: Char = 'F'
        private const val LINE: Char = 'L'
        private const val ROUTINE: Char = 'R'
        private const val SQLSTATE: Char = 'C'
        private const val INTERNAL_POSITION: Char = 'p'
        private const val INTERNAL_QUERY: Char = 'q'
        private const val SCHEMA: Char = 's'
        private const val TABLE: Char = 't'
        private const val COLUMN: Char = 'c'
        private const val DATATYPE: Char = 'd'
        private const val CONSTRAINT: Char = 'n'
    }


    val message: String? get() = fields[MESSAGE]
    val severity: String? get() = fields[SEVERITY]
    val code: String? get() = fields[SQLSTATE]
    val position: Int? get() = fields[POSITION]?.toIntOrNull()
    val schema: String? get() = fields[SCHEMA]
    val table: String? get() = fields[TABLE]
    val column: String? get() = fields[COLUMN]
    val constraint: String? get() = fields[CONSTRAINT]
    val datatype: String? get() = fields[DATATYPE]
    val routine: String? get() = fields[ROUTINE]

    override fun toString(): String {
        return "ErrorResponse(severity=$severity, code=$code, message=$message, schema=$schema, table=$table, column=$column, constraint=$constraint)"
    }
}