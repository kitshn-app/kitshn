package de.kitshn.api.tandoor

import co.touchlab.kermit.Logger
import de.kitshn.json
import de.kitshn.redactForRelease
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.forms.FormBuilder
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.headers
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.readRawBytes
import io.ktor.client.statement.request
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

class TandoorRequestsError(
    val throwable: Throwable?,
    val response: HttpResponse?,
    private val additionalText: String = "",
    val overrideMessage: String? = null
) : Exception(throwable) {
    override val message: String
        get() = overrideMessage ?: ((
                throwable?.message
                    ?: (response?.request?.url.toString() + " | " + response?.request?.method.toString() + " | " + response?.status.toString())
                )
                + additionalText + throwable?.stackTraceToString())

    /** Server answered with 4xx/5xx — reachable, but refused. */
    val isServerRefused: Boolean get() = response != null

    /** No response at all — transport failure (DNS, connect, timeout, TLS, …). */
    val isNetworkFailure: Boolean get() = response == null
}

private const val MAX_ERROR_BODY_LENGTH = 4_000

/**
 * Tandoor returns actionable API errors in the response body (for example
 * `{"detail": "..."}` or `{"error": true, "msg": "..."}`). Surface that text
 * instead of reducing every 4xx/5xx response to its HTTP status code.
 */
internal fun extractTandoorErrorMessage(body: String): String? {
    val trimmed = body.trim()
    if(trimmed.isEmpty()) return null

    val messages = try {
        val element = json.parseToJsonElement(trimmed)
        val preferred = listOf("msg", "detail", "non_field_errors")
        val preferredMessages = preferred.flatMap { key ->
            (element as? JsonObject)?.get(key)?.let(::collectErrorStrings).orEmpty()
        }
        (preferredMessages + collectErrorStrings(element).filterNot { it in preferredMessages })
            .distinct()
    } catch(_: Exception) {
        // Plain-text error pages are useful; HTML is not.
        if(trimmed.startsWith("<")) return null
        listOf(trimmed)
    }

    return messages
        .joinToString("\n\n")
        .take(MAX_ERROR_BODY_LENGTH)
        .ifBlank { null }
}

private fun collectErrorStrings(element: JsonElement): List<String> = when(element) {
    is JsonPrimitive -> if(element.isString && element.content.isNotBlank()) {
        listOf(element.content)
    } else {
        emptyList()
    }
    is JsonArray -> element.flatMap(::collectErrorStrings)
    is JsonObject -> element.entries.flatMap { (key, value) ->
        if(key == "error" && value is JsonPrimitive && !value.isString) {
            emptyList()
        } else {
            collectErrorStrings(value)
        }
    }
}

private suspend fun HttpResponse.errorMessageOrNull(): String? = try {
    extractTandoorErrorMessage(bodyAsText())
} catch(_: Exception) {
    null
}

