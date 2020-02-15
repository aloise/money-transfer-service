package name.aloise.route

import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.response.respond
import io.ktor.routing.*
import name.aloise.service.AccountService
import name.aloise.utils.generic.fold
import name.aloise.utils.http.decodeOrNull

data class AccountCreateRequest(val name: String)
data class ErrorResponse(val error: String)

fun Route.accounts(accountService: AccountService) {
    route("/accounts") {
        get("/{id}") {
            call.parameters["id"]?.toIntOrNull()?.let { accountService.get(it) }.fold({ account ->
                call.respond(account)
            }, {
                call.respond(HttpStatusCode.NotFound)
            })
        }

        post("/") {
            call.decodeOrNull<AccountCreateRequest>().fold({ acc ->
                val accountCreated = accountService.create(AccountService.CreateAccount(acc.name))
                call.respond(HttpStatusCode.Created, accountCreated)
            }, {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("Can't decode the Account json"))
            })
        }

        delete("/{id}") {
            val isDeleted = call.parameters["id"]?.toIntOrNull()?.let { accountService.remove(it) } ?: false
            call.respond(if (isDeleted) HttpStatusCode.NoContent else HttpStatusCode.NotFound)
        }
    }
}


