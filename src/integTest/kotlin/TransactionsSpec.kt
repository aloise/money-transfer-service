import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.TestApplicationEngine
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.setBody
import io.ktor.server.testing.withTestApplication
import kotlinx.serialization.Serializable
import kotlinx.serialization.UnstableDefault
import kotlinx.serialization.json.Json
import name.aloise.module
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@Serializable
data class CreateTransactionResponse(val id: Int,
                                     val fromAccountId: Int,
                                     val toAccountId: Int,
                                     val centAmount: Int,
                                     val status: String)

@UnstableDefault
class TransactionsSpec {

    private fun TestApplicationEngine.createAccount(name: String, centAmount: Int): Int? {
        var accountId: Int?
        handleRequest(HttpMethod.Post, "/accounts") {
            addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody("""{"name" : "$name", "centAmount": $centAmount}""")
        }.apply {
            val content = assertNotNull(response.content)
            val accountCreated = Json.parse(AccountResponse.serializer(), content)
            accountId = accountCreated.id
        }
        return accountId
    }

    @Test
    fun createTransaction() {
        withTestApplication({ module() }) {
            val accountId1 = createAccount("Test1", 1000)
            val accountId2 = createAccount("Test2", 2000)
            var trxId: Int?

            assertNotNull(accountId1)
            assertNotNull(accountId2)

            handleRequest(HttpMethod.Post, "/transactions") {
                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                setBody("""{"fromAccountId": $accountId1, "toAccountId": $accountId2, "centAmount": 500}""")
            }.apply {
                assertEquals(HttpStatusCode.Created, response.status())
                val content = assertNotNull(response.content)
                val trx = Json.parse(CreateTransactionResponse.serializer(), content)
                assertEquals("SUCCESS", trx.status)
                assertEquals(500, trx.centAmount)
                trxId = trx.id
            }

            handleRequest(HttpMethod.Get, "/transactions/$trxId").apply {
                assertEquals(HttpStatusCode.OK, response.status())
                assertNotNull(response.content)
            }

            handleRequest(HttpMethod.Get, "/accounts/$accountId1").apply {
                val acc = Json.parse(AccountResponse.serializer(), assertNotNull(response.content))
                assertEquals(500, acc.centAmount)
            }

            handleRequest(HttpMethod.Get, "/accounts/$accountId2").apply {
                val acc = Json.parse(AccountResponse.serializer(), assertNotNull(response.content))
                assertEquals(2500, acc.centAmount)
            }
        }
    }

}