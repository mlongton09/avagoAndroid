package com.avago.core.sync.v2

import com.avago.core.data.v2.V2Request
import com.avago.core.data.v2.V2Response
import com.avago.core.data.v2.V2Transport
import io.ktor.client.HttpClient
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * Ktor implementation of [V2Transport]. The shared authenticated [HttpClient] supplies
 * `Authorization` (bearer + refresh) and the default JSON `Accept`; this adds the base URL and the
 * v2 headers built by `V2Api`. Non-2xx answers are returned, never thrown, so `V2Api` can map
 * 426 and error envelopes; only a missing response throws (→ `V2ApiException.Network`).
 */
@Singleton
class V2HttpTransport @Inject constructor(
    private val httpClient: HttpClient,
    @Named("baseUrl") private val baseUrl: String,
) : V2Transport {

    override suspend fun send(request: V2Request): V2Response {
        val response = httpClient.request("${baseUrl.trimEnd('/')}${request.path}") {
            method = HttpMethod.parse(request.method)
            expectSuccess = false
            request.query.forEach { (key, value) -> parameter(key, value) }
            request.headers
                .filterKeys { !it.equals(HttpHeaders.Accept, true) && !it.equals(HttpHeaders.ContentType, true) }
                .forEach { (key, value) -> header(key, value) }
            request.body?.let {
                contentType(ContentType.Application.Json)
                setBody(it)
            }
        }
        val headers = response.headers.entries().associate { (key, values) -> key to values.joinToString(",") }
        return V2Response(response.status.value, response.bodyAsText(), headers)
    }
}
