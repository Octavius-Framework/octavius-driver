package io.github.octaviusframework.auth

import io.github.octaviusframework.network.PgStream
import io.github.octaviusframework.network.messages.*
import java.nio.charset.StandardCharsets
import io.github.octaviusframework.exceptions.OctaviusAuthException
import io.github.octaviusframework.exceptions.AuthExceptionMessage

class Authenticator(private val stream: PgStream) {

    fun authenticate(user: String, password: String?) {
        while (true) {
            val msg = stream.receiveMessage()

            when (msg) {
                is AuthenticationMessage.Ok -> {
                    println("Autentykacja udana!")
                    // Pętla będzie kontynuowana żeby zjeść ParameterStatus, aż trafimy na ReadyForQuery
                }
                is AuthenticationMessage.SASL -> {
                    val mechs = msg.mechanisms
                    if (!mechs.contains("SCRAM-SHA-256")) {
                        throw OctaviusAuthException(AuthExceptionMessage.UNSUPPORTED_MECHANISM, details = "Wspierane: $mechs")
                    }

                    val clientNonce = ScramSha256Authenticator.generateClientNonce()
                    val clientFirstMessageBare = "n=,r=$clientNonce"
                    val clientFirstMessage = "n,,$clientFirstMessageBare"

                    stream.sendMessage(SASLInitialResponse("SCRAM-SHA-256", clientFirstMessage))
                    stream.flush()

                    // Czekamy na SASLContinue
                    val continueMsg = stream.receiveMessage()
                    if (continueMsg !is AuthenticationMessage.SASLContinue) {
                        throw OctaviusAuthException(AuthExceptionMessage.PROTOCOL_VIOLATION, details = "Oczekiwano SASLContinue, a dostano: $continueMsg")
                    }

                    val serverFirstMessage = String(continueMsg.data, StandardCharsets.UTF_8)
                    
                    // Parsowanie serverFirstMessage (r=..., s=..., i=...)
                    val parts = serverFirstMessage.split(",")
                    val params = parts.associate { it.substring(0, 1) to it.substring(2) }
                    
                    val serverNonce = params["r"] ?: throw OctaviusAuthException(AuthExceptionMessage.MISSING_PROTOCOL_PARAMETER, details = "Brak r w serverFirstMessage")
                    val saltB64 = params["s"] ?: throw OctaviusAuthException(AuthExceptionMessage.MISSING_PROTOCOL_PARAMETER, details = "Brak s w serverFirstMessage")
                    val iterationsStr = params["i"] ?: throw OctaviusAuthException(AuthExceptionMessage.MISSING_PROTOCOL_PARAMETER, details = "Brak i w serverFirstMessage")
                    
                    val salt = java.util.Base64.getDecoder().decode(saltB64)
                    val iterations = iterationsStr.toInt()

                    val clientFinalMessageWithoutProof = "c=biws,r=$serverNonce"
                    
                    val proof = ScramSha256Authenticator.computeClientProof(
                        password ?: "", 
                        salt, 
                        iterations, 
                        clientFirstMessageBare, 
                        serverFirstMessage, 
                        clientFinalMessageWithoutProof
                    )

                    val clientFinalMessage = "$clientFinalMessageWithoutProof,p=$proof"
                    stream.sendMessage(SASLResponse(clientFinalMessage))
                    stream.flush()

                    // Następnie serwer powinien przysłać SASLFinal
                    val finalMsg = stream.receiveMessage()
                    if (finalMsg is ErrorResponseMessage) {
                        throw OctaviusAuthException(AuthExceptionMessage.SERVER_REJECTED_CREDENTIALS, details = finalMsg.message)
                    }
                    if (finalMsg !is AuthenticationMessage.SASLFinal) {
                        throw OctaviusAuthException(AuthExceptionMessage.PROTOCOL_VIOLATION, details = "Oczekiwano SASLFinal, dostano: $finalMsg")
                    }
                    // W teorii można zweryfikować podpis serwera (v=...), ale dla uproszczenia przechodzimy dalej
                }
                is AuthenticationMessage.CleartextPassword -> {
                    throw OctaviusAuthException(AuthExceptionMessage.UNSUPPORTED_PASSWORD_ENCRYPTION, details = "Serwer zażądał CleartextPassword, obsługujemy tylko SCRAM")
                }
                is AuthenticationMessage.MD5Password -> {
                    throw OctaviusAuthException(AuthExceptionMessage.UNSUPPORTED_PASSWORD_ENCRYPTION, details = "Serwer zażądał MD5Password, obsługujemy tylko SCRAM")
                }
                is ErrorResponseMessage -> {
                    throw OctaviusAuthException(AuthExceptionMessage.SERVER_REJECTED_CREDENTIALS, details = "Błąd od serwera podczas łączenia: ${msg.message}")
                }
                is BackendKeyDataMessage -> {
                    println("Otrzymano klucze procesu: ${msg.processId}")
                }
                is ReadyForQueryMessage -> {
                    println("Zalogowano pomyślnie! Serwer gotowy do zapytań.")
                    return // Koniec fazy logowania
                }
                is ParameterStatusMessage -> {
                    println("Otrzymano parametr sesji: ${msg.name} = ${msg.value}")
                }
                else -> {
                    println("Ignoruję niespodziewaną wiadomość: $msg")
                }
            }
        }
    }
}
