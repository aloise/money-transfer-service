package name.aloise.route

import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.response.respond
import io.ktor.routing.*
import name.aloise.service.AccountService
import name.aloise.service.CreateAccount
import name.aloise.utils.generic.fold
import name.aloise.utils.http.*

data class AccountData(val id: Int, val name: String, val centAmount: Int)
data class AccountCreateRequest(val name: String, val centAmount: Int) : Validation {
    override suspend fun errors(): ValidationErrors =
        listOf(
            if (this.name.trim().isEmpty()) ValidationError("empty_name", "Empty Name") else null,
            if (this.centAmount < 0) ValidationError("negative_amount", "Negative Amount") else null
        ).mapNotNull { it }
}

fun Route.accounts(accountService: AccountService) {
    route("/accounts") {
        get("/{id}") {
            call.parameters["id"]?.toIntOrNull()?.let { accountService.get(it) }.fold({ account ->
                call.respond(AccountData(account.account.id, account.account.name, account.balance.centAmount))
            }, {
                call.respond(HttpStatusCode.NotFound)
            })
        }

        post("/") {
            when (val validation = call.receiveAndValidate<AccountCreateRequest> { it.errors() }) {
                is Valid -> {
                    val req = validation.result
                    val account = accountService.create(CreateAccount(req.name, req.centAmount))
                    call.respond(
                        HttpStatusCode.Created,
                            AccountData(account.account.id, account.account.name, account.balance.centAmount)
                    )
                }
                is ValidationFailed ->
                    call.respond(HttpStatusCode.BadRequest, ValidationErrorResponse(validation.errors))
                is ReceiveFailed ->
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("Can't decode the Account json"))
            }
        }

        delete("/{id}") {
            val isDeleted = call.parameters["id"]?.toIntOrNull()?.let { accountService.remove(it) } ?: false
            call.respond(if (isDeleted) HttpStatusCode.NoContent else HttpStatusCode.NotFound)
        }
    }
}


