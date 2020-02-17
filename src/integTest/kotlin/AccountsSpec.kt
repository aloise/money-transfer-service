import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.setBody
import io.ktor.server.testing.withTestApplication
import kotlinx.serialization.Serializable
import kotlinx.serialization.UnstableDefault
import kotlinx.serialization.json.Json
import name.aloise.module
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@Serializable
data class AccountResponse(val id: Int, val name: String, val centAmount: Int)

@UnstableDefault
class AccountsSpec {

    @Test
    fun testCreateAccount() {
        withTestApplication({ module() }) {
            var accountId: Int?
            val testName = "Test" + Random.Default.nextInt()
            val centAmount: Int = Random.Default.nextInt(0, 100000)
            handleRequest(HttpMethod.Post, "/accounts") {
                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                setBody("""{"name" : "$testName", "centAmount": $centAmount}""")
            }.apply {
                assertEquals(HttpStatusCode.Created, response.status())
                val content = assertNotNull(response.content)

                val accountCreated = Json.parse(AccountResponse.serializer(), content)
                accountId = accountCreated.id
                assertEquals(testName, accountCreated.name)
                assertEquals(centAmount, accountCreated.centAmount)
            }

            handleRequest(HttpMethod.Get, "/accounts/$accountId").apply {
                assertEquals(HttpStatusCode.OK, response.status())
                val content = assertNotNull(response.content)

                val account = Json.parse(AccountResponse.serializer(), content)

                assertEquals(account.id, accountId)
                assertEquals(account.name, testName)
            }
        }
    }

    @Test
    fun testFailToCreateInvalidAccountEmptyName() {
        withTestApplication({ module() }) {
            handleRequest(HttpMethod.Post, "/accounts") {
                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                // Empty account name should be accepted
                setBody("""{"name" : ""}""")
            }.apply {
                assertEquals(HttpStatusCode.BadRequest, response.status())
                assertNotNull(response.content)
                // TODO - Error response validation goes here
            }
        }
    }

    @Test
    fun testFailToCreateInvalidAccountNegativeAmount() {
        withTestApplication({ module() }) {
            val testName = "Test" + Random.Default.nextInt()
            handleRequest(HttpMethod.Post, "/accounts") {
                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                // Empty account name should be accepted
                setBody("""{"name" : "$testName", "centAmount": -100}""")
            }.apply {
                assertEquals(HttpStatusCode.BadRequest, response.status())
                assertNotNull(response.content)
                // TODO - Error response validation goes here
            }
        }
    }


    @Test
    fun testDeleteAccount() {
        withTestApplication({ module() }) {
            var accountId: Int?
            val testName = "Test" + Random.Default.nextInt()
            handleRequest(HttpMethod.Post, "/accounts") {
                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                setBody("""{"name" : "$testName"}""")
            }.apply {
                assertEquals(HttpStatusCode.Created, response.status())
                val content = assertNotNull(response.content)

                val accountCreated = Json.parse(AccountResponse.serializer(), content)
                accountId = accountCreated.id
                assertEquals(testName, accountCreated.name)
            }

            handleRequest(HttpMethod.Delete, "/accounts/$accountId").apply {
                assertEquals(HttpStatusCode.NoContent, response.status())
            }

            handleRequest(HttpMethod.Get, "/accounts/$accountId").apply {
                assertEquals(HttpStatusCode.NotFound, response.status())
            }
        }
    }
}
