package de.kevcodez.gotrue

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient
import org.apache.hc.core5.http.ClassicHttpResponse
import org.apache.hc.core5.http.HttpStatus
import org.apache.hc.core5.http.Method
import org.apache.hc.core5.http.io.HttpClientResponseHandler
import org.apache.hc.core5.http.io.entity.EntityUtils
import org.apache.hc.core5.http.io.entity.StringEntity
import java.net.URI
import kotlin.reflect.KClass

class GoTrueHttpClient(
        private val baseUrl: String,
        private val defaultHeaders: Map<String, Any>,
        private val httpClient: CloseableHttpClient
) {

    private val objectMapper = ObjectMapper()
            .registerModule(KotlinModule())
            .registerModule(JavaTimeModule())
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .setSerializationInclusion(JsonInclude.Include.NON_NULL)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    fun <T : Any> post(path: String, responseType: KClass<T>, headers: Map<String, String> = emptyMap(), data: Any? = null): T {
        return execute(
                method = Method.POST,
                path = path,
                responseType = responseType,
                headers = headers,
                data = data
        )!!
    }

    fun post(path: String, headers: Map<String, String> = emptyMap(), data: Any? = null) {
        execute<Unit>(
                method = Method.POST,
                path = path,
                responseType = null,
                headers = headers,
                data = data
        )
    }

    fun <T : Any> put(path: String, responseType: KClass<T>, headers: Map<String, String> = emptyMap(), data: Any): T {
        return execute(
                method = Method.POST,
                path = path,
                responseType = responseType,
                headers = headers,
                data = data
        )!!
    }

    fun <T : Any> get(path: String, responseType: KClass<T>, headers: Map<String, String> = emptyMap()): T {
        return execute(
                method = Method.GET,
                path = path,
                responseType = responseType,
                headers = headers
        )!!
    }

    private fun <T : Any> execute(method: Method, path: String, responseType: KClass<T>?, data: Any? = null, headers: Map<String, String> = emptyMap()): T? {
        return httpClient.use { httpClient ->
            val httpRequest = HttpUriRequestBase(method.name, URI(baseUrl + path))
            data?.apply {
                val dataAsString = objectMapper.writeValueAsString(data)
                httpRequest.entity = StringEntity(dataAsString)
            }
            val allHeaders = defaultHeaders.filter { !headers.containsKey(it.key) } + headers
            allHeaders.forEach { (name, value) -> httpRequest.addHeader(name, value) }

            @Suppress("UNCHECKED_CAST")
            if (responseType == null) {
                return@use httpClient.execute(httpRequest) as T
            } else {
                return@use httpClient.execute(httpRequest, responseHandler(responseType.java))
            }
        }
    }

    private fun <T> responseHandler(responseType: Class<T>): HttpClientResponseHandler<T> {
        return HttpClientResponseHandler<T> { response ->
            throwIfError(response)

            val entityAsString = response.entity?.let { EntityUtils.toString(it) }

            objectMapper.readValue(entityAsString, responseType)
        }
    }

    private fun throwIfError(response: ClassicHttpResponse) {
        val status = response.code
        val statusSuccessful = status >= HttpStatus.SC_SUCCESS && status < HttpStatus.SC_REDIRECTION

        if (!statusSuccessful) {
            val entityAsString = response.entity?.let { EntityUtils.toString(it) }

            throw GoTrueHttpException(status, entityAsString)
        }
    }
}