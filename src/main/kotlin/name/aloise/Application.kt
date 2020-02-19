package name.aloise

import com.fasterxml.jackson.databind.SerializationFeature
import io.ktor.application.Application
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.features.ContentNegotiation
import io.ktor.jackson.jackson
import io.ktor.response.respondText
import io.ktor.routing.Routing
import io.ktor.routing.get
import io.ktor.routing.route
import io.ktor.server.engine.commandLineEnvironment
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import name.aloise.route.accounts
import name.aloise.route.transactions
import name.aloise.service.AccountService
import name.aloise.service.InMemoryAccountService
import name.aloise.service.InMemoryTransactionService
import name.aloise.service.TransactionService

fun main(args: Array<String>) {
    embeddedServer(Netty, commandLineEnvironment(args)).start(wait = true)
}

fun Application.module() {
    val accountService: AccountService = InMemoryAccountService()
    val transactionService: TransactionService = InMemoryTransactionService(accountService)

    install(ContentNegotiation) {
        jackson {
            enable(SerializationFeature.INDENT_OUTPUT)
        }
    }

    install(Routing) {
        accounts(accountService)
        transactions(transactionService)
        route("/") {
            get("/health") {
                call.respondText("OK")
            }
        }
    }
}
