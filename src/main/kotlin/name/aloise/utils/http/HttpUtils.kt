package name.aloise.utils.http

import io.ktor.application.ApplicationCall
import io.ktor.request.receive
import kotlin.reflect.typeOf

data class ErrorResponse(val message: String)
data class ValidationErrorResponse(val errors: List<ValidationError>)

data class ValidationError(val name: String, val message: String)

interface Validation {
    fun errors(): List<ValidationError>
}

object ValidationErrors {
    val empty = listOf<ValidationError>()
}

sealed class RequestValidationResult<out T>
data class Valid<T>(val result: T) : RequestValidationResult<T>()
data class ReceiveFailed(val cause: Exception) : RequestValidationResult<Nothing>()
data class ValidationFailed(val errors: List<ValidationError>) : RequestValidationResult<Nothing>()

suspend inline fun <reified T : Any> ApplicationCall.receiveAndValidate(
    errors: (T) -> List<ValidationError> = { _ -> ValidationErrors.empty }
): RequestValidationResult<T> =
    try {
        val received: T = receive(typeOf<T>())
        try {
            val validationErrors = errors(received)
            if (validationErrors.isEmpty()) {
                Valid(received)
            } else {
                ValidationFailed(validationErrors)
            }
        } catch (cause: Exception) {
            ReceiveFailed(cause)
        }
    } catch (cause: Exception) {
        ReceiveFailed(cause)
    }
