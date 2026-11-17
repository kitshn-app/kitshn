package de.kitshn.api.tandoor

import coil3.network.NetworkClient
import coil3.network.NetworkRequest
import coil3.network.NetworkResponse
import coil3.network.ktor3.asNetworkClient

/** Keeps existing image loaders usable when timeout changes replace the HTTP clients. */
internal class TandoorImageNetworkClient(private val client: TandoorClient) : NetworkClient {
    override suspend fun <T> executeRequest(
        request: NetworkRequest,
        block: suspend (response: NetworkResponse) -> T,
    ): T {
        // Coil caches its NetworkClient, so resolve the current HTTP client per request,
        // not just lazily when the image loader first starts fetching images.
        return client.httpClient.asNetworkClient().executeRequest(request, block)
    }
}