suspend fun TandoorClient.reqAny(
    endpoint: String,
    _method: HttpMethod,
    data: Any? = null,
    contentType: ContentType? = null,
    custom: HttpRequestBuilder.() -> Unit = { },
    customHttpClient: HttpClient? = null
): HttpResponse {
    try {
        val token = this.credentials.token
        val cookie = this.credentials.cookie

        Logger.d("TandoorRequests") { "Method: $_method, URL: ${credentials.instanceUrl.redactForRelease()}/api${endpoint}" }

        val url = if(endpoint.startsWith("@")) {
            "${credentials.instanceUrl}/${endpoint.substring(1)}"
        } else {
            "${credentials.instanceUrl}/api${endpoint}"
        }

        val response = (customHttpClient ?: httpClient).request {
            url(url)
            method = _method
            headers {
                if(token != null) set("Authorization", "Bearer ${token.token}")

                if(cookie != null) {
                    val csrfToken =
                        cookie.replace(Regex(".*csrftoken="), "").replace(Regex(";.*"), "")
                    set("X-CSRFTOKEN", csrfToken)
                    set("Cookie", cookie)
                }

                credentials.customHeaders.forEach {
                    set(it.field, it.value)
                }

                set("Referer", credentials.instanceUrl)
            }
            if(data != null && contentType != null) {
                setBody(data.toString())
                contentType(contentType)
            }

            custom()
        }

        // 4xx/5xx still means Tandoor answered — server reachable, just refused.
        reportCallSuccess()
        if(!response.status.isSuccess()) {
            val serverMessage = response.errorMessageOrNull()
            throw TandoorRequestsError(
                null,
                response,
                additionalText = serverMessage?.let { "\n\n$it" } ?: ""
            )
        }

        return response
    } catch(e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch(e: Exception) {
        if(e is TandoorRequestsError) {
            throw e
        } else {
            Logger.w("TandoorRequests.kt") { "Transport failure: ${e::class.simpleName}: ${e.message}" }
            reportCallNetworkFailure()
            throw TandoorRequestsError(e, null)
        }
    }
}

suspend fun TandoorClient.req(
    endpoint: String,
    _method: HttpMethod,
    data: JsonObject? = null
): HttpResponse {
    return reqAny(endpoint, _method, data?.toString(), ContentType.Application.Json)
}

suspend fun TandoorClient.reqArray(
    endpoint: String,
    method: HttpMethod,
    data: JsonArray? = null
): JsonArray {
    return json.decodeFromString(
        reqAny(
            endpoint,
            method,
            data?.toString(),
            ContentType.Application.Json
        ).readRawBytes().decodeToString()
    )
}

suspend fun TandoorClient.reqObject(
    endpoint: String,
    method: HttpMethod,
    data: JsonObject? = null
): JsonObject {
    return json.decodeFromString(
        reqAny(
            endpoint,
            method,
            data?.toString(),
            ContentType.Application.Json
        ).readRawBytes().decodeToString()
    )
}

suspend fun TandoorClient.reqByteArray(
    endpoint: String,
    method: HttpMethod,
    data: JsonObject? = null
): ByteArray {
    return reqAny(endpoint, method, data?.toString(), ContentType.Application.Json).readRawBytes()
}

suspend fun TandoorClient.reqMultipart(
    endpoint: String,
    _method: HttpMethod,
    _formData: FormBuilder.() -> Unit
): HttpResponse {
    try {
        val token = this.credentials.token
        val cookie = this.credentials.cookie

        Logger.d("TandoorRequests") { "Method: $_method, URL: ${credentials.instanceUrl.redactForRelease()}/api${endpoint}" }

        val url = if(endpoint.startsWith("@")) {
            "${credentials.instanceUrl}/${endpoint.substring(1)}"
        } else {
            "${credentials.instanceUrl}/api${endpoint}"
        }

        val response = longHttpClient.request {
            url(url)
            method = _method
            headers {
                if(token != null) set("Authorization", "Bearer ${token.token}")

                if(cookie != null) {
                    val csrfToken =
                        cookie.replace(Regex(".*csrftoken="), "").replace(Regex(";.*"), "")
                    set("X-CSRFTOKEN", csrfToken)
                    set("Cookie", cookie)
                }

                credentials.customHeaders.forEach {
                    set(it.field, it.value)
                }

                set("Referer", credentials.instanceUrl)
            }

            setBody(
                MultiPartFormDataContent(
                    formData(_formData)
                )
            )
        }

        reportCallSuccess()
        if(!response.status.isSuccess()) {
            val serverMessage = response.errorMessageOrNull()
            throw TandoorRequestsError(
                null,
                response,
                additionalText = serverMessage?.let { "\n\n$it" } ?: ""
            )
        }

        return response
    } catch(e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch(e: Exception) {
        if(e is TandoorRequestsError) {
            throw e
        } else {
            Logger.w("TandoorRequests.kt") { "Transport failure: ${e::class.simpleName}: ${e.message}" }
            reportCallNetworkFailure()
            throw TandoorRequestsError(e, null)
        }
    }
}

suspend fun TandoorClient.getObject(endpoint: String) = reqObject(endpoint, HttpMethod.Get)
suspend fun TandoorClient.getArray(endpoint: String) = reqArray(endpoint, HttpMethod.Get)
suspend fun TandoorClient.getByteArray(endpoint: String) = reqByteArray(endpoint, HttpMethod.Get)

suspend fun TandoorClient.postObject(endpoint: String, data: JsonObject) =
    reqObject(endpoint, HttpMethod.Post, data)

suspend fun TandoorClient.postArray(endpoint: String, data: JsonArray) =
    reqArray(endpoint, HttpMethod.Post, data)

suspend fun TandoorClient.put(endpoint: String, data: JsonObject) =
    req(endpoint, HttpMethod.Put, data)

suspend fun TandoorClient.putObject(endpoint: String, data: JsonObject) =
    reqObject(endpoint, HttpMethod.Put, data)

suspend fun TandoorClient.putArray(endpoint: String, data: JsonArray) =
    reqArray(endpoint, HttpMethod.Put, data)

suspend fun TandoorClient.postMultipart(endpoint: String, _formData: FormBuilder.() -> Unit) =
    reqMultipart(endpoint, HttpMethod.Post, _formData)

suspend fun TandoorClient.putMultipart(endpoint: String, _formData: FormBuilder.() -> Unit) =
    reqMultipart(endpoint, HttpMethod.Put, _formData)

suspend fun TandoorClient.patchObject(endpoint: String, data: JsonObject) =
    reqObject(endpoint, HttpMethod.Patch, data)

suspend fun TandoorClient.patchArray(endpoint: String, data: JsonArray) =
    reqArray(endpoint, HttpMethod.Patch, data)

suspend fun TandoorClient.delete(endpoint: String) =
    req(endpoint, HttpMethod.Delete)