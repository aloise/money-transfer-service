package name.aloise.utils.http

import io.ktor.application.ApplicationCall
import io.ktor.request.receive
import kotlin.reflect.typeOf

suspend inline fun <reified T : Any> ApplicationCall.decodeOrNull(): T? =
    try {
        receive(typeOf<T>())
    } catch (cause: Exception) {
        null
    }