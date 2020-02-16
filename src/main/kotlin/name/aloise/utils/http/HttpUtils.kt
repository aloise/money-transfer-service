package name.aloise.utils.http

import io.ktor.application.ApplicationCall
import io.ktor.request.receive
import kotlin.reflect.typeOf

data class ErrorResponse(val message: String)
data class ValidationErrorResponse(val errors: ValidationErrors)

data class ValidationError(val name: String, val message: String)
typealias ValidationErrors = List<ValidationError>

interface Validation {
    suspend fun errors(): ValidationErrors
}

object ValidationErrorsDefault {
    val empty: ValidationErrors = listOf()
}

sealed class RequestValidationResult<out T>
data class Valid<T>(val result: T) : RequestValidationResult<T>()
data class ReceiveFailed(val cause: Exception) : RequestValidationResult<Nothing>()
data class ValidationFailed(val errors: ValidationErrors) : RequestValidationResult<Nothing>()

suspend inline fun <reified T : Any> ApplicationCall.receiveAndValidate(
    errors: (T) -> ValidationErrors = { _ -> ValidationErrorsDefault.empty }
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
