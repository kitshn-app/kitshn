package de.kitshn.api.tandoor

import coil3.network.NetworkHeaders
import coil3.network.NetworkRequest
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okio.Buffer
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class TandoorImageNetworkClientTest {
    @Test
    fun imageRequestsSurviveTimeoutChanges() = runBlocking {
        val requests = AtomicInteger()
        val bytes = byteArrayOf(1, 2, 3, 4)
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/image") { exchange ->
            requests.incrementAndGet()
            val authorized = exchange.requestHeaders.getFirst("Authorization") == "Bearer test-token"
            exchange.sendResponseHeaders(if (authorized) 200 else 401, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        val url = "http://127.0.0.1:${server.address.port}"
        val client = TandoorClient(TandoorCredentials(url))
        val networkClient = TandoorImageNetworkClient(client)
        try {
            val timeouts = listOf(
                TandoorTimeoutSettings(),
                TandoorTimeoutSettings(shortTimeout = 10000L, longTimeout = 300000L),
                TandoorTimeoutSettings(shortTimeout = 20000L, longTimeout = 300000L),
                TandoorTimeoutSettings()
            )
            for ((index, timeout) in timeouts.withIndex()) {
                client.configureTimeouts(timeout)
                val request = NetworkRequest(
                    url = "$url/image",
                    headers = NetworkHeaders.Builder().set("Authorization", "Bearer test-token").build()
                )
                withTimeout(10000L) {
                    networkClient.executeRequest(request) { response ->
                        assertEquals(200, response.code, "Image request failed with $timeout")
                        val buffer = Buffer()
                        assertNotNull(response.body).writeTo(buffer)
                        assertContentEquals(bytes, buffer.readByteArray())
                    }
                }
                assertEquals(index + 1, requests.get())
                assertEquals(timeout, client.timeoutSettings)
            }
        } finally {
            client.httpClient.close()
            client.longHttpClient.close()
            server.stop(0)
        }
    }
}
