package de.kitshn.api.tandoor

import coil3.network.NetworkClient
import coil3.network.NetworkRequest
import coil3.network.NetworkResponse
import coil3.network.ktor3.asNetworkClient

internal class TandoorImageNetworkClient(private val client: TandoorClient) : NetworkClient {
    override suspend fun <T> executeRequest(
        request: NetworkRequest,
        block: suspend (response: NetworkResponse) -> T,
    ): T {
        // Coil caches its NetworkClient, so resolve the current HTTP client per request.
        return client.httpClient.asNetworkClient().executeRequest(request, block)
    }
}
