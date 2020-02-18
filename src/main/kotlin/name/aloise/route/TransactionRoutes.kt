package name.aloise.route

import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.get
import io.ktor.routing.post
import io.ktor.routing.route
import name.aloise.service.Transaction
import name.aloise.service.TransactionService
import name.aloise.utils.generic.fold
import name.aloise.utils.http.*

data class TransactionCreateRequest(val fromAccountId: Int, val toAccountId: Int, val centAmount: Int) : Validation {
    override suspend fun errors(): ValidationErrors =
            listOf(
                    if (centAmount <= 0) ValidationError("invalid_amount", "Negative Amount") else null
            ).mapNotNull { it }
}

data class TransactionListResponse(val results: List<Transaction>, val count: Int)

fun Route.transactions(transactionService: TransactionService) {
    route("/transactions") {
        get("/{id}") {
            call.parameters["id"]?.toIntOrNull()?.let { transactionService.get(it) }.fold({ trx ->
                call.respond(trx)
            }, {
                call.respond(HttpStatusCode.NotFound)
            })
        }

        get("/") {
            val transactions = transactionService.list()
            call.respond(TransactionListResponse(transactions, transactions.size))
        }

        post("/") {
            when (val validation = call.receiveAndValidate<TransactionCreateRequest> { it.errors() }) {
                is Valid -> {
                    val req = validation.result
                    val trx = transactionService.create(req.fromAccountId, req.toAccountId, req.centAmount)
                    call.respond(HttpStatusCode.Created, trx)
                }
                is ValidationFailed ->
                    call.respond(HttpStatusCode.BadRequest, ValidationErrorResponse(validation.errors))
                is ReceiveFailed ->
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("Can't decode the Transaction json"))
            }
        }
    }
}