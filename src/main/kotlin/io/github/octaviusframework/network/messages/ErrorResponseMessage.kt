package io.github.octaviusframework.network.messages

/**
 * Odpowiedź błędu od serwera (Tag 'E').
 */
class ErrorResponseMessage(val fields: Map<Char, String>) : BackendMessage {
    val message: String? get() = fields['M']
    val severity: String? get() = fields['S']
    val code: String? get() = fields['C']
    
    override fun toString(): String {
        return "ErrorResponse(severity=$severity, code=$code, message=$message)"
    }
}
