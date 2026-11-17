package de.kitshn.api.tandoor

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import java.net.InetSocketAddress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TandoorErrorMessageTest {
    @Test
    fun extractsActionableTandoorMessages() {
        assertEquals(
            "You must select an AI provider to perform your request.",
            extractTandoorErrorMessage(
                """{"error":true,"msg":"You must select an AI provider to perform your request."}"""
            )
        )
        assertEquals(
            "Ungültige Anmeldedaten.",
            extractTandoorErrorMessage("""{"detail":"Ungültige Anmeldedaten."}""")
        )
        assertEquals(
            "This field is required.",
            extractTandoorErrorMessage("""{"name":["This field is required."]}""")
        )
        assertEquals("Server unavailable", extractTandoorErrorMessage("Server unavailable"))
        assertNull(extractTandoorErrorMessage(""))
        assertNull(extractTandoorErrorMessage("<html><body>Bad gateway</body></html>"))
    }

    @Test
    fun responseBodyIsIncludedInThrownError() = runBlocking {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/api/failure") { exchange ->
            val bytes = """{"detail":"AI model returned no usable content"}""".encodeToByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(400, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.createContext("/api/ai-import/") { exchange ->
            val bytes = """{"error":true,"msg":"Error parsing AI results."}""".encodeToByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(400, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        val client = TandoorClient(TandoorCredentials("http://127.0.0.1:${server.address.port}"))

        try {
            val requestError = assertFailsWith<TandoorRequestsError> {
                client.getObject("/failure")
            }
            assertTrue(requestError.message.contains("AI model returned no usable content"))
            assertTrue(requestError.message.contains("400"))

            val importError = assertFailsWith<TandoorRequestsError> {
                client.postMultipart("/ai-import/") {
                    append("text", "test")
                }
            }
            assertTrue(importError.message.contains("Error parsing AI results."))
            assertFalse(importError.message.contains("400 null"))
        } finally {
            client.httpClient.close()
            client.longHttpClient.close()
            server.stop(0)
        }
    }
}
