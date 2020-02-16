package integration

import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.withTestApplication
import name.aloise.module
import kotlin.test.Test
import kotlin.test.assertEquals

class HealthSpec {
    @Test
    fun testHealth() {
        withTestApplication({ module() }) {
            handleRequest(HttpMethod.Get, "/health").apply {
                assertEquals(HttpStatusCode.OK, response.status())
                assertEquals("OK", response.content)
            }
        }
    }
}